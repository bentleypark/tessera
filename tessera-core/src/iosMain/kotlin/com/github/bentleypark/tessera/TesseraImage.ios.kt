package com.github.bentleypark.tessera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Tessera - Compose-native tile-based image viewer (iOS)
 *
 * @param imageUrl URL or path to the image (http/https, file://)
 * @param modifier Modifier to be applied to the image viewer
 * @param minScale Minimum scale factor (default: 1.0f)
 * @param maxScale Maximum scale factor (default: 10.0f)
 * @param contentScale Content scaling strategy (default: Fit)
 * @param imageLoader Image loading strategy (default: IosImageLoader)
 * @param contentDescription Accessibility description
 * @param enableDismissGesture Enable vertical drag-to-dismiss gesture
 * @param state Observable viewer state created via [rememberTesseraState]
 * @param onDismiss Callback invoked when dismiss gesture is completed
 */
@Composable
fun TesseraImage(
    imageUrl: String,
    modifier: Modifier = Modifier,
    minScale: Float = 1.0f,
    maxScale: Float = 10.0f,
    contentScale: ContentScale = ContentScale.Fit,
    imageLoader: ImageLoaderStrategy? = null,
    contentDescription: String? = null,
    enableDismissGesture: Boolean = false,
    enablePagerIntegration: Boolean = false,
    showScrollIndicators: Boolean = false,
    rotation: ImageRotation = ImageRotation.None,
    tileAnimationDurationMs: Int = 200,
    state: TesseraViewerState? = null,
    lifecycleAwareCache: Boolean = true,
    onDismiss: () -> Unit = {}
) {
    val resolvedLoader = remember(imageLoader) { imageLoader ?: IosImageLoader() }
    val decoderFactory: (ImageSource) -> RegionDecoder = remember {
        { source -> CgImageSourceRegionDecoder(source) }
    }
    TesseraImageContent(
        imageUrl = imageUrl,
        modifier = modifier,
        minScale = minScale,
        maxScale = maxScale,
        contentScale = contentScale,
        imageLoader = resolvedLoader,
        decoderFactory = decoderFactory,
        contentDescription = contentDescription,
        enableDismissGesture = enableDismissGesture,
        enablePagerIntegration = enablePagerIntegration,
        showScrollIndicators = showScrollIndicators,
        rotation = rotation,
        tileAnimationDurationMs = tileAnimationDurationMs,
        viewerState = state,
        lifecycleAwareCache = lifecycleAwareCache,
        onDismiss = onDismiss
    )
}
