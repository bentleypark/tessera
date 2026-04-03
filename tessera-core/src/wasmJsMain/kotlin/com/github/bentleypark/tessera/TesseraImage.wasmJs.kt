package com.github.bentleypark.tessera

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Tessera - Compose-native tile-based image viewer (Web / Wasm)
 *
 * @param imageUrl URL to the image (http/https)
 * @param modifier Modifier to be applied to the image viewer
 * @param minScale Minimum scale factor (default: 1.0f)
 * @param maxScale Maximum scale factor (default: 10.0f)
 * @param contentScale Content scaling strategy (default: Fit)
 * @param imageLoader Image loading strategy, or null to use WasmImageLoader (default: null)
 * @param contentDescription Accessibility description
 * @param enableDismissGesture Enable vertical drag-to-dismiss gesture
 * @param enablePagerIntegration Enable horizontal swipe pass-through to parent pager
 * @param showScrollIndicators Show scroll position indicators when zoomed
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
    rotation: Int = 0,
    onDismiss: () -> Unit = {}
) {
    val resolvedLoader = remember(imageLoader) { imageLoader ?: WasmImageLoader() }
    val decoderFactory = remember {
        createWasmDecoderFactory()
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
        onDismiss = onDismiss
    )
}

private fun createWasmDecoderFactory(): (ImageSource) -> RegionDecoder {
    return { source -> WasmRegionDecoder(source) }
}
