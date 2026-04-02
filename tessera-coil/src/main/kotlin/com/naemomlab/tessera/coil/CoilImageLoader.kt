package com.naemomlab.tessera.coil

import android.content.Context
import timber.log.Timber
import coil3.ImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.naemomlab.tessera.ImageLoaderStrategy
import com.naemomlab.tessera.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/**
 * Coil 3.x-based image loader for network images, content URIs, and local files.
 *
 * Uses Coil's disk cache for efficient image loading. Downloads are cached
 * automatically, so repeated loads of the same URL skip network requests.
 *
 * @param context Android context
 * @param imageLoader Custom Coil ImageLoader instance. If null, a default one is created
 *                    with disk caching enabled.
 */
class CoilImageLoader(
    private val context: Context,
    private val imageLoader: ImageLoader = createDefaultImageLoader(context)
) : ImageLoaderStrategy {

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(imageUrl)
                .build()

            val result = imageLoader.execute(request)

            if (result is SuccessResult) {
                // Try to get the cached file from Coil's disk cache
                val diskCacheKey = result.diskCacheKey
                val diskCache = imageLoader.diskCache

                if (diskCacheKey != null && diskCache != null) {
                    val snapshot = diskCache.openSnapshot(diskCacheKey)
                    if (snapshot != null) {
                        try {
                            val cachedFile = snapshot.data.toFile()
                            if (cachedFile.exists() && cachedFile.length() > 0) {
                                return@withContext Result.success(ImageSource.FileSource(cachedFile))
                            }
                        } catch (e: Exception) {
                            Timber.tag(TAG).w(e, "Failed to read disk cache: $imageUrl")
                        } finally {
                            snapshot.close()
                        }
                    }
                }

                // Fallback: download via Coil and save to temp file
                val safeName = sha256(imageUrl)
                val tempFile = File(context.cacheDir, "tessera_coil_$safeName")

                if (tempFile.exists() && tempFile.length() > 0) {
                    return@withContext Result.success(ImageSource.FileSource(tempFile))
                }

                // Re-fetch as bytes and write to temp file
                val fetchRequest = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .build()
                val fetchResult = imageLoader.execute(fetchRequest)

                if (fetchResult is SuccessResult) {
                    val reDiskCacheKey = fetchResult.diskCacheKey
                    if (reDiskCacheKey != null && diskCache != null) {
                        val reSnapshot = diskCache.openSnapshot(reDiskCacheKey)
                        if (reSnapshot != null) {
                            try {
                                val src = reSnapshot.data.toFile()
                                src.copyTo(tempFile, overwrite = true)
                                return@withContext Result.success(ImageSource.FileSource(tempFile))
                            } catch (e: Exception) {
                                Timber.tag(TAG).w(e, "Failed to copy cache to temp: $imageUrl")
                            } finally {
                                reSnapshot.close()
                            }
                        }
                    }
                }

                Result.failure(Exception("Coil loaded image but could not resolve file: $imageUrl"))
            } else {
                val cause = (result as? ErrorResult)?.throwable
                Result.failure(Exception("Coil load failed: $imageUrl", cause))
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to load image: $imageUrl")
            Result.failure(e)
        }
    }

    override suspend fun clearCache() {
        withContext(Dispatchers.IO) {
            try {
                imageLoader.diskCache?.clear()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to clear disk cache")
            }
            try {
                imageLoader.memoryCache?.clear()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to clear memory cache")
            }
        }
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }

    companion object {
        private const val TAG = "CoilImageLoader"

        private fun createDefaultImageLoader(context: Context): ImageLoader {
            return ImageLoader.Builder(context)
                .diskCache {
                    DiskCache.Builder()
                        .directory(context.cacheDir.resolve("tessera_coil_cache"))
                        .maxSizePercent(0.05)
                        .build()
                }
                .build()
        }
    }
}
