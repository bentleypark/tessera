package com.github.bentleypark.tessera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BitmapRegionDecoder
import android.graphics.Rect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.ImageBitmap
import java.io.File
import java.io.InputStream

/**
 * Wrapper for Android's BitmapRegionDecoder to handle tile-based image decoding.
 */
class ImageDecoder(
    private val imageSource: ImageSource,
    private val tempFileProvider: (String, InputStream) -> File
) : RegionDecoder {
    private var decoder: BitmapRegionDecoder? = null
    private var _imageInfo: ImageInfo? = null
    private var fallbackFile: File? = null

    @Volatile
    private var isDecoderInitialized = false
    private val decoderLock = Any()

    override val imageInfo: ImageInfo
        get() = _imageInfo ?: throw IllegalStateException("Decoder not initialized")

    override fun initialize() {
        if (_imageInfo != null) return

        val options = BitmapFactory.Options().apply {
            inJustDecodeBounds = true
        }

        decodeBounds(options)
        _imageInfo = ImageInfo(
            width = options.outWidth,
            height = options.outHeight,
            mimeType = options.outMimeType ?: "image/jpeg"
        )

        ensureDecoderInitialized()
    }

    private fun ensureDecoderInitialized() {
        if (isDecoderInitialized) return

        synchronized(decoderLock) {
            if (isDecoderInitialized) return

            decoder = createRegionDecoder()
            isDecoderInitialized = true
        }
    }

    override fun decodeTile(rect: TileRect, sampleSize: Int): ImageBitmap? {
        ensureDecoderInitialized()
        val currentDecoder = decoder ?: return null

        return try {
            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }

            val androidRect = Rect(rect.left, rect.top, rect.right, rect.bottom)
            currentDecoder.decodeRegion(androidRect, options)?.asImageBitmap()
        } catch (e: Exception) {
            logError("ImageDecoder", "decodeTile failed: rect=$rect sampleSize=$sampleSize", e)
            null
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
            if (preview == null) {
                getOrCreateFallbackFileForSource()?.let { fallback ->
                    return BitmapFactory.decodeFile(fallback.absolutePath, options)?.asImageBitmap()
                }
            }
            preview?.asImageBitmap()
        } catch (e: Exception) {
            logError("ImageDecoder", "decodePreview failed: maxSize=$maxSize", e)
            null
        }
    }

    override fun close() {
        decoder?.recycle()
        decoder = null
        isDecoderInitialized = false
        fallbackFile?.delete()
        fallbackFile = null
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
