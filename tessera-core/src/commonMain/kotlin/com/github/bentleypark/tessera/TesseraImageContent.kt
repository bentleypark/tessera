package com.github.bentleypark.tessera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.util.fastAny
import androidx.compose.ui.util.fastForEach
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Internal composable that renders the tile-based image viewer.
 * Platform-independent — platform entry points provide imageLoader and decoderFactory.
 */
@Composable
internal fun TesseraImageContent(
    imageUrl: String,
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 10.0f,
    contentScale: ContentScale = ContentScale.Fit,
    imageLoader: ImageLoaderStrategy,
    decoderFactory: (ImageSource) -> RegionDecoder,
    contentDescription: String? = null,
    enableDismissGesture: Boolean = false,
    enablePagerIntegration: Boolean = false,
    showScrollIndicators: Boolean = false,
    rotation: ImageRotation = ImageRotation.None,
    viewerState: TesseraViewerState? = null,
    onDismiss: () -> Unit = {}
) {
    val density = LocalDensity.current.density
    val tileSize = remember(density) { (256 * density).roundToInt().coerceIn(256, 512) }
    var tesseraState by remember { mutableStateOf<TesseraState?>(null) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var loadedTiles by remember { mutableStateOf<Map<String, TileLoadInfo>>(emptyMap()) }
    var currentZoomLevel by remember { mutableIntStateOf(-1) }
    val zoomThreshold = 1.01f
    var currentTime by remember { mutableLongStateOf(currentTimeMillis()) }
    var isDismissing by remember { mutableStateOf(false) }
    var lastInteractionTime by remember { mutableLongStateOf(0L) }

    var dismissOffsetY by remember { mutableFloatStateOf(0f) }
    val semanticsModifier = if (contentDescription != null) {
        Modifier.semantics { this.contentDescription = contentDescription }
    } else {
        Modifier
    }

    LaunchedEffect(dismissOffsetY) {
        if (dismissOffsetY > 200f) {
            isDismissing = true
            delay(50)
            onDismiss()
        } else if (dismissOffsetY > 0f) {
            delay(100)
            if (dismissOffsetY <= 200f) {
                dismissOffsetY = 0f
            }
        }
    }

    LaunchedEffect(imageUrl, imageLoader, tileSize) {
        try {
            val previousState = tesseraState
            tesseraState = null
            loadError = null
            loadedTiles = emptyMap()
            currentZoomLevel = -1
            scale = minScale
            offset = Offset.Zero
            dismissOffsetY = 0f
            isDismissing = false
            previousState?.dispose()

            val result = withContext(imageLoadDispatcher) {
                imageLoader.loadImageSource(imageUrl)
            }
            val source = result.getOrNull()
            if (source != null) {
                val state = TesseraState(source, decoderFactory, tileSize = tileSize)
                val initResult = withContext(imageLoadDispatcher) {
                    state.initializeDecoder()
                }
                state.applyInitResult(initResult)
                when (initResult) {
                    is TesseraState.InitResult.Success -> tesseraState = state
                    is TesseraState.InitResult.Error -> loadError = initResult.message
                }
            } else {
                loadError = result.exceptionOrNull()?.message ?: "Failed to load image"
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("TesseraImage", "load failed: imageUrl=$imageUrl", e)
            loadError = e.message ?: "Failed to load image"
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            currentTime = currentTimeMillis()
            delay(16)
        }
    }

    LaunchedEffect(tesseraState) {
        val state = tesseraState ?: return@LaunchedEffect

        snapshotFlow { state.viewport }
            .collect {
                if (isDismissing) return@collect
                if (state.isLoading || state.error != null) return@collect

                delay(20)

                val visibleTiles = state.getVisibleTiles()
                if (visibleTiles.isEmpty()) return@collect

                val newZoomLevel = visibleTiles.firstOrNull()?.zoomLevel ?: -1
                if (newZoomLevel != currentZoomLevel) {
                    currentZoomLevel = newZoomLevel
                }

                // Skip tile loading at zoom level 0 when the viewport covers the
                // full image — the preview bitmap (1024px) is sufficient.
                // FitWidth/FitHeight modes where the viewport only shows a portion
                // of the image still load tiles for sharp scrollable content.
                if (newZoomLevel == 0) {
                    val vp = state.viewport
                    val info = state.imageInfo
                    if (info == null ||
                        (vp.viewWidth >= info.width.toFloat() - 1f &&
                         vp.viewHeight >= info.height.toFloat() - 1f)
                    ) {
                        return@collect
                    }
                }

                val tilesToLoad = visibleTiles.filter { it.toKey() !in loadedTiles.keys }

                if (tilesToLoad.isEmpty()) {
                    return@collect
                }

                val currentViewport = state.viewport
                val viewportCenterX = currentViewport.offsetX + currentViewport.viewWidth / 2f
                val viewportCenterY = currentViewport.offsetY + currentViewport.viewHeight / 2f

                val sortedTiles = tilesToLoad.sortedBy { coordinate ->
                    val rect = state.getTileRect(coordinate)
                    val tileCenterX = (rect.left + rect.right) / 2f
                    val tileCenterY = (rect.top + rect.bottom) / 2f

                    val dx = tileCenterX - viewportCenterX
                    val dy = tileCenterY - viewportCenterY
                    dx * dx + dy * dy
                }

                val batchStart = currentTimeMillis()
                var loadedCount = 0
                var failCount = 0

                // Decode tiles in parallel chunks matching decoder pool size.
                // Each chunk is decoded concurrently, then cached immediately
                // so tiles appear progressively instead of all at once.
                val chunkSize = 2
                sortedTiles.chunked(chunkSize).forEach { chunk ->
                    ensureActive()

                    val results = supervisorScope {
                        chunk.map { coordinate ->
                            async(ioDispatcher) {
                                try {
                                    coordinate to state.decodeTile(coordinate)
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (e: Exception) {
                                    logError("TesseraImage", "tile decode failed: ${coordinate.toKey()}", e)
                                    coordinate to null
                                }
                            }
                        }.awaitAll()
                    }

                    for ((coordinate, bitmap) in results) {
                        if (bitmap != null) {
                            state.cacheTile(coordinate, bitmap)
                            val loadTime = currentTimeMillis()
                            loadedTiles = loadedTiles + (coordinate.toKey() to TileLoadInfo(loadTime, coordinate.zoomLevel))
                            loadedCount++
                        } else {
                            failCount++
                        }
                    }
                    yield()
                }

                if (failCount > 0 && loadedCount == 0) {
                    logError("TesseraImage", "All $failCount tiles failed to decode")
                }

                val batchTime = currentTimeMillis() - batchStart
                if (loadedCount > 0) {
                    logWarning("TesseraPerf", "tiles: loaded=$loadedCount " +
                        "total=${batchTime}ms avg=${batchTime / loadedCount}ms " +
                        "zoom=$newZoomLevel")
                }
            }
    }

    DisposableEffect(Unit) {
        onDispose {
            tesseraState?.dispose()
        }
    }

    // Sync internal state to public TesseraViewerState after each successful composition.
    // State reads must happen during composition (not inside SideEffect lambda)
    // so that Compose subscribes to changes and triggers recomposition.
    if (viewerState != null) {
        val syncScale = scale
        val syncZoomLevel = currentZoomLevel
        val syncTileCount = tesseraState?.cachedTileCount ?: 0
        val syncTesseraState = tesseraState
        val syncLoadError = loadError
        SideEffect {
            syncViewerState(
                vs = viewerState,
                scale = syncScale,
                zoomLevel = syncZoomLevel,
                tileCount = syncTileCount,
                tesseraState = syncTesseraState,
                loadError = syncLoadError
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .offset { IntOffset(0, dismissOffsetY.roundToInt()) }
            .background(
                Color.Black.copy(
                    alpha = if (enableDismissGesture) {
                        (1f - (dismissOffsetY / 500f)).coerceIn(0f, 1f)
                    } else {
                        1f
                    }
                )
            )
            .then(semanticsModifier),
        contentAlignment = Alignment.Center
    ) {
        val state = tesseraState

        when {
            loadError != null -> {
                Text(
                    text = "Error: $loadError",
                    color = Color.Red,
                    fontSize = 16.sp
                )
            }

            state == null || state.isLoading -> {
                CircularProgressIndicator(color = Color.White)
            }

            state.error != null -> {
                Text(
                    text = "Error: ${state.error}",
                    color = Color.Red,
                    fontSize = 16.sp
                )
            }

            else -> {
                var lastTapTime by remember { mutableLongStateOf(0L) }
                var lastTapOffset by remember { mutableStateOf(Offset.Zero) }

                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = rotation.degrees.toFloat()
                            clip = true
                        }
                        .pointerInput(contentScale, rotation) {
                            awaitPointerEventScope {
                                while (true) {
                                    val event = awaitPointerEvent()
                                    if (event.type != PointerEventType.Scroll) continue
                                    val change = event.changes.firstOrNull() ?: continue
                                    val scrollX = change.scrollDelta.x
                                    val scrollY = change.scrollDelta.y
                                    if (scrollX == 0f && scrollY == 0f) continue

                                    val isZoomModifier = isZoomModifierPressed(event)
                                    change.consume()
                                    if (showScrollIndicators) lastInteractionTime = currentTimeMillis()

                                    tesseraState?.imageInfo?.let { imageInfo ->
                                        val imageWidth = imageInfo.width.toFloat()
                                        val imageHeight = imageInfo.height.toFloat()
                                        val viewWidth = size.width
                                        val viewHeight = size.height
                                        val fitScale = computeFitScale(contentScale, imageWidth, imageHeight, viewWidth.toFloat(), viewHeight.toFloat())

                                        if (isZoomModifier) {
                                            // Ctrl/Cmd + Scroll → Zoom toward cursor
                                            if (scrollY == 0f) return@let
                                            val zoomFactor = if (scrollY < 0) 1.1f else 1f / 1.1f
                                            val oldTotalScale = fitScale * scale
                                            val newScale = (scale * zoomFactor).coerceIn(minScale, maxScale)
                                            val newTotalScale = fitScale * newScale

                                            val cursorPos = change.position
                                            val scaledWidth = imageWidth * oldTotalScale
                                            val scaledHeight = imageHeight * oldTotalScale
                                            val centerX = (viewWidth - scaledWidth) / 2f
                                            val centerY = (viewHeight - scaledHeight) / 2f
                                            val baseOff = Offset(centerX + offset.x, centerY + offset.y)

                                            val imagePointX = (cursorPos.x - baseOff.x) / oldTotalScale
                                            val imagePointY = (cursorPos.y - baseOff.y) / oldTotalScale

                                            val newScaledW = imageWidth * newTotalScale
                                            val newScaledH = imageHeight * newTotalScale
                                            val newCenterX = (viewWidth - newScaledW) / 2f
                                            val newCenterY = (viewHeight - newScaledH) / 2f

                                            val newBaseX = cursorPos.x - (imagePointX * newTotalScale)
                                            val newBaseY = cursorPos.y - (imagePointY * newTotalScale)
                                            val newOffsetX = newBaseX - newCenterX
                                            val newOffsetY = newBaseY - newCenterY

                                            val isZoomedIn = newScale > zoomThreshold
                                            val resolved = resolveContentScale(contentScale, imageWidth, imageHeight, viewWidth.toFloat(), viewHeight.toFloat())
                                            val overflows = resolved == ContentScale.FitWidth || resolved == ContentScale.FitHeight
                                            if (!isZoomedIn && !overflows) {
                                                offset = Offset.Zero
                                                scale = minScale
                                            } else {
                                                val finalScale = if (!isZoomedIn) minScale else newScale
                                                val finalTotalScale = fitScale * finalScale
                                                val finalScaledW = imageWidth * finalTotalScale
                                                val finalScaledH = imageHeight * finalTotalScale
                                                val maxOffsetX = if (finalScaledW > viewWidth) (finalScaledW - viewWidth) / 2f else 0f
                                                val maxOffsetY = if (finalScaledH > viewHeight) (finalScaledH - viewHeight) / 2f else 0f
                                                offset = Offset(
                                                    x = newOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
                                                    y = newOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                                                )
                                                scale = finalScale
                                            }
                                        } else {
                                            // Plain scroll → Pan
                                            val totalScale = fitScale * scale
                                            val scaledWidth = imageWidth * totalScale
                                            val scaledHeight = imageHeight * totalScale

                                            val canPanX = scaledWidth > viewWidth
                                            val canPanY = scaledHeight > viewHeight
                                            if (!canPanX && !canPanY) return@let

                                            val panSpeed = 20f
                                            val panX = -scrollX * panSpeed
                                            val panY = -scrollY * panSpeed

                                            val maxOffsetX = if (canPanX) (scaledWidth - viewWidth) / 2f else 0f
                                            val maxOffsetY = if (canPanY) (scaledHeight - viewHeight) / 2f else 0f

                                            offset = Offset(
                                                x = (offset.x + panX).coerceIn(-maxOffsetX, maxOffsetX),
                                                y = (offset.y + panY).coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        .pointerInput(enablePagerIntegration, enableDismissGesture, contentScale, rotation) {
                            awaitEachGesture {
                                val down = awaitFirstDown(requireUnconsumed = false)
                                var shouldConsume = !enablePagerIntegration
                                var atEdge = false
                                var totalPan = Offset.Zero
                                val gestureStartTime = currentTimeMillis()
                                val downPosition = down.position

                                do {
                                    val event = awaitPointerEvent()
                                    val changes = event.changes

                                    val activeChanges = changes.filter { it.pressed }
                                    val zoom = if (activeChanges.size >= 2) {
                                        val currentDist = (activeChanges[0].position - activeChanges[1].position).getDistance()
                                        val prevDist = (activeChanges[0].previousPosition - activeChanges[1].previousPosition).getDistance()
                                        if (prevDist > 0f) currentDist / prevDist else 1f
                                    } else 1f
                                    val pan = if (activeChanges.isNotEmpty()) {
                                        val avgCurrent = activeChanges.fold(Offset.Zero) { acc, c -> acc + c.position } / activeChanges.size.toFloat()
                                        val avgPrev = activeChanges.fold(Offset.Zero) { acc, c -> acc + c.previousPosition } / activeChanges.size.toFloat()
                                        avgCurrent - avgPrev
                                    } else Offset.Zero
                                    val centroid = if (activeChanges.isNotEmpty()) {
                                        activeChanges.fold(Offset.Zero) { acc, c -> acc + c.position } / activeChanges.size.toFloat()
                                    } else Offset.Zero
                                    totalPan += pan

                                    // Once at edge, stay in pass-through for rest of gesture
                                    if (atEdge) continue

                                    // Determine if we should consume
                                    if (enablePagerIntegration && !shouldConsume) {
                                        val isPinch = zoom != 1f
                                        val isVerticalPan = abs(totalPan.y) > abs(totalPan.x)
                                        val isZoomedIn = scale > zoomThreshold
                                        shouldConsume = isPinch || isVerticalPan || isZoomedIn
                                    }

                                    if (!shouldConsume) continue

                                    // Check edge BEFORE consuming
                                    tesseraState?.imageInfo?.let { imageInfo ->
                                        if (enablePagerIntegration && scale > zoomThreshold &&
                                            abs(pan.x) > abs(pan.y) && zoom == 1f
                                        ) {
                                            val imageWidth = imageInfo.width.toFloat()
                                            val imageHeight = imageInfo.height.toFloat()
                                            val viewWidth = size.width
                                            val viewHeight = size.height
                                            val fitScale = computeFitScale(contentScale, imageWidth, imageHeight, viewWidth.toFloat(), viewHeight.toFloat())
                                            val totalScale = fitScale * scale
                                            val scaledWidth = imageWidth * totalScale
                                            val maxOffsetX = if (scaledWidth > viewWidth) (scaledWidth - viewWidth) / 2f else 0f

                                            val leftEdge = offset.x >= maxOffsetX - 1f && pan.x > 0
                                            val rightEdge = offset.x <= -maxOffsetX + 1f && pan.x < 0
                                            if (leftEdge || rightEdge) {
                                                atEdge = true
                                                continue
                                            }
                                        }
                                    }

                                    // Consume pointer changes
                                    changes.fastForEach { if (it.positionChanged()) it.consume() }
                                    if (showScrollIndicators) lastInteractionTime = currentTimeMillis()

                                    // Dismiss gesture
                                    if (enableDismissGesture &&
                                        scale <= zoomThreshold &&
                                        zoom == 1f &&
                                        abs(pan.y) > abs(pan.x) * 1.5 &&
                                        pan.y > 0
                                    ) {
                                        dismissOffsetY += pan.y
                                        continue
                                    }

                                    // Transform gesture
                                    tesseraState?.imageInfo?.let { imageInfo ->
                                        val imageWidth = imageInfo.width.toFloat()
                                        val imageHeight = imageInfo.height.toFloat()
                                        val viewWidth = size.width
                                        val viewHeight = size.height

                                        val fitScale = computeFitScale(contentScale, imageWidth, imageHeight, viewWidth.toFloat(), viewHeight.toFloat())

                                        val oldTotalScale = fitScale * scale
                                        val oldScaledWidth = imageWidth * oldTotalScale
                                        val oldScaledHeight = imageHeight * oldTotalScale
                                        val oldCenterX = (viewWidth - oldScaledWidth) / 2f
                                        val oldCenterY = (viewHeight - oldScaledHeight) / 2f
                                        val oldBaseOffset =
                                            Offset(oldCenterX + offset.x, oldCenterY + offset.y)

                                        val imagePointX = (centroid.x - oldBaseOffset.x) / oldTotalScale
                                        val imagePointY = (centroid.y - oldBaseOffset.y) / oldTotalScale

                                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                                        val newTotalScale = fitScale * newScale
                                        val newScaledWidth = imageWidth * newTotalScale
                                        val newScaledHeight = imageHeight * newTotalScale
                                        val newCenterX = (viewWidth - newScaledWidth) / 2f
                                        val newCenterY = (viewHeight - newScaledHeight) / 2f

                                        val newBaseOffsetX = centroid.x - (imagePointX * newTotalScale)
                                        val newBaseOffsetY = centroid.y - (imagePointY * newTotalScale)
                                        val newOffsetX = newBaseOffsetX - newCenterX + pan.x
                                        val newOffsetY = newBaseOffsetY - newCenterY + pan.y

                                        val isZoomedIn = newScale > zoomThreshold
                                        val resolved = resolveContentScale(contentScale, imageWidth, imageHeight, viewWidth.toFloat(), viewHeight.toFloat())
                                        val overflows = resolved == ContentScale.FitWidth || resolved == ContentScale.FitHeight
                                        if (!isZoomedIn && !overflows) {
                                            offset = Offset.Zero
                                            scale = minScale
                                        } else {
                                            val maxOffsetX = if (newScaledWidth > viewWidth) {
                                                (newScaledWidth - viewWidth) / 2f
                                            } else 0f

                                            val maxOffsetY = if (newScaledHeight > viewHeight) {
                                                (newScaledHeight - viewHeight) / 2f
                                            } else 0f

                                            offset = Offset(
                                                x = newOffsetX.coerceIn(-maxOffsetX, maxOffsetX),
                                                y = newOffsetY.coerceIn(-maxOffsetY, maxOffsetY)
                                            )
                                            scale = if (!isZoomedIn) minScale else newScale
                                        }
                                    } ?: run {
                                        val newScale = (scale * zoom).coerceIn(minScale, maxScale)
                                        if (newScale > zoomThreshold) {
                                            offset += pan
                                            scale = newScale
                                        } else {
                                            offset = Offset.Zero
                                            scale = minScale
                                        }
                                    }
                                } while (changes.fastAny { it.pressed })

                                // Double-tap detection: short gesture with no significant pan
                                val gestureDuration = currentTimeMillis() - gestureStartTime
                                val totalDistance = totalPan.getDistance()
                                if (gestureDuration < 300 && totalDistance < 20f) {
                                    val now = currentTimeMillis()
                                    val tapDelta = (downPosition - lastTapOffset).getDistance()
                                    if (now - lastTapTime < 400 && tapDelta < 100f) {
                                        // Double tap detected
                                        lastTapTime = 0L
                                        val tapOffset = downPosition
                                        if (scale > minScale) {
                                            scale = minScale
                                            tesseraState?.imageInfo?.let { imageInfo ->
                                                val imageWidth = imageInfo.width.toFloat()
                                                val imageHeight = imageInfo.height.toFloat()
                                                val resolved = resolveContentScale(contentScale, imageWidth, imageHeight, size.width.toFloat(), size.height.toFloat())
                                                val overflows = resolved == ContentScale.FitWidth || resolved == ContentScale.FitHeight
                                                if (overflows) {
                                                    val fitScale = computeFitScale(contentScale, imageWidth, imageHeight, size.width.toFloat(), size.height.toFloat())
                                                    val newTotalScale = fitScale * minScale
                                                    val scaledW = imageWidth * newTotalScale
                                                    val scaledH = imageHeight * newTotalScale
                                                    val maxOX = if (scaledW > size.width) (scaledW - size.width) / 2f else 0f
                                                    val maxOY = if (scaledH > size.height) (scaledH - size.height) / 2f else 0f
                                                    offset = Offset(
                                                        x = offset.x.coerceIn(-maxOX, maxOX),
                                                        y = offset.y.coerceIn(-maxOY, maxOY)
                                                    )
                                                } else {
                                                    offset = Offset.Zero
                                                }
                                            } ?: run { offset = Offset.Zero }
                                        } else {
                                            tesseraState?.imageInfo?.let { imageInfo ->
                                                val imageWidth = imageInfo.width.toFloat()
                                                val imageHeight = imageInfo.height.toFloat()
                                                val viewWidth = size.width
                                                val viewHeight = size.height
                                                val fitScale = computeFitScale(contentScale, imageWidth, imageHeight, viewWidth.toFloat(), viewHeight.toFloat())
                                                val currentTotalScale = fitScale * scale
                                                val targetScale = 3f
                                                val targetTotalScale = fitScale * targetScale
                                                val scaledWidth = imageWidth * currentTotalScale
                                                val scaledHeight = imageHeight * currentTotalScale
                                                val centerX = (viewWidth - scaledWidth) / 2f
                                                val centerY = (viewHeight - scaledHeight) / 2f
                                                val baseOff = Offset(centerX + offset.x, centerY + offset.y)
                                                val tapX = (tapOffset.x - baseOff.x) / currentTotalScale
                                                val tapY = (tapOffset.y - baseOff.y) / currentTotalScale
                                                val tgtW = imageWidth * targetTotalScale
                                                val tgtH = imageHeight * targetTotalScale
                                                val tgtCX = (viewWidth - tgtW) / 2f
                                                val tgtCY = (viewHeight - tgtH) / 2f
                                                val newOX = viewWidth / 2f - (tgtCX + tapX * targetTotalScale)
                                                val newOY = viewHeight / 2f - (tgtCY + tapY * targetTotalScale)
                                                val maxOX = if (tgtW > viewWidth) (tgtW - viewWidth) / 2f else 0f
                                                val maxOY = if (tgtH > viewHeight) (tgtH - viewHeight) / 2f else 0f
                                                offset = Offset(newOX.coerceIn(-maxOX, maxOX), newOY.coerceIn(-maxOY, maxOY))
                                                scale = targetScale
                                                if (showScrollIndicators) lastInteractionTime = currentTimeMillis()
                                            } ?: run { scale = 3f }
                                        }
                                    } else {
                                        lastTapTime = now
                                        lastTapOffset = downPosition
                                    }
                                }
                            }
                        }
                ) {
                    drawRect(
                        color = Color.Black,
                        size = size
                    )

                    val imageInfo = state.imageInfo
                    if (imageInfo != null) {
                        val imageWidth = imageInfo.width.toFloat()
                        val imageHeight = imageInfo.height.toFloat()
                        val fitScale = computeFitScale(contentScale, imageWidth, imageHeight, size.width, size.height)
                        val totalScale = fitScale * scale

                        val scaledWidth = imageWidth * totalScale
                        val scaledHeight = imageHeight * totalScale
                        val centerX = (size.width - scaledWidth) / 2f
                        val centerY = (size.height - scaledHeight) / 2f
                        val baseOffset = Offset(centerX + offset.x, centerY + offset.y)

                        val viewportLeft = if (baseOffset.x < 0) -baseOffset.x / totalScale else 0f
                        val viewportTop = if (baseOffset.y < 0) -baseOffset.y / totalScale else 0f
                        val viewportRight = min(imageWidth, viewportLeft + size.width / totalScale)
                        val viewportBottom =
                            min(imageHeight, viewportTop + size.height / totalScale)
                        val viewportWidth = viewportRight - viewportLeft
                        val viewportHeight = viewportBottom - viewportTop

                        state.updateViewport(
                            Viewport(
                                offsetX = viewportLeft,
                                offsetY = viewportTop,
                                scale = scale,
                                totalScale = totalScale,
                                viewWidth = viewportWidth,
                                viewHeight = viewportHeight
                            )
                        )

                        state.previewBitmap?.let { preview ->
                            drawImage(
                                image = preview,
                                dstOffset = IntOffset(
                                    x = baseOffset.x.toInt(),
                                    y = baseOffset.y.toInt()
                                ),
                                dstSize = IntSize(
                                    width = ceil(scaledWidth).toInt(),
                                    height = ceil(scaledHeight).toInt()
                                )
                            )
                        }

                        loadedTiles
                            .filter { it.value.zoomLevel < currentZoomLevel }
                            .forEach { (tileKey, _) ->
                                state.getCachedTileByKey(tileKey)?.let { (bitmap, coordinate) ->
                                    val tileRect = state.getTileRect(coordinate)
                                    drawTileWithRect(
                                        bitmap = bitmap,
                                        tileRect = tileRect,
                                        totalScale = totalScale,
                                        baseOffset = baseOffset,
                                        alpha = 1f
                                    )
                                }
                            }

                        loadedTiles
                            .filter { it.value.zoomLevel == currentZoomLevel }
                            .forEach { (tileKey, info) ->
                                state.getCachedTileByKey(tileKey)?.let { (bitmap, coordinate) ->
                                    val tileRect = state.getTileRect(coordinate)
                                    val fadeInDuration = 100L
                                    val elapsedTime = currentTime - info.loadTime
                                    val alpha = if (elapsedTime < fadeInDuration) {
                                        (elapsedTime.toFloat() / fadeInDuration).coerceIn(0f, 1f)
                                    } else {
                                        1f
                                    }

                                    drawTileWithRect(
                                        bitmap = bitmap,
                                        tileRect = tileRect,
                                        totalScale = totalScale,
                                        baseOffset = baseOffset,
                                        alpha = alpha
                                    )
                                }
                            }

                        // Scroll indicators
                        if (showScrollIndicators && scale > zoomThreshold) {
                            val fadeOutDelay = 1500L
                            val fadeOutDuration = 500L
                            val elapsed = currentTime - lastInteractionTime
                            val indicatorAlpha = when {
                                lastInteractionTime == 0L -> 0f
                                elapsed < fadeOutDelay -> 0.6f
                                elapsed < fadeOutDelay + fadeOutDuration -> {
                                    0.6f * (1f - (elapsed - fadeOutDelay).toFloat() / fadeOutDuration)
                                }
                                else -> 0f
                            }

                            if (indicatorAlpha > 0f) {
                                drawScrollIndicators(
                                    scaledWidth = scaledWidth,
                                    scaledHeight = scaledHeight,
                                    baseOffset = baseOffset,
                                    canvasWidth = size.width,
                                    canvasHeight = size.height,
                                    alpha = indicatorAlpha
                                )

                                state.previewBitmap?.let { preview ->
                                    drawMinimap(
                                        preview = preview,
                                        imageWidth = imageWidth,
                                        imageHeight = imageHeight,
                                        scaledWidth = scaledWidth,
                                        scaledHeight = scaledHeight,
                                        baseOffset = baseOffset,
                                        canvasWidth = size.width,
                                        canvasHeight = size.height,
                                        alpha = indicatorAlpha
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Resolve Auto content scale based on image and viewport aspect ratios.
 */
internal fun resolveContentScale(
    contentScale: ContentScale,
    imageWidth: Float,
    imageHeight: Float,
    viewWidth: Float,
    viewHeight: Float
): ContentScale {
    if (contentScale != ContentScale.Auto) return contentScale
    if (imageHeight <= 0f || viewHeight <= 0f) return ContentScale.Fit

    val imageAspect = imageWidth / imageHeight
    val viewAspect = viewWidth / viewHeight

    return when {
        imageAspect < viewAspect / 1.5f -> ContentScale.FitWidth  // tall image
        imageAspect > viewAspect * 1.5f -> ContentScale.FitHeight // wide image
        else -> ContentScale.Fit
    }
}

internal fun computeFitScale(
    contentScale: ContentScale,
    imageWidth: Float,
    imageHeight: Float,
    viewWidth: Float,
    viewHeight: Float
): Float {
    if (imageWidth <= 0f || imageHeight <= 0f || viewWidth <= 0f || viewHeight <= 0f) {
        return 1f
    }
    val scaleX = viewWidth / imageWidth
    val scaleY = viewHeight / imageHeight
    val resolved = resolveContentScale(contentScale, imageWidth, imageHeight, viewWidth, viewHeight)
    return when (resolved) {
        ContentScale.FitWidth -> scaleX
        ContentScale.FitHeight -> scaleY
        ContentScale.Fit, ContentScale.Auto -> minOf(scaleX, scaleY)
    }
}

private fun DrawScope.drawTileWithRect(
    bitmap: ImageBitmap,
    tileRect: TileRect,
    totalScale: Float,
    baseOffset: Offset,
    alpha: Float = 1f
) {
    val tileLeft = tileRect.left.toFloat()
    val tileTop = tileRect.top.toFloat()
    val tileRight = tileRect.right.toFloat()
    val tileBottom = tileRect.bottom.toFloat()

    val screenLeft = baseOffset.x + (tileLeft * totalScale)
    val screenTop = baseOffset.y + (tileTop * totalScale)
    val screenRight = baseOffset.x + (tileRight * totalScale)
    val screenBottom = baseOffset.y + (tileBottom * totalScale)

    val screenWidth = screenRight - screenLeft
    val screenHeight = screenBottom - screenTop

    drawImage(
        image = bitmap,
        dstOffset = IntOffset(
            x = screenLeft.toInt(),
            y = screenTop.toInt()
        ),
        dstSize = IntSize(
            width = ceil(screenWidth).toInt(),
            height = ceil(screenHeight).toInt()
        ),
        alpha = alpha
    )
}

private fun DrawScope.drawScrollIndicators(
    scaledWidth: Float,
    scaledHeight: Float,
    baseOffset: Offset,
    canvasWidth: Float,
    canvasHeight: Float,
    alpha: Float
) {
    val barThickness = 4f
    val barMargin = 6f
    val minBarLength = 24f
    val cornerRadius = 2f
    val barColor = Color.White.copy(alpha = alpha)

    // Vertical scroll bar (right edge)
    if (scaledHeight > canvasHeight + 1f) {
        val viewportTop = (-baseOffset.y).coerceAtLeast(0f)
        val visibleRatio = canvasHeight / scaledHeight
        val positionRatio = viewportTop / (scaledHeight - canvasHeight)

        val trackHeight = canvasHeight - barMargin * 2
        val thumbHeight = (trackHeight * visibleRatio).coerceAtLeast(minBarLength)
        val thumbY = barMargin + (trackHeight - thumbHeight) * positionRatio.coerceIn(0f, 1f)
        val thumbX = canvasWidth - barThickness - barMargin

        drawRoundRect(
            color = barColor,
            topLeft = Offset(thumbX, thumbY),
            size = Size(barThickness, thumbHeight),
            cornerRadius = CornerRadius(cornerRadius)
        )
    }

    // Horizontal scroll bar (bottom edge)
    if (scaledWidth > canvasWidth + 1f) {
        val viewportLeft = (-baseOffset.x).coerceAtLeast(0f)
        val visibleRatio = canvasWidth / scaledWidth
        val positionRatio = viewportLeft / (scaledWidth - canvasWidth)

        val trackWidth = canvasWidth - barMargin * 2
        val thumbWidth = (trackWidth * visibleRatio).coerceAtLeast(minBarLength)
        val thumbX = barMargin + (trackWidth - thumbWidth) * positionRatio.coerceIn(0f, 1f)
        val thumbY = canvasHeight - barThickness - barMargin

        drawRoundRect(
            color = barColor,
            topLeft = Offset(thumbX, thumbY),
            size = Size(thumbWidth, barThickness),
            cornerRadius = CornerRadius(cornerRadius)
        )
    }
}

private fun DrawScope.drawMinimap(
    preview: ImageBitmap,
    imageWidth: Float,
    imageHeight: Float,
    scaledWidth: Float,
    scaledHeight: Float,
    baseOffset: Offset,
    canvasWidth: Float,
    canvasHeight: Float,
    alpha: Float
) {
    val minimapMaxSize = 140f
    val minimapMargin = 12f
    val borderWidth = 1f

    // Calculate minimap dimensions preserving aspect ratio
    val imageAspect = imageWidth / imageHeight
    val minimapWidth: Float
    val minimapHeight: Float
    if (imageAspect > 1f) {
        minimapWidth = minimapMaxSize
        minimapHeight = minimapMaxSize / imageAspect
    } else {
        minimapHeight = minimapMaxSize
        minimapWidth = minimapMaxSize * imageAspect
    }

    // Position: bottom-left corner
    val minimapX = minimapMargin
    val minimapY = canvasHeight - minimapHeight - minimapMargin

    // Draw background
    drawRoundRect(
        color = Color.Black.copy(alpha = alpha * 0.7f),
        topLeft = Offset(minimapX - 2f, minimapY - 2f),
        size = Size(minimapWidth + 4f, minimapHeight + 4f),
        cornerRadius = CornerRadius(4f)
    )

    // Draw preview thumbnail
    drawImage(
        image = preview,
        dstOffset = IntOffset(minimapX.toInt(), minimapY.toInt()),
        dstSize = IntSize(minimapWidth.toInt(), minimapHeight.toInt()),
        alpha = alpha
    )

    // Calculate viewport rectangle on minimap
    val viewportLeft = ((-baseOffset.x).coerceAtLeast(0f) / scaledWidth * minimapWidth)
    val viewportTop = ((-baseOffset.y).coerceAtLeast(0f) / scaledHeight * minimapHeight)
    val viewportW = (canvasWidth / scaledWidth * minimapWidth).coerceAtMost(minimapWidth - viewportLeft)
    val viewportH = (canvasHeight / scaledHeight * minimapHeight).coerceAtMost(minimapHeight - viewportTop)

    // Draw viewport rectangle
    drawRect(
        color = Color.White.copy(alpha = alpha * 0.8f),
        topLeft = Offset(minimapX + viewportLeft, minimapY + viewportTop),
        size = Size(viewportW, viewportH),
        style = Stroke(width = borderWidth)
    )
}

internal fun syncViewerState(
    vs: TesseraViewerState,
    scale: Float,
    zoomLevel: Int,
    tileCount: Int,
    tesseraState: TesseraState?,
    loadError: String?
) {
    if (tesseraState != null) {
        vs.sync(
            scale = scale,
            zoomLevel = zoomLevel,
            cachedTileCount = tileCount,
            isLoading = tesseraState.isLoading,
            imageInfo = tesseraState.imageInfo,
            error = tesseraState.error ?: loadError
        )
    } else {
        vs.sync(
            scale = scale,
            zoomLevel = zoomLevel,
            cachedTileCount = tileCount,
            isLoading = loadError == null,
            imageInfo = null,
            error = loadError
        )
    }
}
