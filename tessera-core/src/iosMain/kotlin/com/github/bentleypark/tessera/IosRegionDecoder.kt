package com.github.bentleypark.tessera

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.UByteVar
import kotlinx.cinterop.get
import kotlinx.cinterop.reinterpret
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo as SkiaImageInfo
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.SamplingMode

/**
 * iOS implementation of RegionDecoder using Skia for tile-based decoding.
 *
 * NOTE: Current implementation loads the full image into memory via Image.makeFromEncoded.
 * Unlike Android's BitmapRegionDecoder which decodes only requested regions,
 * this holds the entire decoded image. For very large images (8K+, 50MP+),
 * this may cause high memory usage. A future optimization could use
 * CGImageSource + CGImageCreateWithImageInRect via cinterop for true partial decoding.
 *
 * TODO: Implement streaming region decode for large images (Phase 3 follow-up)
 */
class IosRegionDecoder(
    private val imageSource: ImageSource
) : RegionDecoder {

    private var _imageInfo: ImageInfo? = null
    private var skiaImage: Image? = null

    override val imageInfo: ImageInfo
        get() = _imageInfo ?: throw IllegalStateException("Decoder not initialized")

    override fun initialize() {
        if (_imageInfo != null) return

        val bytes = loadImageBytes()
            ?: throw IllegalStateException("Failed to load image data")

        val image = Image.makeFromEncoded(bytes)
        skiaImage = image

        _imageInfo = ImageInfo(
            width = image.width,
            height = image.height
        )
    }

    override fun decodeTile(rect: TileRect, sampleSize: Int): ImageBitmap? {
        val image = skiaImage ?: run {
            logWarning("IosRegionDecoder", "decodeTile called but skiaImage is null (disposed?)")
            return null
        }

        val tileWidth = (rect.right - rect.left) / sampleSize
        val tileHeight = (rect.bottom - rect.top) / sampleSize
        if (tileWidth <= 0 || tileHeight <= 0) return null

        val bitmap = Bitmap()
        return try {
            bitmap.allocPixels(
                SkiaImageInfo(tileWidth, tileHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
            )

            val canvas = Canvas(bitmap)
            val srcRect = SkiaRect(
                rect.left.toFloat(),
                rect.top.toFloat(),
                rect.right.toFloat(),
                rect.bottom.toFloat()
            )
            val dstRect = SkiaRect(0f, 0f, tileWidth.toFloat(), tileHeight.toFloat())
            canvas.drawImageRect(image, srcRect, dstRect, SamplingMode.LINEAR, Paint(), true)

            Image.makeFromBitmap(bitmap).toComposeImageBitmap()
        } catch (e: Exception) {
            logError("IosRegionDecoder", "decodeTile failed: rect=$rect sampleSize=$sampleSize", e)
            null
        } finally {
            bitmap.close()
        }
    }

    override fun decodePreview(maxSize: Int): ImageBitmap? {
        val image = skiaImage ?: run {
            logWarning("IosRegionDecoder", "decodePreview called but skiaImage is null (disposed?)")
            return null
        }

        val bitmap = Bitmap()
        return try {
            val info = imageInfo
            val scale = maxOf(info.width / maxSize, info.height / maxSize, 1)
            val previewWidth = info.width / scale
            val previewHeight = info.height / scale

            bitmap.allocPixels(
                SkiaImageInfo(previewWidth, previewHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
            )

            val canvas = Canvas(bitmap)
            val srcRect = SkiaRect(0f, 0f, info.width.toFloat(), info.height.toFloat())
            val dstRect = SkiaRect(0f, 0f, previewWidth.toFloat(), previewHeight.toFloat())
            canvas.drawImageRect(image, srcRect, dstRect, SamplingMode.LINEAR, Paint(), true)

            Image.makeFromBitmap(bitmap).toComposeImageBitmap()
        } catch (e: Exception) {
            logError("IosRegionDecoder", "decodePreview failed: maxSize=$maxSize", e)
            null
        } finally {
            bitmap.close()
        }
    }

    override fun close() {
        skiaImage?.close()
        skiaImage = null
        _imageInfo = null
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun loadImageBytes(): ByteArray? {
        return when (val src = imageSource) {
            is ImageSource.PathSource -> {
                val data = platform.Foundation.NSFileManager.defaultManager.contentsAtPath(src.path)
                if (data == null) {
                    logWarning("IosRegionDecoder", "File not found: ${src.path}")
                    return null
                }
                data.toKotlinByteArray()
            }
            is ImageSource.DataSource -> src.getData()
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun platform.Foundation.NSData.toKotlinByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val bytesPtr = bytes ?: return ByteArray(0)
    val nativeBytes = bytesPtr.reinterpret<UByteVar>()
    return ByteArray(size) { nativeBytes[it].toByte() }
}
