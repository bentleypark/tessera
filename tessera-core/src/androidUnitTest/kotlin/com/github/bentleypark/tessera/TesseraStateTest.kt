package com.github.bentleypark.tessera

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeRegionDecoder(
    private val width: Int = 1024,
    private val height: Int = 768,
    private val shouldFailInit: Boolean = false,
    private val shouldReturnNullTile: Boolean = false
) : RegionDecoder {

    private var _imageInfo: ImageInfo? = null
    var closeCalled = false
        private set
    var decodeTileCount = 0
        private set

    override val imageInfo: ImageInfo
        get() = _imageInfo ?: throw IllegalStateException("Not initialized")

    override fun initialize() {
        if (shouldFailInit) throw RuntimeException("Fake init failure")
        _imageInfo = ImageInfo(width, height)
    }

    override fun decodeTile(rect: TileRect, sampleSize: Int): ImageBitmap? {
        decodeTileCount++
        if (shouldReturnNullTile) return null
        val w = maxOf(1, (rect.right - rect.left) / sampleSize)
        val h = maxOf(1, (rect.bottom - rect.top) / sampleSize)
        return Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888).asImageBitmap()
    }

    override fun decodePreview(maxSize: Int): ImageBitmap? {
        val scale = maxOf(width / maxSize, height / maxSize, 1)
        return Bitmap.createBitmap(width / scale, height / scale, Bitmap.Config.ARGB_8888)
            .asImageBitmap()
    }

    override fun close() {
        closeCalled = true
    }
}

@RunWith(RobolectricTestRunner::class)
class TesseraStateTest {

    private val dummySource = ImageSource.FileSource(File("/fake/path"))

    private fun createInitializedState(
        decoder: FakeRegionDecoder = FakeRegionDecoder(),
        maxCacheSize: Int = 150
    ): Pair<TesseraState, FakeRegionDecoder> {
        val state = TesseraState(dummySource, { decoder }, maxCacheSize = maxCacheSize)
        state.initialize()
        return state to decoder
    }

    // --- Initialize ---

