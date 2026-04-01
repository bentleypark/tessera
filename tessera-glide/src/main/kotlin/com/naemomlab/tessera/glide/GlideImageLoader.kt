package com.naemomlab.tessera.glide

import android.content.Context
import com.bumptech.glide.Glide
import com.naemomlab.tessera.ImageLoaderStrategy
import com.naemomlab.tessera.ImageSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
        } catch (e: Exception) {
            e.printStackTrace()
            Result.failure(e)
        }
    }

    override suspend fun clearCache() = withContext(Dispatchers.IO) {
        Glide.get(context).clearDiskCache()
    }
}
