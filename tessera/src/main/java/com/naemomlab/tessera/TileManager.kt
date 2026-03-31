package com.naemomlab.tessera

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Manages tile grid calculation, viewport-based tile selection, and zoom level determination.
 */
class TileManager(
    private val imageInfo: ImageInfo,
    private val tileSize: Int = 256
) {

    fun calculateZoomLevel(scale: Float): Int {
        return when {
            scale < 1.5f -> 0
            scale < 3.0f -> 1
            scale < 6.0f -> 2
            else -> 3
        }
    }

    fun calculateSampleSize(zoomLevel: Int): Int {
        return when (zoomLevel) {
            0 -> 2
            else -> 1
        }
    }

    fun createTileGrid(zoomLevel: Int): TileGrid {
        val sampleSize = calculateSampleSize(zoomLevel)
        val scaledWidth = imageInfo.width / sampleSize
        val scaledHeight = imageInfo.height / sampleSize

        val columns = ceil(scaledWidth.toFloat() / tileSize).toInt()
        val rows = ceil(scaledHeight.toFloat() / tileSize).toInt()

        return TileGrid(
            tileSize = tileSize,
            columns = columns,
            rows = rows,
            zoomLevel = zoomLevel
        )
    }

    fun getVisibleTiles(viewport: Viewport): List<TileCoordinate> {
        val zoomLevel = calculateZoomLevel(viewport.scale)
        val grid = createTileGrid(zoomLevel)

        val viewportLeft = max(0f, viewport.offsetX)
        val viewportTop = max(0f, viewport.offsetY)
        val viewportRight = min(imageInfo.width.toFloat(), viewport.offsetX + viewport.viewWidth)
        val viewportBottom = min(imageInfo.height.toFloat(), viewport.offsetY + viewport.viewHeight)

        val startCol = max(0, floor(viewportLeft / tileSize).toInt())
        val startRow = max(0, floor(viewportTop / tileSize).toInt())
        val endCol = min(grid.columns - 1, ceil(viewportRight / tileSize).toInt())
        val endRow = min(grid.rows - 1, ceil(viewportBottom / tileSize).toInt())

        val tiles = mutableListOf<TileCoordinate>()
        for (row in startRow..endRow) {
            for (col in startCol..endCol) {
                tiles.add(
                    TileCoordinate(
                        col = col,
                        row = row,
                        zoomLevel = zoomLevel
                    )
                )
            }
        }

        return tiles
    }

    fun getTileRect(coordinate: TileCoordinate): TileRect {
        val sampleSize = calculateSampleSize(coordinate.zoomLevel)

        val left = coordinate.col * tileSize * sampleSize
        val top = coordinate.row * tileSize * sampleSize
        val right = min(left + tileSize * sampleSize, imageInfo.width)
        val bottom = min(top + tileSize * sampleSize, imageInfo.height)

        return TileRect(left, top, right, bottom)
    }
}
