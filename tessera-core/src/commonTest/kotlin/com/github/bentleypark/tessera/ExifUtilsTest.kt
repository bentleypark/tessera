package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ExifUtilsTest {

    // Raw image: 1800x1200 (landscape)
    private val rawWidth = 1800
    private val rawHeight = 1200

    /** Every remapped rect must be well-formed and stay within the raw image bounds. */
    private fun assertWithinRawBounds(r: TileRect, w: Int = rawWidth, h: Int = rawHeight) {
        assertTrue(r.left in 0..w && r.right in 0..w, "x out of raw bounds: $r (w=$w)")
        assertTrue(r.top in 0..h && r.bottom in 0..h, "y out of raw bounds: $r (h=$h)")
        assertTrue(r.left <= r.right && r.top <= r.bottom, "inverted rect: $r")
    }

    // --- Orientation 0 (normal) ---

    @Test
    fun remapRect_orientation0_noChange() {
        val rect = TileRect(left = 100, top = 200, right = 612, bottom = 712)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 0)
        assertEquals(rect, result)
    }

    // --- Orientation 90° CW ---
    // Display: 1200x1800 (portrait). Display (l,t,r,b) -> Raw (t, H-r, b, H-l)

    @Test
    fun remapRect_orientation90_topLeftTile() {
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90)
        assertEquals(TileRect(left = 0, top = 944, right = 256, bottom = 1200), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_orientation90_centerTile() {
        val rect = TileRect(left = 400, top = 600, right = 656, bottom = 856)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90)
        assertEquals(TileRect(left = 600, top = 544, right = 856, bottom = 800), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_orientation90_fullImage() {
        // Display is 1200x1800 (portrait after 90° rotation)
        val rect = TileRect(left = 0, top = 0, right = 1200, bottom = 1800)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90)
        // Full display maps to the full raw image: (t=0, H-r=0, b=1800, H-l=1200)
        assertEquals(TileRect(left = 0, top = 0, right = 1800, bottom = 1200), result)
        assertWithinRawBounds(result)
    }

    // --- Orientation 180° ---
    // Display: 1800x1200 (same aspect). Display (l,t,r,b) -> Raw (W-r, H-b, W-l, H-t)

    @Test
    fun remapRect_orientation180_topLeftTile() {
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 180)
        assertEquals(TileRect(left = 1544, top = 944, right = 1800, bottom = 1200), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_orientation180_fullImage() {
        val rect = TileRect(left = 0, top = 0, right = 1800, bottom = 1200)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 180)
        assertEquals(TileRect(left = 0, top = 0, right = 1800, bottom = 1200), result)
        assertWithinRawBounds(result)
    }

    // --- Orientation 270° CW ---
    // Display: 1200x1800 (portrait). Display (l,t,r,b) -> Raw (W-b, l, W-t, r)

    @Test
    fun remapRect_orientation270_topLeftTile() {
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 270)
        assertEquals(TileRect(left = 1544, top = 0, right = 1800, bottom = 256), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_orientation270_centerTile() {
        val rect = TileRect(left = 400, top = 600, right = 656, bottom = 856)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 270)
        assertEquals(TileRect(left = 944, top = 400, right = 1200, bottom = 656), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_orientation270_fullImage() {
        val rect = TileRect(left = 0, top = 0, right = 1200, bottom = 1800)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 270)
        assertEquals(TileRect(left = 0, top = 0, right = 1800, bottom = 1200), result)
        assertWithinRawBounds(result)
    }

    // --- Symmetry: remap + inverse remap = identity ---

    @Test
    fun remapRect_90then270_returnsOriginal() {
        val original = TileRect(left = 100, top = 200, right = 356, bottom = 456)
        val remapped90 = remapRectForOrientation(original, rawWidth, rawHeight, 90)
        // The inverse of a 90° remap is a 270° remap with swapped raw dimensions.
        val restored = remapRectForOrientation(remapped90, rawHeight, rawWidth, 270)
        assertEquals(original, restored)
    }

    @Test
    fun remapRect_180twice_returnsOriginal() {
        val original = TileRect(left = 100, top = 200, right = 356, bottom = 456)
        val remapped = remapRectForOrientation(original, rawWidth, rawHeight, 180)
        val restored = remapRectForOrientation(remapped, rawWidth, rawHeight, 180)
        assertEquals(original, restored)
    }

    // --- Mirror ---

    @Test
    fun remapRect_mirrorOnly_flipsHorizontally() {
        val rect = TileRect(left = 100, top = 200, right = 356, bottom = 456)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 0, isMirrored = true)
        assertEquals(TileRect(left = 1444, top = 200, right = 1700, bottom = 456), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_180withMirror_orientation4() {
        // Orientation 4 = vertical flip. Independently: x unchanged, y flipped about rawHeight.
        val rect = TileRect(left = 100, top = 200, right = 356, bottom = 456)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 180, isMirrored = true)
        assertEquals(TileRect(left = 100, top = 744, right = 356, bottom = 1000), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_90withMirror_orientation5() {
        // Orientation 5 (transpose). Absolute literal so a regression in the mirror
        // axis (must span rawWidth, not rawHeight) fails instead of silently passing.
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90, isMirrored = true)
        assertEquals(TileRect(left = 1544, top = 944, right = 1800, bottom = 1200), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_270withMirror_orientation7() {
        // Orientation 7 (transverse). Absolute literal, same rationale as orientation 5.
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 270, isMirrored = true)
        assertEquals(TileRect(left = 0, top = 0, right = 256, bottom = 256), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_mirrorTwice_returnsOriginal() {
        val original = TileRect(left = 100, top = 200, right = 356, bottom = 456)
        val mirrored = remapRectForOrientation(original, rawWidth, rawHeight, 0, isMirrored = true)
        val restored = remapRectForOrientation(mirrored, rawWidth, rawHeight, 0, isMirrored = true)
        assertEquals(original, restored)
    }

    // --- Square image (rawWidth == rawHeight) ---
    // The rawWidth/rawHeight swap is invisible here (|W-H| == 0), so these cases
    // guard the formula in the region where the old buggy code also passed.

    @Test
    fun remapRect_squareImage_orientation90_inBounds() {
        val size = 1000
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, size, size, 90)
        assertEquals(TileRect(left = 0, top = 744, right = 256, bottom = 1000), result)
        assertWithinRawBounds(result, size, size)
    }

    @Test
    fun remapRect_squareImage_90then270_sameDimsReturnsOriginal() {
        // For a square image the display dimensions do not swap, so the inverse of
        // a 90° remap is a 270° remap with the SAME dimensions.
        val size = 1000
        val original = TileRect(left = 100, top = 200, right = 356, bottom = 456)
        val remapped90 = remapRectForOrientation(original, size, size, 90)
        val restored = remapRectForOrientation(remapped90, size, size, 270)
        assertEquals(original, restored)
    }

    // --- Edge cases ---

    @Test
    fun remapRect_zeroSizeRect_orientation90() {
        val rect = TileRect(left = 500, top = 500, right = 500, bottom = 500)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90)
        assertEquals(TileRect(left = 500, top = 700, right = 500, bottom = 700), result)
        assertWithinRawBounds(result)
    }

    @Test
    fun remapRect_unknownDegrees_noChange() {
        val rect = TileRect(left = 100, top = 200, right = 612, bottom = 712)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 45)
        assertEquals(rect, result)
    }
}
