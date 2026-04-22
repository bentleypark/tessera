package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 * Verifies the zoom-level-0 tile-skip decision used by TesseraImageContent.
 *
 * The rule: at level 0 tiles are redundant only when the viewport covers the
 * entire source image. FitWidth/FitHeight modes (partial viewport) must still
 * load tiles, or scrolled content falls back to the blurry preview bitmap.
 */
class ZeroLevelTileSkipTest {

    // --- Full cover (Fit mode) — should skip ---

    @Test
    fun fit_viewportCoversEntireImage_skips() {
        // Classic Fit: fitScale chosen so that viewport == image on both axes.
        val viewport = Viewport(viewWidth = 800f, viewHeight = 600f)
        val info = ImageInfo(width = 800, height = 600)
        assertTrue(shouldSkipZeroLevelTiles(viewport, info))
    }

    @Test
    fun fit_viewportMarginallyLarger_skips() {
        // Guards the `-1f` tolerance: floating-point drift by <1px still skips.
        val viewport = Viewport(viewWidth = 800.4f, viewHeight = 600.2f)
        val info = ImageInfo(width = 800, height = 600)
        assertTrue(shouldSkipZeroLevelTiles(viewport, info))
    }

    // --- FitWidth (tall image, vertical scroll) — should NOT skip ---

    @Test
    fun fitWidth_tallImageOverflowsVertically_loadsTiles() {
        // Webtoon: image 600x3000, fit to 400-wide viewport →
        // viewport in image coords = 600 x (800/fitScale) where fitScale=400/600.
        // viewportHeight ≈ 1200 < imageHeight 3000 → must load tiles.
        val viewport = Viewport(viewWidth = 600f, viewHeight = 1200f)
        val info = ImageInfo(width = 600, height = 3000)
        assertFalse(shouldSkipZeroLevelTiles(viewport, info))
    }

    // --- FitHeight (wide panorama, horizontal scroll) — should NOT skip ---

    @Test
    fun fitHeight_wideImageOverflowsHorizontally_loadsTiles() {
        // Panorama: image 3000x600, fit to 800-tall viewport →
        // viewportWidth < imageWidth → tiles required.
        val viewport = Viewport(viewWidth = 2000f, viewHeight = 600f)
        val info = ImageInfo(width = 3000, height = 600)
        assertFalse(shouldSkipZeroLevelTiles(viewport, info))
    }

    // --- Partial coverage on a single axis is enough to require tiles ---

    @Test
    fun partialHorizontalCoverage_loadsTiles() {
        val viewport = Viewport(viewWidth = 799f, viewHeight = 600f)
        val info = ImageInfo(width = 1200, height = 600)
        assertFalse(shouldSkipZeroLevelTiles(viewport, info))
    }

    @Test
    fun partialVerticalCoverage_loadsTiles() {
        val viewport = Viewport(viewWidth = 800f, viewHeight = 599f)
        val info = ImageInfo(width = 800, height = 1200)
        assertFalse(shouldSkipZeroLevelTiles(viewport, info))
    }

    // --- Null image info — skip defensively (nothing to decode) ---

    @Test
    fun nullImageInfo_skips() {
        assertTrue(shouldSkipZeroLevelTiles(Viewport(), imageInfo = null))
    }

    // --- Default viewport (before layout) with valid info — does not skip ---

    @Test
    fun zeroViewport_doesNotSkip() {
        // Before the first layout pass viewport is (0,0). We prefer "no skip"
        // so the next viewport update re-triggers evaluation instead of
        // latching the skip branch.
        val viewport = Viewport()
        val info = ImageInfo(width = 800, height = 600)
        assertFalse(shouldSkipZeroLevelTiles(viewport, info))
    }

    // --- Sanity: exact boundary within tolerance ---

    @Test
    fun exactBoundary_skips() {
        val viewport = Viewport(viewWidth = 1024f, viewHeight = 768f)
        val info = ImageInfo(width = 1024, height = 768)
        assertEquals(true, shouldSkipZeroLevelTiles(viewport, info))
    }
}
