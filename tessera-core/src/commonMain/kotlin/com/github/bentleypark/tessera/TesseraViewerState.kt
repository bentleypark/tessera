package com.github.bentleypark.tessera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/**
 * Observable state for the Tessera image viewer.
 *
 * Use [rememberTesseraState] to create an instance and pass it to [TesseraImage]
 * to observe zoom level, loading state, image metadata, and other viewer properties
 * from outside the composable.
 *
 * ```kotlin
 * val state = rememberTesseraState()
 *
 * TesseraImage(
 *     imageUrl = "https://example.com/large-image.jpg",
 *     state = state
 * )
 *
 * // Observe state externally
 * Text("Scale: ${state.scale}")
 * Text("Loading: ${state.isLoading}")
 * state.imageInfo?.let { Text("Size: ${it.width} x ${it.height}") }
 * ```
 */
@Stable
class TesseraViewerState {
    /** Current user zoom scale (1.0 = no zoom). Does not include fitScale. */
    var scale: Float by mutableFloatStateOf(1f)
        internal set

    /** True while the image is being downloaded and decoded for the first time. */
    var isLoading: Boolean by mutableStateOf(true)
        internal set

    /** Image metadata (width, height, mimeType). Null until loading completes. */
    var imageInfo: ImageInfo? by mutableStateOf(null)
        internal set

    /** Error message if image loading or decoding failed. Null on success. */
    var error: String? by mutableStateOf(null)
        internal set

    /** Current tile zoom level (0–3). -1 before any tiles are loaded. */
    var zoomLevel: Int by mutableIntStateOf(-1)
        internal set

    /** Number of tiles currently cached in memory. */
    var cachedTileCount: Int by mutableIntStateOf(0)
        internal set

    /** True when the image has loaded successfully and tiles are being rendered. */
    val isReady: Boolean
        get() = !isLoading && error == null && imageInfo != null

    internal fun sync(
        scale: Float,
        zoomLevel: Int,
        cachedTileCount: Int,
        isLoading: Boolean,
        imageInfo: ImageInfo?,
        error: String?
    ) {
        this.scale = scale
        this.zoomLevel = zoomLevel
        this.cachedTileCount = cachedTileCount
        this.isLoading = isLoading
        this.imageInfo = imageInfo
        this.error = error
    }
}

/**
 * Creates and remembers a [TesseraViewerState] instance.
 *
 * Pass the returned state to [TesseraImage] via the `state` parameter,
 * then observe its properties to react to viewer changes.
 */
@Composable
fun rememberTesseraState(): TesseraViewerState {
    return remember { TesseraViewerState() }
}
