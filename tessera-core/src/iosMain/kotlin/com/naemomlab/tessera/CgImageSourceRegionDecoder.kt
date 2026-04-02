package com.naemomlab.tessera

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.atomicfu.locks.withLock
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect as SkiaRect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.ImageInfo as SkiaImageInfo
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.kCFAllocatorDefault
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextGetData
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawImage
import platform.CoreGraphics.CGContextRelease
import platform.CoreGraphics.CGImageAlphaInfo
import platform.CoreGraphics.CGImageGetHeight
import platform.CoreGraphics.CGImageGetWidth
import platform.CoreGraphics.CGImageRelease
import platform.CoreGraphics.CGImageRef
import platform.CoreGraphics.CGRectMake
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.ImageIO.CGImageSourceCreateImageAtIndex
import platform.ImageIO.CGImageSourceCreateThumbnailAtIndex
import platform.ImageIO.CGImageSourceCreateWithData
import platform.ImageIO.CGImageSourceCreateWithURL
import platform.ImageIO.CGImageSourceRef
import platform.ImageIO.kCGImageSourceCreateThumbnailFromImageAlways
import platform.ImageIO.kCGImageSourceCreateThumbnailWithTransform
import platform.ImageIO.kCGImageSourceShouldCacheImmediately
import platform.ImageIO.kCGImageSourceSubsampleFactor
import platform.ImageIO.kCGImageSourceThumbnailMaxPixelSize
import platform.posix.memcpy

/**
 * iOS region decoder combining CGImageSource subsample decoding with Skia tile extraction.
 *
 * Strategy:
 * 1. CGImageSource reads image dimensions without full decode
 * 2. Per zoom level, CGImageSource decodes at appropriate subsample (1/2, 1/4, 1/8)
 * 3. Subsampled CGImage is converted to Skia Image ONCE per zoom level
 * 4. Skia Canvas.drawImageRect extracts tiles at ~1ms each
 *
 * Memory benefit: at zoom level 0, only 1/4 of full resolution is in memory.
 * Speed benefit: tile extraction uses Skia's optimized memory operations.
 * Large images (30MP+) are capped at subsample factor 2+ to prevent OOM.
 */