    @Test
    fun initialize_success_setsImageInfoAndLoading() {
        val decoder = FakeRegionDecoder(width = 1920, height = 1080)
        val state = TesseraState(dummySource, { decoder }, maxCacheSize = 150)

        assertTrue(state.isLoading)
        assertNull(state.imageInfo)

        state.initialize()

        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.imageInfo)
        assertEquals(1920, state.imageInfo!!.width)
        assertEquals(1080, state.imageInfo!!.height)
        assertNotNull(state.previewBitmap)
    }

    @Test
    fun initialize_failure_setsError() {
        val decoder = FakeRegionDecoder(shouldFailInit = true)
        val state = TesseraState(dummySource, { decoder }, maxCacheSize = 150)

        state.initialize()

        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertTrue(state.error!!.contains("Fake init failure"))
    }

    // --- Viewport ---

    @Test
    fun updateViewport_updatesState() {
        val (state, _) = createInitializedState()
        val newViewport = Viewport(
            offsetX = 100f,
            offsetY = 200f,
            scale = 2.0f,
            viewWidth = 1080f,
            viewHeight = 1920f
        )

        state.updateViewport(newViewport)

        assertEquals(newViewport, state.viewport)
    }

    @Test
    fun defaultViewport_hasDefaultValues() {
        val (state, _) = createInitializedState()
        assertEquals(Viewport(), state.viewport)
    }

    // --- getVisibleTiles ---

    @Test
    fun getVisibleTiles_beforeInitialize_returnsEmpty() {
        val state = TesseraState(dummySource, { FakeRegionDecoder() }, maxCacheSize = 150)
        assertTrue(state.getVisibleTiles().isEmpty())
    }

    @Test
    fun getVisibleTiles_afterInitialize_returnsTiles() {
        val (state, _) = createInitializedState(FakeRegionDecoder(width = 1024, height = 768))
        state.updateViewport(
            Viewport(offsetX = 0f, offsetY = 0f, scale = 1f, viewWidth = 1024f, viewHeight = 768f)
        )

        val tiles = state.getVisibleTiles()
        assertTrue(tiles.isNotEmpty())
    }

    // --- Tile Cache ---

    @Test
    fun getCachedTile_beforeLoad_returnsNull() {
        val (state, _) = createInitializedState()
        assertNull(state.getCachedTile(TileCoordinate(0, 0, 0)))
    }

    @Test
    fun loadTile_cachesAndRetrieves() {
        val (state, _) = createInitializedState()

        val coord = TileCoordinate(0, 0, 0)
        val bitmap = state.loadTile(coord)

        assertNotNull(bitmap)
        assertNotNull(state.getCachedTile(coord))
    }

    @Test
    fun loadTile_withCacheFalse_doesNotCache() {
        val (state, _) = createInitializedState()

        val coord = TileCoordinate(0, 0, 0)
        val bitmap = state.loadTile(coord, cache = false)

        assertNotNull(bitmap)
        assertNull(state.getCachedTile(coord))
    }

    @Test
    fun loadTile_duplicateLoad_returnsCached() {
        val (state, decoder) = createInitializedState()

        val coord = TileCoordinate(0, 0, 0)
        state.loadTile(coord)
        val initialCount = decoder.decodeTileCount

        state.loadTile(coord)
        assertEquals(initialCount, decoder.decodeTileCount)
    }

    @Test
    fun loadTile_decodeTileReturnsNull_doesNotCache() {
        val (state, _) = createInitializedState(FakeRegionDecoder(shouldReturnNullTile = true))

        val coord = TileCoordinate(0, 0, 0)
        val result = state.loadTile(coord)

        assertNull(result)
        assertNull(state.getCachedTile(coord))
    }

    @Test
    fun getCachedTileByKey_returnsCoordinatePair() {
        val (state, _) = createInitializedState()

        val coord = TileCoordinate(1, 2, 0)
        state.loadTile(coord)

        val result = state.getCachedTileByKey(coord.toKey())
        assertNotNull(result)
        assertEquals(coord, result.second)
    }

    // --- LRU Eviction ---

    @Test
    fun lruEviction_evictsOldestWhenFull() {
        val (state, _) = createInitializedState(maxCacheSize = 3)

        val coords = (0 until 3).map { TileCoordinate(it, 0, 0) }
        coords.forEach { state.loadTile(it) }

        coords.forEach { assertNotNull(state.getCachedTile(it)) }

        val newCoord = TileCoordinate(3, 0, 0)
        state.loadTile(newCoord)

        assertNull(state.getCachedTile(coords[0]), "Oldest tile should be evicted")
        assertNotNull(state.getCachedTile(newCoord), "New tile should be cached")
    }

    @Test
    fun lruEviction_accessRefreshesOrder() {
        val (state, _) = createInitializedState(maxCacheSize = 3)

        val coords = (0 until 3).map { TileCoordinate(it, 0, 0) }
        coords.forEach { state.loadTile(it) }

        // Access coords[0] to refresh its position in LRU → order: [1, 2, 0]
        state.getCachedTile(coords[0])

        // Load new tile → should evict coords[1] (oldest in access order)
        val newCoord = TileCoordinate(3, 0, 0)
        state.loadTile(newCoord)

        assertNotNull(state.getCachedTile(coords[0]), "Recently accessed tile should survive")
        assertNull(state.getCachedTile(coords[1]), "Least recently used tile should be evicted")
    }

    // --- Dispose ---

    @Test
    fun dispose_clearsState() {
        val (state, decoder) = createInitializedState()

        state.loadTile(TileCoordinate(0, 0, 0))
        state.dispose()

        assertTrue(decoder.closeCalled)
        assertNull(state.getCachedTile(TileCoordinate(0, 0, 0)), "Cache should be cleared after dispose")
    }

    @Test
    fun loadTile_afterDispose_returnsNull() {
        val (state, _) = createInitializedState()
        state.dispose()

        assertNull(state.loadTile(TileCoordinate(0, 0, 0)), "loadTile should return null after dispose")
    }

    // --- getTileRect ---

    // --- initializeDecoder / applyInitResult ---

    @Test
    fun initializeDecoder_success_returnsSuccessResult() {
        val decoder = FakeRegionDecoder(width = 1920, height = 1080)
        val state = TesseraState(dummySource, { decoder }, maxCacheSize = 150)

        val result = state.initializeDecoder()

        assertTrue(result is TesseraState.InitResult.Success)
        assertEquals(1920, (result as TesseraState.InitResult.Success).info.width)
        assertEquals(1080, result.info.height)
        assertNotNull(result.preview)
    }

    @Test
    fun initializeDecoder_failure_returnsErrorResult() {
        val decoder = FakeRegionDecoder(shouldFailInit = true)
        val state = TesseraState(dummySource, { decoder }, maxCacheSize = 150)

        val result = state.initializeDecoder()

        assertTrue(result is TesseraState.InitResult.Error)
        assertTrue((result as TesseraState.InitResult.Error).message.contains("Fake init failure"))
    }

    @Test
    fun applyInitResult_success_updatesComposeState() {
        val decoder = FakeRegionDecoder(width = 2560, height = 1440)
        val state = TesseraState(dummySource, { decoder }, maxCacheSize = 150)

        assertTrue(state.isLoading)

        val result = state.initializeDecoder()
        state.applyInitResult(result)

        assertFalse(state.isLoading)
        assertNull(state.error)
        assertNotNull(state.imageInfo)
        assertEquals(2560, state.imageInfo!!.width)
        assertNotNull(state.previewBitmap)
    }

    @Test
    fun applyInitResult_error_setsErrorAndClearsDecoder() {
        val decoder = FakeRegionDecoder(shouldFailInit = true)
        val state = TesseraState(dummySource, { decoder }, maxCacheSize = 150)

        val result = state.initializeDecoder()
        state.applyInitResult(result)

        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertNull(state.imageInfo)
        // decodeTile should return null since decoder was cleared
        assertNull(state.decodeTile(TileCoordinate(0, 0, 0)))
    }

    // --- decodeTile / cacheTile ---

    @Test
    fun decodeTile_returnsDecodedBitmap() {
        val (state, _) = createInitializedState()

        val bitmap = state.decodeTile(TileCoordinate(0, 0, 0))

        assertNotNull(bitmap)
        // Should NOT be cached (decodeTile does not cache)
        assertNull(state.getCachedTile(TileCoordinate(0, 0, 0)))
    }

    @Test
    fun decodeTile_afterDispose_returnsNull() {
        val (state, _) = createInitializedState()
        state.dispose()

        assertNull(state.decodeTile(TileCoordinate(0, 0, 0)))
    }

    @Test
    fun decodeTile_beforeInitialize_returnsNull() {
        val state = TesseraState(dummySource, { FakeRegionDecoder() }, maxCacheSize = 150)

        assertNull(state.decodeTile(TileCoordinate(0, 0, 0)))
    }

    @Test
    fun cacheTile_storesInCache() {
        val (state, _) = createInitializedState()
        val coord = TileCoordinate(0, 0, 0)

        val bitmap = state.decodeTile(coord)
        assertNotNull(bitmap)

        state.cacheTile(coord, bitmap)

        assertNotNull(state.getCachedTile(coord))
    }

    @Test
    fun cacheTile_respectsMaxCacheSize() {
        val (state, _) = createInitializedState(maxCacheSize = 2)

        val coords = (0 until 3).map { TileCoordinate(it, 0, 0) }
        coords.forEach { coord ->
            val bitmap = state.decodeTile(coord)!!
            state.cacheTile(coord, bitmap)
        }

        // First tile should be evicted
        assertNull(state.getCachedTile(coords[0]))
        assertNotNull(state.getCachedTile(coords[2]))
    }

    @Test
    fun cacheTile_thenLoadTile_returnsCachedWithoutDecode() {
        val (state, decoder) = createInitializedState()
        val coord = TileCoordinate(0, 0, 0)

        val bitmap = state.decodeTile(coord)!!
        state.cacheTile(coord, bitmap)
        val countAfterCache = decoder.decodeTileCount

        val result = state.loadTile(coord)
        assertNotNull(result)
        assertEquals(countAfterCache, decoder.decodeTileCount, "loadTile should use cache, not decode again")
    }

    @Test
    fun loadTile_cacheHit_updatesAccessOrder() {
        val (state, _) = createInitializedState(maxCacheSize = 2)

        val coord0 = TileCoordinate(0, 0, 0)
        val coord1 = TileCoordinate(1, 0, 0)
        state.loadTile(coord0)
        state.loadTile(coord1)

        // Access coord0 via loadTile cache hit to refresh LRU
        state.loadTile(coord0)

        // Add new tile — should evict coord1 (least recent), not coord0
        val coord2 = TileCoordinate(2, 0, 0)
        state.loadTile(coord2)

        assertNotNull(state.getCachedTile(coord0), "Recently accessed tile should survive")
        assertNull(state.getCachedTile(coord1), "Least recently used tile should be evicted")
    }

    // --- getTileRect ---

    @Test
    fun getTileRect_beforeInitialize_returnsZeroRect() {
        val state = TesseraState(dummySource, { FakeRegionDecoder() }, maxCacheSize = 150)
        assertEquals(TileRect(0, 0, 0, 0), state.getTileRect(TileCoordinate(0, 0, 0)))
    }

    @Test
    fun getTileRect_afterInitialize_returnsValidRect() {
        val (state, _) = createInitializedState(FakeRegionDecoder(width = 1024, height = 768))

        val rect = state.getTileRect(TileCoordinate(0, 0, 0))
        assertTrue(rect.right > 0)
        assertTrue(rect.bottom > 0)
    }
}
