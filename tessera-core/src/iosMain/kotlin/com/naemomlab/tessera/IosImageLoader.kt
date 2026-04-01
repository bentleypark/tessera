package com.naemomlab.tessera

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import platform.CoreCrypto.CC_SHA256
import platform.CoreCrypto.CC_SHA256_DIGEST_LENGTH
import platform.Foundation.NSFileManager
import platform.Foundation.NSHTTPURLResponse
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.NSURLRequest
import platform.Foundation.NSURLSession
import platform.Foundation.dataTaskWithRequest
import platform.Foundation.writeToFile
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.resume

/**
 * iOS image loader using NSURLSession for network images
 * and NSFileManager for local files.
 */
class IosImageLoader : ImageLoaderStrategy {

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(ioDispatcher) {
        try {
            val url = NSURL.URLWithString(imageUrl)
                ?: return@withContext Result.failure(IllegalArgumentException("Invalid URL: $imageUrl"))

            val scheme = url.scheme?.lowercase()

            when (scheme) {
                "file" -> {
                    val path = url.path
                        ?: return@withContext Result.failure(IllegalArgumentException("No path for: $imageUrl"))
                    Result.success(ImageSource.PathSource(path))
                }
                "http", "https" -> {
                    val tempPath = downloadToTempFile(url, imageUrl)
                    if (tempPath != null) {
                        Result.success(ImageSource.PathSource(tempPath))
                    } else {
                        Result.failure(Exception("Failed to download: $imageUrl"))
                    }
                }
                else -> {
                    Result.failure(IllegalArgumentException("Unsupported scheme: $scheme"))
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("IosImageLoader", "loadImageSource failed: $imageUrl", e)
            Result.failure(e)
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    override suspend fun clearCache() {
        val tempDir = NSTemporaryDirectory()
        val fileManager = NSFileManager.defaultManager
        val contents = fileManager.contentsOfDirectoryAtPath(tempDir, null)
        contents?.forEach { item ->
            val name = item as? String ?: return@forEach
            if (name.startsWith("tessera_")) {
                fileManager.removeItemAtPath(tempDir + name, null)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private suspend fun downloadToTempFile(url: NSURL, imageUrl: String): String? {
        return suspendCancellableCoroutine { continuation ->
            val request = NSURLRequest.requestWithURL(url)
            val task = NSURLSession.sharedSession.dataTaskWithRequest(request) { data, response, error ->
                if (!continuation.isActive) return@dataTaskWithRequest

                if (error != null) {
                    logError("IosImageLoader", "Download error for $imageUrl: ${error.localizedDescription} (code=${error.code})")
                    continuation.resume(null)
                    return@dataTaskWithRequest
                }

                if (data == null) {
                    logError("IosImageLoader", "Download returned null data for $imageUrl")
                    continuation.resume(null)
                    return@dataTaskWithRequest
                }

                val httpResponse = response as? NSHTTPURLResponse
                val statusCode = httpResponse?.statusCode?.toInt() ?: -1
                if (statusCode !in 200..299) {
                    logError("IosImageLoader", "HTTP $statusCode for $imageUrl")
                    continuation.resume(null)
                    return@dataTaskWithRequest
                }

                val safeName = sha256(imageUrl)
                val tempPath = NSTemporaryDirectory() + "tessera_$safeName"
                val success = data.writeToFile(tempPath, atomically = true)

                if (success) {
                    continuation.resume(tempPath)
                } else {
                    logError("IosImageLoader", "Failed to write temp file: $tempPath")
                    continuation.resume(null)
                }
            }
            task.resume()
            continuation.invokeOnCancellation { task.cancel() }
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
}
