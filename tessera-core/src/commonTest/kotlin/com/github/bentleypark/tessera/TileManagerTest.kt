package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileManagerTest {

    private val imageInfo = ImageInfo(width = 1920, height = 1080)
    private val manager = TileManager(imageInfo, tileSize = 256)

    // --- calculateZoomLevel ---

    @Test
    fun calculateZoomLevel_lowScale_returnsLevel0() {
        assertEquals(0, manager.calculateZoomLevel(0.5f))
        assertEquals(0, manager.calculateZoomLevel(1.0f))
        assertEquals(0, manager.calculateZoomLevel(1.49f))
    }

    @Test
    fun calculateZoomLevel_mediumScale_returnsLevel1() {
        assertEquals(1, manager.calculateZoomLevel(1.5f))
        assertEquals(1, manager.calculateZoomLevel(2.0f))
        assertEquals(1, manager.calculateZoomLevel(2.99f))
    }

    @Test
    fun calculateZoomLevel_highScale_returnsLevel2() {
        assertEquals(2, manager.calculateZoomLevel(3.0f))
        assertEquals(2, manager.calculateZoomLevel(4.0f))
        assertEquals(2, manager.calculateZoomLevel(5.99f))
    }

    @Test
    fun calculateZoomLevel_veryHighScale_returnsLevel3() {
        assertEquals(3, manager.calculateZoomLevel(6.0f))
        assertEquals(3, manager.calculateZoomLevel(10.0f))
        assertEquals(3, manager.calculateZoomLevel(100.0f))
    }

    // --- calculateSampleSize ---

    @Test
    fun calculateSampleSize_level0_returns2() {
        assertEquals(2, manager.calculateSampleSize(0))
    }

    @Test
    fun calculateSampleSize_otherLevels_returns1() {
        assertEquals(1, manager.calculateSampleSize(1))
        assertEquals(1, manager.calculateSampleSize(2))
        assertEquals(1, manager.calculateSampleSize(3))
    }

    // --- createTileGrid ---

    @Test
    fun createTileGrid_level0_halfResolution() {
        val grid = manager.createTileGrid(0)
        // sampleSize=2 → scaledWidth=960, scaledHeight=540
        // columns = ceil(960/256) = 4, rows = ceil(540/256) = 3
        assertEquals(4, grid.columns)
        assertEquals(3, grid.rows)
        assertEquals(0, grid.zoomLevel)
        assertEquals(256, grid.tileSize)
    }

    @Test
    fun createTileGrid_level1_fullResolution() {
        val grid = manager.createTileGrid(1)
        // sampleSize=1 → scaledWidth=1920, scaledHeight=1080
        // columns = ceil(1920/256) = 8, rows = ceil(1080/256) = 5
        assertEquals(8, grid.columns)
        assertEquals(5, grid.rows)
        assertEquals(1, grid.zoomLevel)
    }

    @Test
    fun createTileGrid_exactMultiple() {
        val exactManager = TileManager(ImageInfo(width = 512, height = 256), tileSize = 256)
        val grid = exactManager.createTileGrid(1)
        assertEquals(2, grid.columns)
        assertEquals(1, grid.rows)
    }

    @Test
    fun createTileGrid_smallImage() {
        val smallManager = TileManager(ImageInfo(width = 100, height = 50), tileSize = 256)
        val grid = smallManager.createTileGrid(1)
        assertEquals(1, grid.columns)
        assertEquals(1, grid.rows)
    }

    // --- getVisibleTiles ---

    @Test
    fun getVisibleTiles_fullViewport_returnsAllTiles() {
        val viewport = Viewport(
            offsetX = 0f,
            offsetY = 0f,
            scale = 1.0f,
            viewWidth = 1920f,
            viewHeight = 1080f
        )
        val tiles = manager.getVisibleTiles(viewport)
        val grid = manager.createTileGrid(0)
        assertEquals(grid.totalTiles, tiles.size)
    }

    @Test
    fun getVisibleTiles_partialViewport_returnsSubset() {
        val viewport = Viewport(
            offsetX = 0f,
            offsetY = 0f,
            scale = 1.0f,
            viewWidth = 300f,
            viewHeight = 300f
        )
        val tiles = manager.getVisibleTiles(viewport)
        assertTrue(tiles.isNotEmpty())
        assertTrue(tiles.size < manager.createTileGrid(0).totalTiles)
    }

    @Test
    fun getVisibleTiles_zoomedIn_usesHigherZoomLevel() {
        val viewport = Viewport(
            offsetX = 0f,
            offsetY = 0f,
            scale = 2.0f,
            viewWidth = 500f,
            viewHeight = 500f
        )
        val tiles = manager.getVisibleTiles(viewport)
        assertTrue(tiles.all { it.zoomLevel == 1 })
    }

    @Test
    fun getVisibleTiles_offsetViewport_returnsCorrectTiles() {
        val viewport = Viewport(
            offsetX = 512f,
            offsetY = 512f,
            scale = 1.0f,
            viewWidth = 256f,
            viewHeight = 256f
        )
        val tiles = manager.getVisibleTiles(viewport, prefetchMargin = 0)
        assertTrue(tiles.isNotEmpty())
        // All tiles should be in the offset region (no prefetch margin)
        tiles.forEach { tile ->
            assertTrue(tile.col >= 2, "col ${tile.col} should be >= 2")
            assertTrue(tile.row >= 2, "row ${tile.row} should be >= 2")
        }
    }

    @Test
    fun getVisibleTiles_negativeOffset_clampsToZero() {
        val viewport = Viewport(
            offsetX = -100f,
            offsetY = -200f,
            scale = 1.0f,
            viewWidth = 500f,
            viewHeight = 500f
        )
        val tiles = manager.getVisibleTiles(viewport)
        assertTrue(tiles.isNotEmpty())
        tiles.forEach { tile ->
            assertTrue(tile.col >= 0, "col should be >= 0")
            assertTrue(tile.row >= 0, "row should be >= 0")
        }
    }

    @Test
    fun getVisibleTiles_zeroViewport_returnsAtLeastOneTile() {
        val viewport = Viewport(
            offsetX = 0f,
            offsetY = 0f,
            scale = 1.0f,
            viewWidth = 0f,
            viewHeight = 0f
        )
        val tiles = manager.getVisibleTiles(viewport)
        // With zero viewport, still includes tile at (0,0) since ceil(0/256)=0 and floor(0/256)=0
        assertTrue(tiles.isNotEmpty())
    }

    @Test
    fun getVisibleTiles_allTilesHaveSameZoomLevel() {
        val viewport = Viewport(
            offsetX = 0f,
            offsetY = 0f,
            scale = 3.5f,
            viewWidth = 1000f,
            viewHeight = 1000f
        )
        val tiles = manager.getVisibleTiles(viewport)
        val expectedZoom = manager.calculateZoomLevel(3.5f)
        assertTrue(tiles.all { it.zoomLevel == expectedZoom })
    }

    // --- getTileRect ---

    @Test
    fun getTileRect_firstTile_level0() {
        val coord = TileCoordinate(col = 0, row = 0, zoomLevel = 0)
        val rect = manager.getTileRect(coord)
        // sampleSize=2 → left=0, top=0, right=min(512,1920)=512, bottom=min(512,1080)=512
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(512, rect.right)
        assertEquals(512, rect.bottom)
    }

    @Test
    fun getTileRect_firstTile_level1() {
        val coord = TileCoordinate(col = 0, row = 0, zoomLevel = 1)
        val rect = manager.getTileRect(coord)
        // sampleSize=1 → left=0, top=0, right=min(256,1920)=256, bottom=min(256,1080)=256
        assertEquals(0, rect.left)
        assertEquals(0, rect.top)
        assertEquals(256, rect.right)
        assertEquals(256, rect.bottom)
    }

    @Test
    fun getTileRect_lastTile_clampedToImageBounds() {
        val grid = manager.createTileGrid(1)
        val lastCol = grid.columns - 1
        val lastRow = grid.rows - 1
        val coord = TileCoordinate(col = lastCol, row = lastRow, zoomLevel = 1)
        val rect = manager.getTileRect(coord)
        // right and bottom should not exceed image dimensions
        assertTrue(rect.right <= imageInfo.width, "right ${rect.right} <= ${imageInfo.width}")
        assertTrue(rect.bottom <= imageInfo.height, "bottom ${rect.bottom} <= ${imageInfo.height}")
    }

    @Test
    fun getTileRect_middleTile() {
        val coord = TileCoordinate(col = 2, row = 1, zoomLevel = 1)
        val rect = manager.getTileRect(coord)
        // sampleSize=1 → left=512, top=256, right=768, bottom=512
        assertEquals(512, rect.left)
        assertEquals(256, rect.top)
        assertEquals(768, rect.right)
        assertEquals(512, rect.bottom)
    }

    // --- Custom tileSize ---

    @Test
    fun customTileSize_affectsGrid() {
        val largeManager = TileManager(imageInfo, tileSize = 512)
        val grid = largeManager.createTileGrid(1)
        // columns = ceil(1920/512) = 4, rows = ceil(1080/512) = 3
        assertEquals(4, grid.columns)
        assertEquals(3, grid.rows)
    }

    // --- Large image tile count reduction with bigger tile size ---

    @Test
    fun largerTileSize_reducesTileCount_108MP() {
        val largeImage = ImageInfo(width = 12000, height = 7149)
        val small = TileManager(largeImage, tileSize = 256)
        val large = TileManager(largeImage, tileSize = 512)

        val gridSmall = small.createTileGrid(0) // sampleSize=2 → 6000x3575
        val gridLarge = large.createTileGrid(0) // sampleSize=2 → 6000x3575

        // 256px: ceil(6000/256)=24, ceil(3575/256)=14 → 336
        assertEquals(24, gridSmall.columns)
        assertEquals(14, gridSmall.rows)
        assertEquals(336, gridSmall.totalTiles)

        // 512px: ceil(6000/512)=12, ceil(3575/512)=7 → 84
        assertEquals(12, gridLarge.columns)
        assertEquals(7, gridLarge.rows)
        assertEquals(84, gridLarge.totalTiles)

        // 75% reduction
        assertTrue(gridLarge.totalTiles < gridSmall.totalTiles / 3)
    }

    @Test
    fun largerTileSize_fewerVisibleTiles() {
        val largeImage = ImageInfo(width = 4000, height = 3000)
        val small = TileManager(largeImage, tileSize = 256)
        val large = TileManager(largeImage, tileSize = 512)

        // Zoomed-in viewport covering ~1000x1000 image pixels
        val viewport = Viewport(
            offsetX = 1000f,
            offsetY = 1000f,
            scale = 2.0f,
            viewWidth = 1000f,
            viewHeight = 1000f
        )

        val tilesSmall = small.getVisibleTiles(viewport)
        val tilesLarge = large.getVisibleTiles(viewport)

        assertTrue(tilesLarge.size < tilesSmall.size,
            "512px tiles (${tilesLarge.size}) should be fewer than 256px tiles (${tilesSmall.size})")
    }

    // --- Prefetch margin ---

    @Test
    fun prefetchMargin_expandsVisibleTiles() {
        val viewport = Viewport(
            offsetX = 512f,
            offsetY = 512f,
            scale = 2.0f,
            viewWidth = 500f,
            viewHeight = 500f
        )
        val withoutMargin = manager.getVisibleTiles(viewport, prefetchMargin = 0)
        val withMargin = manager.getVisibleTiles(viewport, prefetchMargin = 128)

        assertTrue(withMargin.size > withoutMargin.size,
            "Prefetch margin should include more tiles: ${withMargin.size} > ${withoutMargin.size}")
        // All non-margin tiles should be included in margin result
        withoutMargin.forEach { tile ->
            assertTrue(tile in withMargin, "Visible tile $tile should be in prefetched set")
        }
    }

    @Test
    fun prefetchMargin_zero_matchesExactViewport() {
        val viewport = Viewport(
            offsetX = 256f,
            offsetY = 256f,
            scale = 2.0f,
            viewWidth = 256f,
            viewHeight = 256f
        )
        val noMargin = manager.getVisibleTiles(viewport, prefetchMargin = 0)
        val defaultMargin = manager.getVisibleTiles(viewport)

        assertTrue(defaultMargin.size >= noMargin.size,
            "Default margin should include at least as many tiles")
    }

    @Test
    fun prefetchMargin_clampedToImageBounds() {
        // Viewport at top-left corner — margin can't go below 0
        val viewport = Viewport(
            offsetX = 0f,
            offsetY = 0f,
            scale = 2.0f,
            viewWidth = 300f,
            viewHeight = 300f
        )
        val tiles = manager.getVisibleTiles(viewport, prefetchMargin = 500)
        tiles.forEach { tile ->
            assertTrue(tile.col >= 0, "col should be >= 0")
            assertTrue(tile.row >= 0, "row should be >= 0")
        }
    }

    @Test
    fun tileSize_384_intermediateValue() {
        val mgr = TileManager(imageInfo, tileSize = 384)
        val grid = mgr.createTileGrid(1)
        // columns = ceil(1920/384) = 5, rows = ceil(1080/384) = 3
        assertEquals(5, grid.columns)
        assertEquals(3, grid.rows)
    }
}
