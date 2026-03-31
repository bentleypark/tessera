package com.naemomlab.tessera

import android.content.Context
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Glide-based image loader.
 */
class GlideImageLoader(private val context: Context) : ImageLoaderStrategy {
    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val file = com.bumptech.glide.Glide.with(context)
                .asFile()
                .load(imageUrl)
                .submit()
                .get()

            Result.success(ImageSource.FileSource(file))
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        com.bumptech.glide.Glide.get(context).clearDiskCache()
    }
}

/**
 * Coil-based image loader.
 */
class CoilImageLoader(private val context: Context) : ImageLoaderStrategy {
    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val imageLoader = coil3.ImageLoader(context)
            val request = coil3.request.ImageRequest.Builder(context)
                .data(imageUrl)
                .diskCachePolicy(coil3.request.CachePolicy.ENABLED)
                .build()

            val result = imageLoader.execute(request)

            if (result is coil3.request.SuccessResult) {
                val diskCache = imageLoader.diskCache
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
        val imageLoader = coil3.ImageLoader(context)
        imageLoader.diskCache?.clear()
        imageLoader.memoryCache?.clear()
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
 * - http/https -> Coil (network images with disk caching)
 * - android.resource -> Resource (app bundled images)
 * - file/content -> Glide (local files, gallery images)
 * - unknown scheme -> Glide (fallback)
 */
class RoutingImageLoader(
    private val coil: ImageLoaderStrategy,
    private val glide: ImageLoaderStrategy,
    private val resource: ImageLoaderStrategy
) : ImageLoaderStrategy {

    constructor(context: Context) : this(
        coil = CoilImageLoader(context),
        glide = GlideImageLoader(context),
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
            "http", "https" -> coil
            "android.resource" -> resource
            "file", "content" -> glide
            else -> glide
        }
        val result = primary.loadImageSource(imageUrl)
        if (result.isSuccess) return result

        // Coil 실패 시 Glide로 fallback (네트워크 이미지)
        if (primary === coil) {
            return glide.loadImageSource(imageUrl)
        }
        return result
    }

    override suspend fun clearCache() {
        coil.clearCache()
        glide.clearCache()
    }
}
