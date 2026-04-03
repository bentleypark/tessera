package com.github.bentleypark.tessera

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.coroutines.cancellation.CancellationException
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface

/**
 * Web (Wasm) RegionDecoder using Skia Surface for tile extraction.
 *
 * Strategy (similar to iOS CgImageSourceRegionDecoder):
 * 1. Decode full image from ByteArray via Skia Image.makeFromEncoded()
 * 2. Cache the decoded Skia Image in memory
 * 3. Extract tiles via Surface + Canvas.drawImageRect() + makeImageSnapshot()
 *
 * Uses Surface.makeRasterN32Premul for CPU-side rendering.
 * Limitation: full image loaded into memory (~30MP max in browser).
 */
class WasmRegionDecoder(
    private val imageSource: ImageSource
) : RegionDecoder {

    private var _imageInfo: ImageInfo? = null
    private var skiaImage: Image? = null
    private var disposed = false

    override val imageInfo: ImageInfo
        get() = _imageInfo ?: throw IllegalStateException("Decoder not initialized")

    override fun initialize() {
        if (_imageInfo != null) return

        val bytes = when (val source = imageSource) {
            is ImageSource.DataSource -> source.data
        }

        if (bytes.isEmpty()) {
            throw IllegalStateException("Image data is empty")
        }

        val image = Image.makeFromEncoded(bytes)
        skiaImage = image

        if (image.width <= 0 || image.height <= 0) {
            throw IllegalStateException("Invalid image dimensions: ${image.width}x${image.height}")
        }

        _imageInfo = ImageInfo(
            width = image.width,
            height = image.height,
            mimeType = "image/unknown"
        )
    }

    override fun decodeTile(rect: TileRect, sampleSize: Int): ImageBitmap? {
        if (disposed) return null
        val image = skiaImage ?: return null

        return try {
            val srcLeft = rect.left.coerceIn(0, image.width)
            val srcTop = rect.top.coerceIn(0, image.height)
            val srcRight = rect.right.coerceIn(srcLeft, image.width)
            val srcBottom = rect.bottom.coerceIn(srcTop, image.height)

            val w = srcRight - srcLeft
            val h = srcBottom - srcTop
            if (w <= 0 || h <= 0) return null

            val outW = maxOf(1, w / sampleSize)
            val outH = maxOf(1, h / sampleSize)

            extractRegion(image, srcLeft, srcTop, srcRight, srcBottom, outW, outH)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("WasmRegionDecoder", "decodeTile failed: rect=$rect sampleSize=$sampleSize", e)
            null
        }
    }

    override fun decodePreview(maxSize: Int): ImageBitmap? {
        if (disposed) return null
        val image = skiaImage ?: return null

        return try {
            val info = imageInfo
            val scale = maxOf(info.width / maxSize, info.height / maxSize, 1)
            val outW = maxOf(1, info.width / scale)
            val outH = maxOf(1, info.height / scale)

            extractRegion(image, 0, 0, image.width, image.height, outW, outH)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("WasmRegionDecoder", "decodePreview failed: maxSize=$maxSize", e)
            null
        }
    }

    override fun close() {
        disposed = true
        skiaImage?.close()
        skiaImage = null
    }

    private fun extractRegion(
        image: Image,
        srcLeft: Int, srcTop: Int, srcRight: Int, srcBottom: Int,
        outW: Int, outH: Int
    ): ImageBitmap {
        val surface = Surface.makeRasterN32Premul(outW, outH)
        try {
            surface.canvas.drawImageRect(
                image,
                SkiaRect.makeLTRB(
                    srcLeft.toFloat(), srcTop.toFloat(),
                    srcRight.toFloat(), srcBottom.toFloat()
                ),
                SkiaRect.makeWH(outW.toFloat(), outH.toFloat()),
                SamplingMode.DEFAULT,
                null,
                true
            )
            val snapshot = surface.makeImageSnapshot()
            val bitmap = snapshot.toComposeImageBitmap()
            snapshot.close()
            return bitmap
        } finally {
            surface.close()
        }
    }
}
