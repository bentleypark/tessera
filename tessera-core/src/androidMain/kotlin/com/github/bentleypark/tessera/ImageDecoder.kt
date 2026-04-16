package com.github.bentleypark.tessera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Matrix
import android.graphics.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.InputStream
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * Wrapper for Android's BitmapRegionDecoder to handle tile-based image decoding.
 * Uses a pool of decoder instances for parallel tile decoding (FileSource only).
 * Automatically handles EXIF orientation for correct display.
 */
class ImageDecoder(
    private val imageSource: ImageSource,
    private val tempFileProvider: (String, InputStream) -> File,
    private val maxDecoderInstances: Int = 2
) : RegionDecoder {
    private var _imageInfo: ImageInfo? = null
    private var fallbackFile: File? = null

    // Decoder pool for parallel decoding
    private var decoderPool: ArrayBlockingQueue<BitmapRegionDecoder>? = null
    private val allDecoders = mutableListOf<BitmapRegionDecoder>()

    // EXIF orientation: raw pixel dimensions (before rotation)
    private var rawWidth: Int = 0
    private var rawHeight: Int = 0
    private var rotationDegrees: Int = 0
    private var isMirrored: Boolean = false

    @Volatile
    private var isDecoderInitialized = false
    @Volatile
    private var isClosed = false
    private val decoderLock = Any()

    // File path for creating additional decoder instances
    private var decoderFilePath: String? = null

    override val imageInfo: ImageInfo
        get() = _imageInfo ?: throw IllegalStateException("Decoder not initialized")

    override fun initialize() {
        if (_imageInfo != null) return

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        decodeBounds(options)
        rawWidth = options.outWidth
        rawHeight = options.outHeight

        // Read EXIF orientation
        readExifOrientation()

        // Report post-rotation dimensions
        val (displayWidth, displayHeight) = if (rotationDegrees == 90 || rotationDegrees == 270) {
            rawHeight to rawWidth
        } else {
            rawWidth to rawHeight
        }

        _imageInfo = ImageInfo(
            width = displayWidth,
            height = displayHeight,
            mimeType = options.outMimeType ?: "image/jpeg"
        )

        ensureDecoderInitialized()
    }

    private fun readExifOrientation() {
        try {
            val exif = when (val source = imageSource) {
                is ImageSource.FileSource -> ExifInterface(source.file.absolutePath)
                is ImageSource.ResourceSource -> {
                    source.openStream().use { ExifInterface(it) }
                }
            }

            when (exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90 -> { rotationDegrees = 90 }
                ExifInterface.ORIENTATION_ROTATE_180 -> { rotationDegrees = 180 }
                ExifInterface.ORIENTATION_ROTATE_270 -> { rotationDegrees = 270 }
                ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> { isMirrored = true }
                ExifInterface.ORIENTATION_FLIP_VERTICAL -> { rotationDegrees = 180; isMirrored = true }
                ExifInterface.ORIENTATION_TRANSPOSE -> { rotationDegrees = 90; isMirrored = true }
                ExifInterface.ORIENTATION_TRANSVERSE -> { rotationDegrees = 270; isMirrored = true }
                else -> { /* ORIENTATION_NORMAL or ORIENTATION_UNDEFINED */ }
            }
        } catch (e: Exception) {
            logWarning("ImageDecoder", "Failed to read EXIF orientation", e)
        }
    }

    private fun ensureDecoderInitialized() {
        if (isDecoderInitialized) return

        synchronized(decoderLock) {
            if (isDecoderInitialized) return

            // Resolve file path for pool creation
            val filePath = resolveFilePath()
            decoderFilePath = filePath

            val poolSize = if (filePath != null) maxDecoderInstances else 1
            val pool = ArrayBlockingQueue<BitmapRegionDecoder>(poolSize)

            // Create first decoder
            val firstDecoder = createRegionDecoder()
            if (firstDecoder != null) {
                pool.add(firstDecoder)
                allDecoders.add(firstDecoder)
            }

            // Create additional decoders from file path (only for FileSource)
            if (filePath != null && firstDecoder != null) {
                for (i in 1 until poolSize) {
                    try {
                        val extra = createRegionDecoderFromFile(File(filePath))
                        if (extra != null) {
                            pool.add(extra)
                            allDecoders.add(extra)
                        }
                    } catch (e: Exception) {
                        logWarning("ImageDecoder", "Failed to create pool decoder #$i", e)
                        break
                    }
                }
            }

            decoderPool = pool
            isDecoderInitialized = true

            if (allDecoders.size > 1) {
                logWarning("TesseraPerf", "decoder pool: ${allDecoders.size} instances")
            }
        }
    }

    private fun resolveFilePath(): String? {
        return when (val source = imageSource) {
            is ImageSource.FileSource -> source.file.absolutePath
            is ImageSource.ResourceSource -> {
                getOrCreateFallbackFile(source.description, source.openStream)?.absolutePath
            }
        }
    }

    private fun acquireDecoder(): BitmapRegionDecoder? {
        if (isClosed) return null
        return decoderPool?.poll(5, TimeUnit.SECONDS)
    }

    private fun releaseDecoder(decoder: BitmapRegionDecoder) {
        if (isClosed) {
            decoder.recycle()
            return
        }
        decoderPool?.put(decoder)
    }

    override fun decodeTile(rect: TileRect, sampleSize: Int): ImageBitmap? {
        ensureDecoderInitialized()
        val currentDecoder = acquireDecoder() ?: return null

        return try {
            val format = ImageFormat.fromMimeType(imageInfo.mimeType)
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = if (format == ImageFormat.JPEG) {
                    Bitmap.Config.RGB_565
                } else {
                    Bitmap.Config.ARGB_8888
                }
            }

            // Remap display coordinates to raw pixel coordinates
            val rawRect = remapRect(rect)
            val androidRect = Rect(rawRect.left, rawRect.top, rawRect.right, rawRect.bottom)
            val decoded = currentDecoder.decodeRegion(androidRect, options) ?: return null

            // Apply rotation (thread-safe, operates on independent bitmap)
            val rotated = applyRotation(decoded)
            if (rotated !== decoded) decoded.recycle()
            rotated.asImageBitmap()
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("ImageDecoder", "decodeTile failed: rect=$rect sampleSize=$sampleSize", e)
            null
        } finally {
            releaseDecoder(currentDecoder)
        }
    }

    override fun decodePreview(maxSize: Int): ImageBitmap? {
        return try {
            val info = imageInfo
            val scale = maxOf(
                info.width / maxSize,
                info.height / maxSize,
                1
            )

            val options = BitmapFactory.Options().apply {
                inSampleSize = scale
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val preview = openStream().use { stream ->
                BitmapFactory.decodeStream(stream, null, options)
            }
            val bitmap = if (preview == null) {
                getOrCreateFallbackFileForSource()?.let { fallback ->
                    BitmapFactory.decodeFile(fallback.absolutePath, options)
                }
            } else {
                preview
            }

            // Apply rotation to preview
            bitmap?.let {
                val rotated = applyRotation(it)
                if (rotated !== it) it.recycle()
                rotated.asImageBitmap()
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("ImageDecoder", "decodePreview failed: maxSize=$maxSize", e)
            null
        }
    }

    override fun close() {
        synchronized(decoderLock) {
            isClosed = true
            // Only recycle decoders currently in the pool.
            // In-flight decoders will be recycled when returned via releaseDecoder().
            val remaining = mutableListOf<BitmapRegionDecoder>()
            decoderPool?.drainTo(remaining)
            remaining.forEach { it.recycle() }
            allDecoders.clear()
            decoderPool = null
            isDecoderInitialized = false
            fallbackFile?.delete()
            fallbackFile = null
            decoderFilePath = null
        }
    }

    private fun remapRect(rect: TileRect): TileRect {
        return remapRectForOrientation(rect, rawWidth, rawHeight, rotationDegrees, isMirrored)
    }

    /**
     * Apply EXIF rotation/mirror to a decoded bitmap.
     * Returns the same bitmap if no rotation needed.
     */
    private fun applyRotation(bitmap: Bitmap): Bitmap {
        if (rotationDegrees == 0 && !isMirrored) return bitmap

        val matrix = Matrix()
        if (isMirrored) matrix.preScale(-1f, 1f)
        if (rotationDegrees != 0) matrix.postRotate(rotationDegrees.toFloat())

        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun decodeBounds(options: BitmapFactory.Options) {
        when (val source = imageSource) {
            is ImageSource.FileSource -> {
                BitmapFactory.decodeFile(source.file.absolutePath, options)
            }

            is ImageSource.ResourceSource -> {
                val decoded = source.openStream().use { stream ->
                    BitmapFactory.decodeStream(stream, null, options)
                }
                if ((options.outWidth <= 0 || options.outHeight <= 0) && decoded == null) {
                    getOrCreateFallbackFile(
                        source.description,
                        source.openStream
                    )?.let { fallback ->
                        BitmapFactory.decodeFile(fallback.absolutePath, options)
                    }
                }
            }
        }
    }

    private fun createRegionDecoder(): BitmapRegionDecoder? {
        return try {
            when (val source = imageSource) {
                is ImageSource.FileSource -> {
                    createRegionDecoderFromFile(source.file)
                }

                is ImageSource.ResourceSource -> {
                    createRegionDecoderFromDescriptor(source).also {
                        if (it == null) {
                            val fallback =
                                getOrCreateFallbackFile(source.description, source.openStream)
                            return fallback?.let { file -> createRegionDecoderFromFile(file) }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logWarning("ImageDecoder", "createRegionDecoder failed", e)
            null
        }
    }

    private fun createRegionDecoderFromFile(file: File): BitmapRegionDecoder? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            BitmapRegionDecoder.newInstance(file.absolutePath)
        } else {
            @Suppress("DEPRECATION")
            file.inputStream().use { inputStream ->
                BitmapRegionDecoder.newInstance(inputStream, false)
            }
        }
    }

    private fun createRegionDecoderFromDescriptor(source: ImageSource.ResourceSource): BitmapRegionDecoder? {
        return try {
            @Suppress("DEPRECATION")
            source.openStream().use { stream ->
                BitmapRegionDecoder.newInstance(stream, false)
            }
        } catch (e: Exception) {
            logWarning("ImageDecoder", "createRegionDecoderFromDescriptor failed", e)
            null
        }
    }

    private fun getOrCreateFallbackFileForSource(): File? {
        return when (val source = imageSource) {
            is ImageSource.FileSource -> source.file
            is ImageSource.ResourceSource -> getOrCreateFallbackFile(
                source.description,
                source.openStream
            )
        }
    }

    private fun getOrCreateFallbackFile(
        description: String,
        openStream: () -> InputStream
    ): File? {
        val existing = fallbackFile
        if (existing != null) return existing
        val created = openStream().use { stream ->
            tempFileProvider(description, stream)
        }
        fallbackFile = created
        return created
    }

    private fun openStream(): InputStream {
        return when (val source = imageSource) {
            is ImageSource.FileSource -> source.file.inputStream()
            is ImageSource.ResourceSource -> source.openStream()
        }
    }
}
