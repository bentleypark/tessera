package com.github.bentleypark.tessera

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

/**
 * Tessera - Compose-native tile-based image viewer (Android)
 *
 * @param imageUrl URL or path to the image (http/https, file://, content://)
 * @param modifier Modifier to be applied to the image viewer
 * @param minScale Minimum scale factor (default: 1.0f)
 * @param maxScale Maximum scale factor (default: 10.0f)
 * @param contentScale Content scaling strategy (default: Fit)
 * @param imageLoader Image loading strategy (default: RoutingImageLoader)
 * @param contentDescription Accessibility description
 * @param enableDismissGesture Enable vertical drag-to-dismiss gesture
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
    val context = LocalContext.current
    val resolvedLoader = remember(imageLoader) { imageLoader ?: RoutingImageLoader(context) }
    val decoderFactory = remember {
        createAndroidDecoderFactory(context.cacheDir)
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

/**
 * Tessera - Compose-native tile-based image viewer for Android resource images
 *
 * @param imageResId Android drawable resource ID (e.g., R.drawable.my_image)
 */
@Composable
fun TesseraImage(
    imageResId: Int,
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
    val context = LocalContext.current
    val resolvedLoader = remember(imageLoader) { imageLoader ?: ResourceImageLoader(context) }
    val imageUrl = "android.resource://${context.packageName}/$imageResId"
    val decoderFactory = remember {
        createAndroidDecoderFactory(context.cacheDir)
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

private fun createAndroidDecoderFactory(cacheDir: java.io.File): (ImageSource) -> RegionDecoder {
    return { source ->
        val tempProvider: (String, java.io.InputStream) -> java.io.File =
            { description, stream ->
                val safeName = description.hashCode().toString()
                val outFile = java.io.File(cacheDir, "tessera_$safeName")
                outFile.outputStream().use { output ->
                    stream.copyTo(output)
                }
                outFile
            }
        ImageDecoder(source, tempProvider)
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Tessera - URL")
@Composable
private fun TesseraImagePreview() {
    TesseraImage(
        imageUrl = "https://example.com/image.jpg",
        modifier = Modifier.fillMaxSize()
    )
}

@Preview(showBackground = true, widthDp = 360, heightDp = 640, name = "Tessera - Resource")
@Composable
private fun TesseraImageResourcePreview() {
    TesseraImage(
        imageResId = android.R.drawable.ic_menu_gallery,
        modifier = Modifier.fillMaxSize()
    )
}
