package com.github.bentleypark.tessera

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.IntSize

/**
 * Tile coordinate information
 */
data class TileCoordinate(
    val col: Int,
    val row: Int,
    val zoomLevel: Int
) {
    fun toKey(): String = "$zoomLevel-$col-$row"
}

/**
 * Tile data for rendering
 */
data class Tile(
    val coordinate: TileCoordinate,
    val bitmap: ImageBitmap,
    val position: Offset,
    val size: IntSize
)

/**
 * Platform-agnostic rectangle for tile regions.
 * Replaces android.graphics.Rect for KMP compatibility.
 */
data class TileRect(
    val left: Int,
    val top: Int,
    val right: Int,
    val bottom: Int
)

/**
 * Image metadata
 */
data class ImageInfo(
    val width: Int,
    val height: Int,
    val mimeType: String = "image/jpeg"
)

/**
 * Tile grid configuration
 */
data class TileGrid(
    val tileSize: Int = 256,
    val columns: Int,
    val rows: Int,
    val zoomLevel: Int
) {
    val totalTiles: Int get() = columns * rows
}

/**
 * Content scaling strategy for initial image display.
 */
enum class ContentScale {
    /** Fit entire image in viewport (default behavior) */
    Fit,
    /** Fit to viewport width, allow vertical scrolling (for tall images like webtoons) */
    FitWidth,
    /** Fit to viewport height, allow horizontal scrolling (for wide images like panoramas) */
    FitHeight,
    /** Auto-detect based on aspect ratio: tall → FitWidth, wide → FitHeight, normal → Fit */
    Auto
}

/**
 * Viewport information (visible area on screen)
 */
data class Viewport(
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val scale: Float = 1f,
    val totalScale: Float = 1f,
    val viewWidth: Float = 0f,
    val viewHeight: Float = 0f
)

/**
 * Tile loading information
 */
data class TileLoadInfo(
    val loadTime: Long,
    val zoomLevel: Int
)

/**
 * Platform-agnostic interface for region-based image decoding.
 * Android implementation uses BitmapRegionDecoder; iOS would use CGImageSource.
 */
interface RegionDecoder : AutoCloseable {
    val imageInfo: ImageInfo
    fun initialize()
    fun decodeTile(rect: TileRect, sampleSize: Int = 1): ImageBitmap?
    fun decodePreview(maxSize: Int = 512): ImageBitmap?
}
