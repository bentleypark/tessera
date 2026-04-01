package com.naemomlab.tessera

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Network image loader that downloads images to a temp file.
 * No external dependencies required — uses standard java.net.URL.
 */
class NetworkImageLoader(private val context: Context) : ImageLoaderStrategy {

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val safeName = imageUrl.hashCode().toString()
            val tempFile = java.io.File(context.cacheDir, "tessera_$safeName")

            if (!tempFile.exists() || tempFile.length() == 0L) {
                val connection = java.net.URL(imageUrl).openConnection().apply {
                    connectTimeout = 15_000
                    readTimeout = 30_000
                }
                connection.getInputStream().use { input ->
                    tempFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
            }

            Result.success(ImageSource.FileSource(tempFile))
        } catch (e: Exception) {
            logError("NetworkImageLoader", "Failed to download: $imageUrl", e)
            Result.failure(e)
        }
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        context.cacheDir.listFiles()?.forEach { file ->
            if (file.name.startsWith("tessera_")) {
                file.delete()
            }
        }
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
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun clearCache() = Unit
}

/**
 * Routes image loading based on URI scheme.
 * - http/https -> primary network loader (Coil by default)
 * - android.resource -> Resource (app bundled images)
 * - file/content/other -> fallback loader
 *
 * If no fallback is provided, the network loader handles all schemes.
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
        } catch (_: Exception) {
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
            return local.loadImageSource(imageUrl)
        }
        return result
    }

    override suspend fun clearCache() {
        network.clearCache()
        local?.clearCache()
    }
}
