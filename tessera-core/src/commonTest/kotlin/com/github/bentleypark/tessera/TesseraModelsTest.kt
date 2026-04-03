package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals

class TileCoordinateTest {

    @Test
    fun toKey_formatsCorrectly() {
        val coordinate = TileCoordinate(col = 3, row = 5, zoomLevel = 2)
        assertEquals("2-3-5", coordinate.toKey())
    }

    @Test
    fun toKey_zeroValues() {
        val coordinate = TileCoordinate(col = 0, row = 0, zoomLevel = 0)
        assertEquals("0-0-0", coordinate.toKey())
    }

    @Test
    fun equality_sameValues() {
        val a = TileCoordinate(1, 2, 3)
        val b = TileCoordinate(1, 2, 3)
        assertEquals(a, b)
        assertEquals(a.hashCode(), b.hashCode())
    }

    @Test
    fun copy_overridesSpecifiedFields() {
        val original = TileCoordinate(col = 1, row = 2, zoomLevel = 0)
        val copied = original.copy(zoomLevel = 3)
        assertEquals(TileCoordinate(col = 1, row = 2, zoomLevel = 3), copied)
    }
}

class TileRectTest {

    @Test
    fun creation_preservesValues() {
        val rect = TileRect(left = 10, top = 20, right = 266, bottom = 276)
        assertEquals(10, rect.left)
        assertEquals(20, rect.top)
        assertEquals(266, rect.right)
        assertEquals(276, rect.bottom)
    }

    @Test
    fun equality_sameValues() {
        val a = TileRect(0, 0, 256, 256)
        val b = TileRect(0, 0, 256, 256)
        assertEquals(a, b)
    }
}

class ImageInfoTest {

    @Test
    fun defaultMimeType_isJpeg() {
        val info = ImageInfo(width = 1920, height = 1080)
        assertEquals("image/jpeg", info.mimeType)
    }

    @Test
    fun customMimeType_isPreserved() {
        val info = ImageInfo(width = 800, height = 600, mimeType = "image/png")
        assertEquals("image/png", info.mimeType)
    }
}

class TileGridTest {

    @Test
    fun totalTiles_calculatesCorrectly() {
        val grid = TileGrid(tileSize = 256, columns = 4, rows = 3, zoomLevel = 0)
        assertEquals(12, grid.totalTiles)
    }

    @Test
    fun totalTiles_singleTile() {
        val grid = TileGrid(columns = 1, rows = 1, zoomLevel = 0)
        assertEquals(1, grid.totalTiles)
    }

    @Test
    fun totalTiles_zeroColumns() {
        val grid = TileGrid(columns = 0, rows = 5, zoomLevel = 0)
        assertEquals(0, grid.totalTiles)
    }

    @Test
    fun defaultTileSize_is256() {
        val grid = TileGrid(columns = 2, rows = 2, zoomLevel = 0)
        assertEquals(256, grid.tileSize)
    }
}

class ViewportTest {

    @Test
    fun defaultValues() {
        val viewport = Viewport()
        assertEquals(0f, viewport.offsetX)
        assertEquals(0f, viewport.offsetY)
        assertEquals(1f, viewport.scale)
        assertEquals(1f, viewport.totalScale)
        assertEquals(0f, viewport.viewWidth)
        assertEquals(0f, viewport.viewHeight)
    }

    @Test
    fun customValues_preserved() {
        val viewport = Viewport(
            offsetX = 100f,
            offsetY = 200f,
            scale = 2.5f,
            totalScale = 5f,
            viewWidth = 1080f,
            viewHeight = 1920f
        )
        assertEquals(100f, viewport.offsetX)
        assertEquals(200f, viewport.offsetY)
        assertEquals(2.5f, viewport.scale)
        assertEquals(5f, viewport.totalScale)
        assertEquals(1080f, viewport.viewWidth)
        assertEquals(1920f, viewport.viewHeight)
    }
}

class TileLoadInfoTest {

    @Test
    fun creation_preservesValues() {
        val info = TileLoadInfo(loadTime = 150L, zoomLevel = 2)
        assertEquals(150L, info.loadTime)
        assertEquals(2, info.zoomLevel)
    }
}

class ImageFormatTest {

    @Test
    fun fromMimeType_jpeg() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromMimeType("image/jpeg"))
    }

    @Test
    fun fromMimeType_jpg() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromMimeType("image/jpg"))
    }

    @Test
    fun fromMimeType_png() {
        assertEquals(ImageFormat.PNG, ImageFormat.fromMimeType("image/png"))
    }

    @Test
    fun fromMimeType_webp() {
        assertEquals(ImageFormat.WEBP, ImageFormat.fromMimeType("image/webp"))
    }

    @Test
    fun fromMimeType_gif() {
        assertEquals(ImageFormat.GIF, ImageFormat.fromMimeType("image/gif"))
    }

    @Test
    fun fromMimeType_unknown() {
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromMimeType("image/bmp"))
    }

    @Test
    fun fromMimeType_caseInsensitive() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromMimeType("image/JPEG"))
        assertEquals(ImageFormat.PNG, ImageFormat.fromMimeType("Image/PNG"))
        assertEquals(ImageFormat.WEBP, ImageFormat.fromMimeType("IMAGE/WEBP"))
    }

    @Test
    fun fromMimeType_emptyString() {
        assertEquals(ImageFormat.UNKNOWN, ImageFormat.fromMimeType(""))
    }

    @Test
    fun fromMimeType_partialMatch() {
        assertEquals(ImageFormat.JPEG, ImageFormat.fromMimeType("jpeg"))
        assertEquals(ImageFormat.PNG, ImageFormat.fromMimeType("png"))
    }
}
