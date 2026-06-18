package com.github.bentleypark.tessera

import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
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
    private val tileSize: Int = 256,
    private val maxCacheSize: Int = (150 * 256 * 256 / (tileSize * tileSize)).coerceAtLeast(50)
) {
    @Volatile
    private var decoder: RegionDecoder? = null
    @Volatile
    private var tileManager: TileManager? = null
    @Volatile
    private var disposed = false
    private val tileCache = mutableStateMapOf<String, Pair<ImageBitmap, TileCoordinate>>()
    private val tileCacheAccessOrder = mutableListOf<String>()

    /** Tile metadata tracked separately from [tileCache] so renderers needn't subscribe to bitmap identity churn. */
    internal val loadedTiles: SnapshotStateMap<String, TileLoadInfo> = mutableStateMapOf()

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

    /** Number of tiles currently held in the LRU cache. Tracked separately to avoid
     *  subscribing composition to the entire SnapshotStateMap. */
    var cachedTileCount by mutableIntStateOf(0)
        private set

    /** Bump to force the tile-load pipeline to re-emit even when viewport is unchanged. */
    internal var reloadGeneration by mutableIntStateOf(0)
        private set

    /** Synchronous init for testing and simple usage. Must be called on the main thread. */
    fun initialize() {
        val result = initializeDecoder()
        applyInitResult(result)
    }

    /**
     * Heavy initialization (file I/O + Skia decode). Safe to call from any thread.
     * Sets decoder and tileManager internally. Call [applyInitResult] on the main thread afterward
     * to update Compose state.
     */
    internal fun initializeDecoder(): InitResult {
        val initStart = currentTimeMillis()
        return try {
            val regionDecoder = decoderFactory(imageSource)
            regionDecoder.initialize()
            val info = regionDecoder.imageInfo
            val decoderTime = currentTimeMillis() - initStart

            decoder = regionDecoder
            tileManager = TileManager(info, tileSize)

            // Warn about large non-JPEG images (subsample APIs don't save memory for PNG, etc.)
            val format = ImageFormat.fromMimeType(info.mimeType)
            val pixels = info.width.toLong() * info.height.toLong()
            if (format != ImageFormat.JPEG && pixels > 30_000_000) {
                logWarning("Tessera", "${format.name} image (${pixels / 1_000_000}MP) " +
                    "may cause high memory usage on iOS/Desktop. " +
                    "Subsample APIs decode the full image internally for non-JPEG formats. " +
                    "JPEG is recommended for images over 30MP.")
            }

            val previewStart = currentTimeMillis()
            val preview = regionDecoder.decodePreview(maxSize = 1024)
            val previewTime = currentTimeMillis() - previewStart

            val totalTime = currentTimeMillis() - initStart
            logWarning("TesseraPerf", "init: ${info.width}x${info.height} " +
                "decoder=${decoderTime}ms preview=${previewTime}ms total=${totalTime}ms")

            InitResult.Success(info, preview)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logError("TesseraState", "initialize failed", e)
            InitResult.Error("${e.simpleClassName()}: ${e.message ?: "Unknown error"}")
        }
    }

    /** Apply decode results to Compose state. Must be called on the main thread. */
    internal fun applyInitResult(result: InitResult) {
        when (result) {
            is InitResult.Success -> {
                error = null
                imageInfo = result.info
                previewBitmap = result.preview
                isLoading = false
            }
            is InitResult.Error -> {
                decoder = null
                tileManager = null
                error = result.message
                isLoading = false
            }
        }
    }

    internal sealed class InitResult {
        class Success(
            val info: ImageInfo,
            val preview: ImageBitmap?
        ) : InitResult()
        class Error(val message: String) : InitResult()
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

    /** Decode tile without touching Compose state. Safe to call from any thread. */
    fun decodeTile(coordinate: TileCoordinate): ImageBitmap? {
        if (disposed) {
            logWarning("TesseraState", "decodeTile called after dispose: ${coordinate.toKey()}")
            return null
        }
        val regionDecoder = decoder ?: run {
            logWarning("TesseraState", "decodeTile: decoder is null for ${coordinate.toKey()}")
            return null
        }
        val manager = tileManager ?: run {
            logWarning("TesseraState", "decodeTile: tileManager is null for ${coordinate.toKey()}")
            return null
        }

        val rect = manager.getTileRect(coordinate)
        val sampleSize = manager.calculateSampleSize(coordinate.zoomLevel)

        val tileKey = coordinate.toKey()
        val tileStart = currentTimeMillis()
        return regionDecoder.decodeTile(rect, sampleSize)?.also {
            val tileTime = currentTimeMillis() - tileStart
            if (tileTime > 50) {
                logWarning("TesseraPerf", "slowTile: $tileKey ${tileTime}ms " +
                    "rect=${rect.right - rect.left}x${rect.bottom - rect.top} sample=$sampleSize")
            }
        }
    }

    /** Decode + cache in one call. Must be called on the main thread (writes Compose state). */
    fun loadTile(coordinate: TileCoordinate, cache: Boolean = true): ImageBitmap? {
        val key = coordinate.toKey()
        tileCache[key]?.let {
            updateAccessOrder(key)
            return it.first
        }

        val bitmap = decodeTile(coordinate) ?: return null
        if (cache) cacheTile(coordinate, bitmap)
        return bitmap
    }

    /** Cache a decoded tile. Must be called on the main thread. */
    fun cacheTile(coordinate: TileCoordinate, bitmap: ImageBitmap) {
        val key = coordinate.toKey()
        evictLRUIfNeeded()
        tileCache[key] = bitmap to coordinate
        updateAccessOrder(key)
        cachedTileCount = tileCache.size
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
        loadedTiles.clear()
        cachedTileCount = 0
    }

    /** Releases tiles but keeps the decoder, so resume can re-tile without re-opening the image. */
    internal fun clearCacheForBackground() {
        if (disposed) return
        val releasedTiles = cachedTileCount
        tileCache.clear()
        tileCacheAccessOrder.clear()
        loadedTiles.clear()
        cachedTileCount = 0
        reloadGeneration++
        logWarning("TesseraPerf", "bgClear: released=$releasedTiles reloadGen=$reloadGeneration")
    }
}
