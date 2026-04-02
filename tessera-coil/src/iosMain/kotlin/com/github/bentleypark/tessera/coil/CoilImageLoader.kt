package com.github.bentleypark.tessera.coil

import coil3.ImageLoader
import coil3.PlatformContext
import coil3.disk.DiskCache
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.github.bentleypark.tessera.ImageLoaderStrategy
import com.github.bentleypark.tessera.ImageSource
import com.github.bentleypark.tessera.ioDispatcher
import com.github.bentleypark.tessera.logError
import com.github.bentleypark.tessera.logWarning
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.withContext
import okio.Path.Companion.toPath
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import kotlin.coroutines.cancellation.CancellationException

/**
 * Coil 3.x-based image loader for iOS.
 *
 * Uses Coil KMP with Ktor (Darwin engine) for network requests.
 * Downloads images to temp files and returns ImageSource.PathSource.
 */
class CoilImageLoader(
    private val imageLoader: ImageLoader = createDefaultImageLoader()
) : ImageLoaderStrategy {

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(ioDispatcher) {
        try {
            val request = ImageRequest.Builder(PlatformContext.INSTANCE)
                .data(imageUrl)
                .build()

            val result = imageLoader.execute(request)

            if (result is SuccessResult) {
                val diskCacheKey = result.diskCacheKey
                val diskCache = imageLoader.diskCache

                if (diskCacheKey != null && diskCache != null) {
                    val snapshot = diskCache.openSnapshot(diskCacheKey)
                    if (snapshot != null) {
                        try {
                            val cachedPath = snapshot.data.toString()
                            val fileManager = NSFileManager.defaultManager
                            if (fileManager.fileExistsAtPath(cachedPath)) {
                                return@withContext Result.success(ImageSource.PathSource(cachedPath))
                            } else {
                                logWarning("CoilImageLoader", "Disk cache path does not exist: $cachedPath")
                            }
                        } catch (e: Exception) {
                            logWarning("CoilImageLoader", "Failed to read disk cache: $imageUrl", e)
                        } finally {
                            snapshot.close()
                        }
                    }
                }

                // Fallback: re-fetch and copy to temp file
                val safeName = sha256(imageUrl)
                val tempPath = NSTemporaryDirectory() + "tessera_coil_$safeName"

                val fileManager = NSFileManager.defaultManager
                if (fileManager.fileExistsAtPath(tempPath)) {
                    return@withContext Result.success(ImageSource.PathSource(tempPath))
                }

                // Re-execute to populate disk cache
                val reFetchResult = imageLoader.execute(
                    ImageRequest.Builder(PlatformContext.INSTANCE).data(imageUrl).build()
                )
                if (reFetchResult is SuccessResult) {
                    val reKey = reFetchResult.diskCacheKey
                    if (reKey != null && diskCache != null) {
                        val reSnapshot = diskCache.openSnapshot(reKey)
                        if (reSnapshot != null) {
                            try {
                                val srcPath = reSnapshot.data.toString()
                                @OptIn(ExperimentalForeignApi::class)
                                if (fileManager.fileExistsAtPath(srcPath)) {
                                    fileManager.copyItemAtPath(srcPath, tempPath, null)
                                    return@withContext Result.success(ImageSource.PathSource(tempPath))
                                }
                            } catch (e: Exception) {
                                logWarning("CoilImageLoader", "Failed to copy cache to temp: $imageUrl", e)
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
            logError("CoilImageLoader", "Failed to load image: $imageUrl", e)
            Result.failure(e)
        }
    }

    override suspend fun clearCache() {
        withContext(ioDispatcher) {
            try {
                imageLoader.diskCache?.clear()
            } catch (e: Exception) {
                logWarning("CoilImageLoader", "Failed to clear disk cache", e)
            }
            try {
                imageLoader.memoryCache?.clear()
            } catch (e: Exception) {
                logWarning("CoilImageLoader", "Failed to clear memory cache", e)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun sha256(input: String): String {
        val data = input.encodeToByteArray()
        val digest = UByteArray(CC_SHA256_DIGEST_LENGTH)
        data.usePinned { pinned ->
            digest.usePinned { digestPinned ->
                CC_SHA256(pinned.addressOf(0), data.size.convert(), digestPinned.addressOf(0))
            }
        }
        return digest.joinToString("") { it.toString(16).padStart(2, '0') }
    }

    companion object {
        /** Factory for Swift interop (Kotlin default params not visible in Swift) */
        fun create(): CoilImageLoader = CoilImageLoader()

        private fun createDefaultImageLoader(): ImageLoader {
            val cacheDir = NSSearchPathForDirectoriesInDomains(
                NSCachesDirectory, NSUserDomainMask, true
            ).firstOrNull() as? String
            if (cacheDir == null) {
                logWarning("CoilImageLoader", "NSCachesDirectory not found, falling back to NSTemporaryDirectory")
            }
            val effectiveCacheDir = cacheDir ?: NSTemporaryDirectory()

            return ImageLoader.Builder(PlatformContext.INSTANCE)
                .diskCache {
                    DiskCache.Builder()
                        .directory("$effectiveCacheDir/tessera_coil_cache".toPath())
                        .maxSizePercent(0.05)
                        .build()
                }
                .build()
        }
    }
}
