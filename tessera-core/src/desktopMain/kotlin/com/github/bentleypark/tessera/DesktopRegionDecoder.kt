package com.github.bentleypark.tessera

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import java.awt.geom.AffineTransform
import java.awt.image.AffineTransformOp
import java.awt.image.BufferedImage
import java.io.File
import kotlin.concurrent.Volatile
import javax.imageio.ImageIO
import javax.imageio.ImageReadParam
import javax.imageio.ImageReader
import javax.imageio.stream.ImageInputStream

/**
 * Desktop (JVM) RegionDecoder using subsampled image cache + tile extraction.
 *
 * Strategy (similar to iOS CgImageSourceRegionDecoder):
 * 1. ImageReader reads image dimensions without full decode
 * 2. Per zoom level, decode at appropriate subsample factor (1, 2, 4, 8)
 *    Large images (30MP+) are capped at subsample factor 2+ to prevent OOM.
 * 3. Cache the subsampled BufferedImage in memory
 * 4. Extract tiles via getSubimage() + copy
 *
 * Memory benefit: at zoom level 0 with sampleSize=2, only 1/4 of full resolution in memory.
 */
class DesktopRegionDecoder(
    private val imageSource: ImageSource
) : RegionDecoder {

    private var reader: ImageReader? = null
    private var imageStream: ImageInputStream? = null
    private var _imageInfo: ImageInfo? = null

    private var rawWidth: Int = 0
    private var rawHeight: Int = 0
    private var rotationDegrees: Int = 0
    private var isMirrored: Boolean = false
    private var minSubsampleFactor: Int = 1

    // Cached subsampled image bundled as immutable snapshot for thread-safe access.
    private data class CachedState(
        val image: BufferedImage,
        val factor: Int,
        val width: Int,
        val height: Int
    )
    @Volatile private var cachedState: CachedState? = null

    @Volatile
    private var disposed = false

    override val imageInfo: ImageInfo
        get() = _imageInfo ?: throw IllegalStateException("Decoder not initialized")

    override fun initialize() {
        if (_imageInfo != null) return

        val file = getFile()
        val stream = ImageIO.createImageInputStream(file)
            ?: throw IllegalStateException("Cannot create ImageInputStream for ${file.absolutePath}")
        imageStream = stream

        val readers = ImageIO.getImageReaders(stream)
        if (!readers.hasNext()) {
            stream.close()
            throw IllegalStateException("No ImageReader found for ${file.absolutePath}")
        }

        val imgReader = readers.next()
        imgReader.input = stream
        reader = imgReader

        rawWidth = imgReader.getWidth(0)
        rawHeight = imgReader.getHeight(0)

        readExifOrientation(file)

        val (displayWidth, displayHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }

        val formatName = imgReader.formatName?.lowercase() ?: "jpeg"
        val mimeType = when (formatName) {
            "jpeg", "jpg" -> "image/jpeg"
            "png" -> "image/png"
            "gif" -> "image/gif"
            "bmp" -> "image/bmp"
            "webp" -> "image/webp"
            else -> "image/$formatName"
        }

        _imageInfo = ImageInfo(
            width = displayWidth,
            height = displayHeight,
            mimeType = mimeType
        )

        // Memory protection for large images
        val megapixels = rawWidth.toLong() * rawHeight.toLong()
        minSubsampleFactor = when {
            megapixels > 80_000_000 -> 4
            megapixels > 30_000_000 -> 2
            else -> 1
        }

        // Pre-warm cache for default zoom level (sampleSize=2)
        val warmupFactor = maxOf(2, minSubsampleFactor)
        getOrCreateCachedImage(warmupFactor)
    }

    override fun decodeTile(rect: TileRect, sampleSize: Int): ImageBitmap? {
        if (disposed) return null

        return try {
            val requestedFactor = when {
                sampleSize >= 8 -> 8
                sampleSize >= 4 -> 4
                sampleSize >= 2 -> 2
                else -> 1
            }
            val factor = maxOf(requestedFactor, minSubsampleFactor)

            // Get cached state atomically — all fields read from the same snapshot
            val state = synchronized(this) {
                getOrCreateCachedImage(factor)
            } ?: return null

            val rawRect = remapRectForOrientation(rect, rawWidth, rawHeight, rotationDegrees, isMirrored)

            // Scale raw coordinates to cached subsampled image dimensions
            val scaleX = state.width.toFloat() / rawWidth.toFloat()
            val scaleY = state.height.toFloat() / rawHeight.toFloat()

            val srcLeft = (rawRect.left * scaleX).toInt().coerceIn(0, state.width)
            val srcTop = (rawRect.top * scaleY).toInt().coerceIn(0, state.height)
            val srcRight = (rawRect.right * scaleX).toInt().coerceIn(srcLeft, state.width)
            val srcBottom = (rawRect.bottom * scaleY).toInt().coerceIn(srcTop, state.height)

            val w = srcRight - srcLeft
            val h = srcBottom - srcTop
            if (w <= 0 || h <= 0) return null

            val tile = state.image.getSubimage(srcLeft, srcTop, w, h)

            // Apply additional downscaling if effective sample > cached factor
            val effectiveSampleSize = maxOf(sampleSize, factor)
            val outputTile = if (effectiveSampleSize > factor) {
                val additionalScale = factor.toDouble() / effectiveSampleSize.toDouble()
                val outW = maxOf(1, (w * additionalScale).toInt())
                val outH = maxOf(1, (h * additionalScale).toInt())
                val scaled = BufferedImage(outW, outH, BufferedImage.TYPE_INT_ARGB)
                val g = scaled.createGraphics()
                g.drawImage(tile, 0, 0, outW, outH, null)
                g.dispose()
                scaled
            } else {
                // Copy to avoid sharing parent raster
                val copy = BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB)
                val g = copy.createGraphics()
                g.drawImage(tile, 0, 0, null)
                g.dispose()
                copy
            }

            val rotated = applyRotation(outputTile)
            rotated.toComposeImageBitmap()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("DesktopRegionDecoder", "decodeTile failed: rect=$rect sampleSize=$sampleSize", e)
            null
        }
    }

    override fun decodePreview(maxSize: Int): ImageBitmap? {
        if (disposed) return null
        val imgReader = reader ?: return null

        return try {
            val info = imageInfo
            val scale = maxOf(info.width / maxSize, info.height / maxSize, 1)

            val preview: BufferedImage = synchronized(this) {
                val param: ImageReadParam = imgReader.defaultReadParam
                if (scale > 1) {
                    param.setSourceSubsampling(scale, scale, 0, 0)
                }
                imgReader.read(0, param)
            }

            val rotated = applyRotation(preview)
            rotated.toComposeImageBitmap()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("DesktopRegionDecoder", "decodePreview failed: maxSize=$maxSize", e)
            null
        }
    }

    override fun close() {
        disposed = true
        cachedState = null
        try {
            reader?.dispose()
        } catch (e: Exception) {
            logWarning("DesktopRegionDecoder", "Failed to dispose ImageReader", e)
        }
        try {
            imageStream?.close()
        } catch (e: Exception) {
            logWarning("DesktopRegionDecoder", "Failed to close ImageInputStream", e)
        }
        reader = null
        imageStream = null
    }

    /**
     * Get or create cached subsampled image for the given factor.
     * If the cached factor differs, re-decode at the new factor.
     * Returns an immutable CachedState snapshot for thread-safe access.
     */
    private fun getOrCreateCachedImage(factor: Int): CachedState? {
        val existing = cachedState
        if (existing != null && existing.factor == factor) return existing

        val imgReader = reader ?: return null

        return try {
            val param: ImageReadParam = imgReader.defaultReadParam
            if (factor > 1) {
                param.setSourceSubsampling(factor, factor, 0, 0)
            }

            val decoded = imgReader.read(0, param)
            val state = CachedState(decoded, factor, decoded.width, decoded.height)
            cachedState = state
            state
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("DesktopRegionDecoder", "Failed to decode at factor=$factor", e)
            null
        }
    }

    private fun getFile(): File {
        return when (val source = imageSource) {
            is ImageSource.FileSource -> source.file
        }
    }

    // --- EXIF orientation ---

    private fun readExifOrientation(file: File) {
        try {
            val stream = ImageIO.createImageInputStream(file) ?: return
            try {
                val readers = ImageIO.getImageReaders(stream)
                if (!readers.hasNext()) return
                val exifReader = readers.next()
                exifReader.input = stream
                try {
                    val metadata = exifReader.getImageMetadata(0) ?: return
                    val formatNames = metadata.metadataFormatNames
                    for (formatName in formatNames) {
                        val root = metadata.getAsTree(formatName)
                        val orientation = findExifOrientation(root)
                        if (orientation != null && orientation != 1) {
                            applyExifOrientation(orientation)
                            break
                        }
                    }
                } finally {
                    exifReader.dispose()
                }
            } finally {
                stream.close()
            }
        } catch (e: Exception) {
            logWarning("DesktopRegionDecoder", "Failed to read EXIF orientation", e)
        }
    }

    private fun findExifOrientation(node: org.w3c.dom.Node): Int? {
        val attrs = node.attributes
        if (attrs != null) {
            val tagAttr = attrs.getNamedItem("Tag") ?: attrs.getNamedItem("number")
            if (tagAttr != null && tagAttr.nodeValue == "274") {
                return findValueInChildren(node)
            }
        }

        val children = node.childNodes
        for (i in 0 until children.length) {
            val result = findExifOrientation(children.item(i))
            if (result != null) return result
        }
        return null
    }

    private fun findValueInChildren(node: org.w3c.dom.Node): Int? {
        val children = node.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i)
            val attrs = child.attributes
            if (attrs != null) {
                val valueAttr = attrs.getNamedItem("value")
                if (valueAttr != null) {
                    return valueAttr.nodeValue.toIntOrNull()
                }
            }
            val result = findValueInChildren(child)
            if (result != null) return result
        }
        val text = node.textContent?.trim()
        if (text != null && text.length <= 2) {
            return text.toIntOrNull()
        }
        return null
    }

    private fun applyExifOrientation(orientation: Int) {
        when (orientation) {
            2 -> { isMirrored = true }
            3 -> { rotationDegrees = 180 }
            4 -> { rotationDegrees = 180; isMirrored = true }
            5 -> { rotationDegrees = 90; isMirrored = true }
            6 -> { rotationDegrees = 90 }
            7 -> { rotationDegrees = 270; isMirrored = true }
            8 -> { rotationDegrees = 270 }
        }
    }

    private fun applyRotation(image: BufferedImage): BufferedImage {
        if (rotationDegrees == 0 && !isMirrored) return image

        val w = image.width
        val h = image.height
        val transform = AffineTransform()

        when {
            rotationDegrees == 90 && !isMirrored -> {
                transform.translate(h.toDouble(), 0.0)
                transform.rotate(Math.toRadians(90.0))
            }
            rotationDegrees == 180 && !isMirrored -> {
                transform.translate(w.toDouble(), h.toDouble())
                transform.rotate(Math.toRadians(180.0))
            }
            rotationDegrees == 270 && !isMirrored -> {
                transform.translate(0.0, w.toDouble())
                transform.rotate(Math.toRadians(270.0))
            }
            rotationDegrees == 0 && isMirrored -> {
                transform.translate(w.toDouble(), 0.0)
                transform.scale(-1.0, 1.0)
            }
            rotationDegrees == 180 && isMirrored -> {
                transform.translate(0.0, h.toDouble())
                transform.scale(1.0, -1.0)
            }
            rotationDegrees == 90 && isMirrored -> {
                transform.translate(h.toDouble(), 0.0)
                transform.rotate(Math.toRadians(90.0))
                transform.translate(w.toDouble(), 0.0)
                transform.scale(-1.0, 1.0)
            }
            rotationDegrees == 270 && isMirrored -> {
                transform.translate(0.0, w.toDouble())
                transform.rotate(Math.toRadians(270.0))
                transform.translate(h.toDouble(), 0.0)
                transform.scale(-1.0, 1.0)
            }
        }

        val (newW, newH) = if (rotationDegrees == 90 || rotationDegrees == 270) h to w else w to h
        val rotated = BufferedImage(newW, newH, BufferedImage.TYPE_INT_ARGB)
        val g = rotated.createGraphics()
        g.drawImage(image, AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR), 0, 0)
        g.dispose()
        return rotated
    }
}
