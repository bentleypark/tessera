package com.naemomlab.tessera

/**
 * Image loading strategy interface.
 * Platform-free for KMP compatibility — implementations receive platform context via constructor.
 */
interface ImageLoaderStrategy {
    suspend fun loadImageSource(imageUrl: String): Result<ImageSource>
    suspend fun clearCache()
}
