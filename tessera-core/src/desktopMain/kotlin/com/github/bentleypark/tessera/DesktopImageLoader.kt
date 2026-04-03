package com.github.bentleypark.tessera

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import kotlin.coroutines.cancellation.CancellationException

/**
 * Desktop image loader that handles HTTP/HTTPS URLs and local file paths.
 * Downloads to system temp directory with SHA-256 cache keys.
 * Uses atomic staging file pattern (tmp -> rename) for crash-safe downloads.
 */
class DesktopImageLoader : ImageLoaderStrategy {

    private val cacheDir: File by lazy {
        File(System.getProperty("java.io.tmpdir"), "tessera_cache").also {
            it.mkdirs()
        }
    }

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val scheme = imageUrl.substringBefore("://").lowercase()

            when (scheme) {
                "file" -> {
                    val path = imageUrl.removePrefix("file://")
                    val file = File(path)
                    if (!file.exists()) {
                        return@withContext Result.failure(
                            IllegalArgumentException("File not found: $path")
                        )
                    }
                    Result.success(ImageSource.FileSource(file))
                }
                "http", "https" -> {
                    val safeName = sha256(imageUrl)
                    val cachedFile = File(cacheDir, "tessera_$safeName")

                    if (!cachedFile.exists() || cachedFile.length() == 0L) {
                        val stagingFile = File(cacheDir, "tessera_${safeName}.tmp")
                        try {
                            val connection = URI(imageUrl).toURL().openConnection().apply {
                                connectTimeout = 15_000
                                readTimeout = 30_000
                            }
                            // Validate HTTP status code
                            (connection as? HttpURLConnection)?.let { http ->
                                val code = http.responseCode
                                if (code !in 200..299) {
                                    http.disconnect()
                                    return@withContext Result.failure(
                                        IllegalStateException("HTTP $code for $imageUrl")
                                    )
                                }
                            }
                            connection.getInputStream().use { input ->
                                stagingFile.outputStream().use { output ->
                                    input.copyTo(output)
                                }
                            }
                            Files.move(
                                stagingFile.toPath(),
                                cachedFile.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        } finally {
                            if (stagingFile.exists()) {
                                stagingFile.delete()
                            }
                        }
                    }

                    Result.success(ImageSource.FileSource(cachedFile))
                }
                else -> {
                    // Try as local file path
                    val file = File(imageUrl)
                    if (file.exists()) {
                        Result.success(ImageSource.FileSource(file))
                    } else {
                        Result.failure(
                            IllegalArgumentException("Unsupported scheme or file not found: $imageUrl")
                        )
                    }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("DesktopImageLoader", "Failed to load: $imageUrl", e)
            Result.failure(e)
        }
    }

    override suspend fun clearCache(): Unit = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("tessera_")) {
                file.delete()
            }
        }
        Unit
    }

    private fun sha256(input: String): String {
        return MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}
