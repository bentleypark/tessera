package com.naemomlab.tessera

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Coil-based image loader.
 * Uses the app's singleton ImageLoader which includes the OkHttp network fetcher.
 */
class CoilImageLoader(private val context: Context) : ImageLoaderStrategy {

    private val imageLoader: coil3.ImageLoader
        get() = coil3.SingletonImageLoader.get(context)

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val loader = imageLoader
            val request = coil3.request.ImageRequest.Builder(context)
                .data(imageUrl)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build()

            val result = loader.execute(request)

            if (result is coil3.request.SuccessResult) {
                val diskCache = loader.diskCache
                if (diskCache != null) {
                    val snapshot = diskCache.openSnapshot(imageUrl)
                    snapshot?.use {
                        val file = it.data.toFile()
                        return@withContext Result.success(ImageSource.FileSource(file))
                    }
                }
            }

            Result.failure(Exception("Failed to load image with Coil"))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun clearCache(): Unit = withContext(Dispatchers.IO) {
        val loader = imageLoader
        loader.diskCache?.clear()
        loader.memoryCache?.clear()
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
     * Convenience constructor using Coil for network and resource loading.
     * For local file/content URI support, add tessera-glide and pass a GlideImageLoader as [local].
     */
    constructor(context: Context) : this(
        network = CoilImageLoader(context),
        local = null,
        resource = ResourceImageLoader(context)
    )

    constructor(context: Context, local: ImageLoaderStrategy) : this(
        network = CoilImageLoader(context),
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