@OptIn(ExperimentalForeignApi::class)
class CgImageSourceRegionDecoder(
    private val imageSource: ImageSource
) : RegionDecoder {

    private var cgImageSource: CGImageSourceRef? = null
    private var _imageInfo: ImageInfo? = null
    private var minSubsampleFactor: Int = 1

    // Cached Skia image and actual dimensions for the current subsample level.
    // Access synchronized via cacheLock.
    @Volatile private var cachedSkiaImage: Image? = null
    @Volatile private var cachedFactor: Int = 0
    @Volatile private var cachedWidth: Int = 0
    @Volatile private var cachedHeight: Int = 0
    private val cacheLock = kotlinx.atomicfu.locks.ReentrantLock()

    override val imageInfo: ImageInfo
        get() = _imageInfo ?: throw IllegalStateException("Decoder not initialized")

    override fun initialize() {
        if (_imageInfo != null) return

        val source = createImageSource()
            ?: throw IllegalStateException("Failed to create CGImageSource")
        cgImageSource = source

        val factorNum = CFBridgingRetain(NSNumber(int = 1))
            ?: throw IllegalStateException("Failed to create NSNumber")
        val options = createDictionary(kCGImageSourceSubsampleFactor to factorNum)
        CFRelease(factorNum)

        val cgImage = CGImageSourceCreateImageAtIndex(source, 0u, options)
        CFRelease(options)

        if (cgImage == null) {
            throw IllegalStateException("Failed to read image at index 0")
        }

        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()
        CGImageRelease(cgImage)

        if (width <= 0 || height <= 0) {
            throw IllegalStateException("Invalid image dimensions: ${width}x${height}")
        }

        _imageInfo = ImageInfo(width = width, height = height)

        val megapixels = width.toLong() * height.toLong()
        minSubsampleFactor = when {
            megapixels > 80_000_000 -> 4
            megapixels > 30_000_000 -> 2
            else -> 1
        }

        // Pre-warm the default zoom level
        val warmupFactor = maxOf(2, minSubsampleFactor)
        val warmupImage = getOrCreateSkiaImage(source, warmupFactor)
        if (warmupImage == null) {
            logWarning("CgRegionDecoder", "Cache warm-up failed for factor=$warmupFactor")
        }
    }

    override fun decodeTile(rect: TileRect, sampleSize: Int): ImageBitmap? {
        val source = cgImageSource ?: run {
            logWarning("CgRegionDecoder", "decodeTile called but source is null")
            return null
        }

        val requestedFactor = when {
            sampleSize >= 8 -> 8
            sampleSize >= 4 -> 4
            sampleSize >= 2 -> 2
            else -> 1
        }
        val factor = maxOf(requestedFactor, minSubsampleFactor)

        // Hold lock for entire tile decode to prevent use-after-close
        return cacheLock.withLock {
            val skiaImage = getOrCreateSkiaImage(source, factor) ?: return@withLock null

            val info = _imageInfo ?: return@withLock null
            val scaleX = cachedWidth.toFloat() / info.width.toFloat()
            val scaleY = cachedHeight.toFloat() / info.height.toFloat()

            val effectiveSampleSize = maxOf(sampleSize, factor)
            val tileWidth = (rect.right - rect.left) / effectiveSampleSize
            val tileHeight = (rect.bottom - rect.top) / effectiveSampleSize
            if (tileWidth <= 0 || tileHeight <= 0) return@withLock null

            val bitmap = Bitmap()
            try {
                bitmap.allocPixels(
                    SkiaImageInfo(tileWidth, tileHeight, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
                )

                val canvas = Canvas(bitmap)
                val srcRect = SkiaRect(
                    rect.left.toFloat() * scaleX,
                    rect.top.toFloat() * scaleY,
                    rect.right.toFloat() * scaleX,
                    rect.bottom.toFloat() * scaleY
                )
                val dstRect = SkiaRect(0f, 0f, tileWidth.toFloat(), tileHeight.toFloat())
                canvas.drawImageRect(skiaImage, srcRect, dstRect, SamplingMode.LINEAR, Paint(), true)

                Image.makeFromBitmap(bitmap).toComposeImageBitmap()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                logError("CgRegionDecoder", "decodeTile failed: rect=$rect sampleSize=$sampleSize", e)
                null
            } finally {
                bitmap.close()
            }
        }
    }

    override fun decodePreview(maxSize: Int): ImageBitmap? {
        val source = cgImageSource ?: run {
            logWarning("CgRegionDecoder", "decodePreview called but source is null")
            return null
        }

        val maxSizeNum = CFBridgingRetain(NSNumber(int = maxSize)) ?: run {
            logWarning("CgRegionDecoder", "CFBridgingRetain failed for maxSize=$maxSize")
            return null
        }
        val options = createDictionary(
            kCGImageSourceCreateThumbnailFromImageAlways to kCFBooleanTrue,
            kCGImageSourceCreateThumbnailWithTransform to kCFBooleanTrue,
            kCGImageSourceThumbnailMaxPixelSize to maxSizeNum
        )
        CFRelease(maxSizeNum)

        val thumbnail = CGImageSourceCreateThumbnailAtIndex(source, 0u, options)
        CFRelease(options)

        if (thumbnail == null) {
            logWarning("CgRegionDecoder", "Failed to create thumbnail")
            return null
        }

        return try {
            val width = CGImageGetWidth(thumbnail).toInt()
            val height = CGImageGetHeight(thumbnail).toInt()
            cgImageToSkiaImage(thumbnail, width, height)?.toComposeImageBitmap()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("CgRegionDecoder", "decodePreview failed: maxSize=$maxSize", e)
            null
        } finally {
            CGImageRelease(thumbnail)
        }
    }

    override fun close() {
        cacheLock.withLock {
            cachedSkiaImage?.close()
            cachedSkiaImage = null
            cachedFactor = 0
            cachedWidth = 0
            cachedHeight = 0
        }
        cgImageSource?.let { CFRelease(it) }
        cgImageSource = null
        _imageInfo = null
    }

    /**
     * Get or create a Skia Image for the given subsample factor.
     * Must be called within synchronized(cacheLock).
     */
    private fun getOrCreateSkiaImage(source: CGImageSourceRef, factor: Int): Image? {
        if (cachedFactor == factor && cachedSkiaImage != null) {
            return cachedSkiaImage
        }

        // Create new image BEFORE releasing old one
        val factorNum = CFBridgingRetain(NSNumber(int = factor)) ?: run {
            logWarning("CgRegionDecoder", "CFBridgingRetain failed for factor=$factor")
            return cachedSkiaImage // fallback to old
        }
        val options = createDictionary(
            kCGImageSourceSubsampleFactor to factorNum,
            kCGImageSourceShouldCacheImmediately to kCFBooleanTrue
        )
        CFRelease(factorNum)

        val cgImage = CGImageSourceCreateImageAtIndex(source, 0u, options)
        CFRelease(options)

        if (cgImage == null) {
            logWarning("CgRegionDecoder", "Failed to create subsampled image factor=$factor, keeping previous")
            return cachedSkiaImage // fallback to old
        }

        val width = CGImageGetWidth(cgImage).toInt()
        val height = CGImageGetHeight(cgImage).toInt()

        logWarning("TesseraPerf", "subsample: factor=$factor size=${width}x${height}")

        val newImage = cgImageToSkiaImage(cgImage, width, height)
        CGImageRelease(cgImage)

        if (newImage == null) {
            logWarning("CgRegionDecoder", "cgImageToSkiaImage failed for factor=$factor, keeping previous")
            return cachedSkiaImage // fallback to old
        }

        // Success — now release old image
        cachedSkiaImage?.close()
        cachedSkiaImage = newImage
        cachedFactor = factor
        cachedWidth = width
        cachedHeight = height
        return newImage
    }

    private fun cgImageToSkiaImage(cgImage: CGImageRef, width: Int, height: Int): Image? {
        val colorSpace = CGColorSpaceCreateDeviceRGB() ?: return null

        val bytesPerRow = width * 4
        val bitmapInfo = CGImageAlphaInfo.kCGImageAlphaPremultipliedLast.value

        val context = CGBitmapContextCreate(
            data = null,
            width = width.toULong(),
            height = height.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = bytesPerRow.toULong(),
            space = colorSpace,
            bitmapInfo = bitmapInfo
        )
        CGColorSpaceRelease(colorSpace)

        if (context == null) {
            logWarning("CgRegionDecoder", "Failed to create bitmap context ${width}x${height}")
            return null
        }

        return try {
            CGContextDrawImage(
                context,
                CGRectMake(0.0, 0.0, width.toDouble(), height.toDouble()),
                cgImage
            )

            val pixelData = CGBitmapContextGetData(context)
            if (pixelData == null) {
                logWarning("CgRegionDecoder", "CGBitmapContextGetData returned null for ${width}x${height}")
                return null
            }

            val totalBytes = bytesPerRow * height
            val byteArray = ByteArray(totalBytes)
            byteArray.usePinned { pinned ->
                memcpy(pinned.addressOf(0), pixelData, totalBytes.convert())
            }

            val bitmap = Bitmap()
            bitmap.allocPixels(
                SkiaImageInfo(width, height, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
            )
            bitmap.installPixels(byteArray)

            val skiaImage = Image.makeFromBitmap(bitmap)
            bitmap.close()
            skiaImage
        } finally {
            CGContextRelease(context)
        }
    }

    private fun createImageSource(): CGImageSourceRef? {
        return when (val src = imageSource) {
            is ImageSource.PathSource -> {
                val nsUrl = NSURL.fileURLWithPath(src.path)
                val cfUrl = CFBridgingRetain(nsUrl) as? platform.CoreFoundation.CFURLRef ?: run {
                    logWarning("CgRegionDecoder", "CFBridgingRetain failed for path: ${src.path}")
                    return null
                }
                val source = CGImageSourceCreateWithURL(cfUrl, null)
                CFRelease(cfUrl)
                if (source == null) {
                    logWarning("CgRegionDecoder", "CGImageSourceCreateWithURL returned null for: ${src.path}")
                }
                source
            }
            is ImageSource.DataSource -> {
                val bytes = src.getData()
                bytes.usePinned { pinned ->
                    val cfData = CFDataCreate(
                        kCFAllocatorDefault,
                        pinned.addressOf(0).reinterpret(),
                        bytes.size.convert()
                    )
                    if (cfData != null) {
                        val source = CGImageSourceCreateWithData(cfData, null)
                        CFRelease(cfData)
                        source
                    } else {
                        null
                    }
                }
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun createDictionary(vararg pairs: Pair<Any?, Any?>): platform.CoreFoundation.CFDictionaryRef {
        val dict = CFDictionaryCreateMutable(kCFAllocatorDefault, pairs.size.convert(), null, null)
            ?: throw IllegalStateException("Failed to allocate CFDictionary")
        for ((key, value) in pairs) {
            if (key != null && value != null) {
                platform.CoreFoundation.CFDictionarySetValue(
                    dict,
                    key as platform.CoreFoundation.CFTypeRef?,
                    value as platform.CoreFoundation.CFTypeRef?
                )
            }
        }
        return dict
    }

}
