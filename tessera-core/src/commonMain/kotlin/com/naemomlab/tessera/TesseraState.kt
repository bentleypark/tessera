package com.naemomlab.tessera

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.ImageBitmap
import kotlin.concurrent.Volatile
import kotlin.coroutines.cancellation.CancellationException

/**
 * Manages the state of the Tessera image viewer including tile caching and viewport tracking.
 */
@Stable
class TesseraState(
    private val imageSource: ImageSource,
    private val decoderFactory: (ImageSource) -> RegionDecoder,
    private val maxCacheSize: Int = 150
) {
    @Volatile
    private var decoder: RegionDecoder? = null
    @Volatile
    private var tileManager: TileManager? = null
    @Volatile
    private var disposed = false
    private val tileCache = mutableStateMapOf<String, Pair<ImageBitmap, TileCoordinate>>()
    private val tileCacheAccessOrder = mutableListOf<String>()

    var imageInfo by mutableStateOf<ImageInfo?>(null)
        private set
    var isLoading by mutableStateOf(true)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var viewport by mutableStateOf(Viewport())
        private set
    var previewBitmap by mutableStateOf<ImageBitmap?>(null)
        private set

    fun initialize() {
        try {
            isLoading = true
            error = null

            val regionDecoder = decoderFactory(imageSource)
            regionDecoder.initialize()
            decoder = regionDecoder

            val info = regionDecoder.imageInfo
            imageInfo = info

            regionDecoder.decodePreview(maxSize = 1024)?.let {
                previewBitmap = it
            }

            tileManager = TileManager(info)

            isLoading = false
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("TesseraState", "initialize failed", e)
            error = "${e.simpleClassName()}: ${e.message ?: "Unknown error"}"
            isLoading = false
        }
    }

    fun updateViewport(newViewport: Viewport) {
        viewport = newViewport
    }

    fun getVisibleTiles(): List<TileCoordinate> {
        return tileManager?.getVisibleTiles(viewport) ?: emptyList()
    }

    fun getCachedTile(coordinate: TileCoordinate): ImageBitmap? {
        val key = coordinate.toKey()
        updateAccessOrder(key)
        return tileCache[key]?.first
    }

    fun getCachedTileByKey(key: String): Pair<ImageBitmap, TileCoordinate>? {
        updateAccessOrder(key)
        return tileCache[key]
    }

    private fun updateAccessOrder(key: String) {
        tileCacheAccessOrder.remove(key)
        tileCacheAccessOrder.add(key)
    }

    fun loadTile(coordinate: TileCoordinate, cache: Boolean = true): ImageBitmap? {
        val key = coordinate.toKey()
        tileCache[key]?.let {
            return it.first
        }

        if (disposed) return null
        val regionDecoder = decoder ?: return null
        val manager = tileManager ?: return null

        val rect = manager.getTileRect(coordinate)
        val sampleSize = manager.calculateSampleSize(coordinate.zoomLevel)

        return regionDecoder.decodeTile(rect, sampleSize)?.also {
            if (cache) {
                evictLRUIfNeeded()
                tileCache[key] = it to coordinate
                updateAccessOrder(key)
            }
        }
    }

    private fun evictLRUIfNeeded() {
        while (tileCache.size >= maxCacheSize && tileCacheAccessOrder.isNotEmpty()) {
            val oldestKey = tileCacheAccessOrder.removeAt(0)
            tileCache.remove(oldestKey)
        }
    }

    fun getTileRect(coordinate: TileCoordinate): TileRect {
        return tileManager?.getTileRect(coordinate) ?: TileRect(0, 0, 0, 0)
    }

    fun dispose() {
        disposed = true
        decoder?.close()
        decoder = null
        tileCache.clear()
        tileCacheAccessOrder.clear()
    }
}
