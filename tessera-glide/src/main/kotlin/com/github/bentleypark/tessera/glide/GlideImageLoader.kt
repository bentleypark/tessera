package com.github.bentleypark.tessera.glide

import android.content.Context
import timber.log.Timber
import com.bumptech.glide.Glide
import com.github.bentleypark.tessera.ImageLoaderStrategy
import com.github.bentleypark.tessera.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.coroutines.cancellation.CancellationException

/**
 * Glide-based image loader for local files, content URIs, and network fallback.
 */
class GlideImageLoader(private val context: Context) : ImageLoaderStrategy {

    override suspend fun loadImageSource(
        imageUrl: String
    ): Result<ImageSource> = withContext(Dispatchers.IO) {
        try {
            val file = Glide.with(context)
                .asFile()
                .load(imageUrl)
                .submit()
                .get()

            Result.success(ImageSource.FileSource(file))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.tag("GlideImageLoader").e(e, "Failed to load image: $imageUrl")
            Result.failure(e)
        }
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        Glide.get(context).clearDiskCache()
    }
}
