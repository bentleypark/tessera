package com.github.bentleypark.tessera

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Network image loader that downloads images to a temp file.
 * No external dependencies required — uses standard java.net.URL.
 */
class NetworkImageLoader(private val context: Context) : ImageLoaderStrategy {

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val safeName = sha256(imageUrl)
            val tempFile = java.io.File(context.cacheDir, "tessera_$safeName")

            if (!tempFile.exists() || tempFile.length() == 0L) {
                val stagingFile = java.io.File(context.cacheDir, "tessera_${safeName}.tmp")
                try {
                    val connection = java.net.URL(imageUrl).openConnection().apply {
                        connectTimeout = 15_000
                        readTimeout = 30_000
                    }
                    connection.getInputStream().use { input ->
                        stagingFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    stagingFile.renameTo(tempFile)
                } finally {
                    stagingFile.delete()
                }
            }

            Result.success(ImageSource.FileSource(tempFile))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("NetworkImageLoader", "Failed to download: $imageUrl", e)
            Result.failure(e)
        }
    }

    override suspend fun clearCache(): Unit = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("tessera_")) {
                file.delete()
            }
        }
        Unit
    }

    private fun sha256(input: String): String {
        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    }
}

/**
 * Resource-based image loader for android.resource:// URIs.
 */
class ResourceImageLoader(private val context: Context) : ImageLoaderStrategy {
    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val uri = imageUrl.toUri()
            Result.success(
                ImageSource.ResourceSource(
                    openStream = {
                        context.contentResolver.openInputStream(uri)
                            ?: throw IllegalArgumentException("No stream for $imageUrl")
                    },
                    description = imageUrl
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("ResourceImageLoader", "Failed to load resource: $imageUrl", e)
            Result.failure(e)
        }
    }

    override suspend fun clearCache() = Unit
}

/**
 * Routes image loading based on URI scheme.
 * - http/https -> NetworkImageLoader (default)
 * - android.resource -> ResourceImageLoader (app bundled images)
 * - file/content/other -> local loader (e.g. GlideImageLoader from tessera-glide)
 *
 * If no local loader is provided, the network loader handles all schemes.
 */
class RoutingImageLoader(
    private val network: ImageLoaderStrategy,
    private val local: ImageLoaderStrategy? = null,
    private val resource: ImageLoaderStrategy
) : ImageLoaderStrategy {

    /**
     * Convenience constructor using NetworkImageLoader for network images.
     * For local file/content URI support, add tessera-glide and pass a GlideImageLoader as [local].
     */
    constructor(context: Context) : this(
        network = NetworkImageLoader(context),
        local = null,
        resource = ResourceImageLoader(context)
    )

    constructor(context: Context, local: ImageLoaderStrategy) : this(
        network = NetworkImageLoader(context),
        local = local,
        resource = ResourceImageLoader(context)
    )

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> {
        val scheme = try {
            imageUrl.toUri().scheme?.lowercase()
        } catch (e: Exception) {
            logWarning("RoutingImageLoader", "Failed to parse URI: $imageUrl", e)
            null
        }
        val primary = when (scheme) {
            "http", "https" -> network
            "android.resource" -> resource
            "file", "content" -> local ?: network
            else -> local ?: network
        }
        val result = primary.loadImageSource(imageUrl)
        if (result.isSuccess) return result

        // Network loader failure → fallback to local loader
        if (primary === network && local != null) {
            logWarning("RoutingImageLoader", "Primary loader failed, falling back to local", result.exceptionOrNull())
            return local.loadImageSource(imageUrl)
        }
        return result
    }

    override suspend fun clearCache() {
        network.clearCache()
        local?.clearCache()
    }
}
