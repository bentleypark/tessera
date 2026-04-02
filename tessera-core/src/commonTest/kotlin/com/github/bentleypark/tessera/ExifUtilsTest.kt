package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals

class ExifUtilsTest {

    // Raw image: 1800x1200 (landscape)
    private val rawWidth = 1800
    private val rawHeight = 1200

    // --- Orientation 0 (normal) ---

    @Test
    fun remapRect_orientation0_noChange() {
        val rect = TileRect(left = 100, top = 200, right = 612, bottom = 712)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 0)
        assertEquals(rect, result)
    }

    // --- Orientation 90° CW ---
    // Display: 1200x1800 (portrait). Display (l,t,r,b) -> Raw (t, W-r, b, W-l)

    @Test
    fun remapRect_orientation90_topLeftTile() {
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90)
        assertEquals(TileRect(left = 0, top = 1544, right = 256, bottom = 1800), result)
    }

    @Test
    fun remapRect_orientation90_centerTile() {
        val rect = TileRect(left = 400, top = 600, right = 656, bottom = 856)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90)
        assertEquals(TileRect(left = 600, top = 1144, right = 856, bottom = 1400), result)
    }

    @Test
    fun remapRect_orientation90_fullImage() {
        // Display is 1200x1800 (portrait after 90° rotation)
        val rect = TileRect(left = 0, top = 0, right = 1200, bottom = 1800)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90)
        // Raw: (t=0, W-r=1800-1200=600, b=1800, W-l=1800-0=1800)
        assertEquals(TileRect(left = 0, top = 600, right = 1800, bottom = 1800), result)
    }

    // --- Orientation 180° ---
    // Display: 1800x1200 (same aspect). Display (l,t,r,b) -> Raw (W-r, H-b, W-l, H-t)

    @Test
    fun remapRect_orientation180_topLeftTile() {
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 180)
        assertEquals(TileRect(left = 1544, top = 944, right = 1800, bottom = 1200), result)
    }

    @Test
    fun remapRect_orientation180_fullImage() {
        val rect = TileRect(left = 0, top = 0, right = 1800, bottom = 1200)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 180)
        assertEquals(TileRect(left = 0, top = 0, right = 1800, bottom = 1200), result)
    }

    // --- Orientation 270° CW ---
    // Display: 1200x1800 (portrait). Display (l,t,r,b) -> Raw (H-b, l, H-t, r)

    @Test
    fun remapRect_orientation270_topLeftTile() {
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 270)
        assertEquals(TileRect(left = 944, top = 0, right = 1200, bottom = 256), result)
    }

    @Test
    fun remapRect_orientation270_centerTile() {
        val rect = TileRect(left = 400, top = 600, right = 656, bottom = 856)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 270)
        assertEquals(TileRect(left = 344, top = 400, right = 600, bottom = 656), result)
    }

    // --- Symmetry: remap + inverse remap = identity ---

    @Test
    fun remapRect_90then270_returnsOriginal() {
        val original = TileRect(left = 100, top = 200, right = 356, bottom = 456)
        val remapped90 = remapRectForOrientation(original, rawWidth, rawHeight, 90)
        // After 90° remap, dimensions swap: raw for 90° output uses rawWidth
        // Inverse of 90° is 270° with swapped raw dimensions
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
    }

    @Test
    fun remapRect_90withMirror_orientation5() {
        val rect = TileRect(left = 0, top = 0, right = 256, bottom = 256)
        val rotated = remapRectForOrientation(rect, rawWidth, rawHeight, 90, isMirrored = false)
        val mirrored = remapRectForOrientation(rect, rawWidth, rawHeight, 90, isMirrored = true)
        // Mirror flips horizontally in raw space (rawHeight dimension for 90°)
        assertEquals(rawHeight - rotated.right, mirrored.left)
        assertEquals(rawHeight - rotated.left, mirrored.right)
        assertEquals(rotated.top, mirrored.top)
        assertEquals(rotated.bottom, mirrored.bottom)
    }

    @Test
    fun remapRect_mirrorTwice_returnsOriginal() {
        val original = TileRect(left = 100, top = 200, right = 356, bottom = 456)
        val mirrored = remapRectForOrientation(original, rawWidth, rawHeight, 0, isMirrored = true)
        val restored = remapRectForOrientation(mirrored, rawWidth, rawHeight, 0, isMirrored = true)
        assertEquals(original, restored)
    }

    // --- Edge cases ---

    @Test
    fun remapRect_zeroSizeRect_orientation90() {
        val rect = TileRect(left = 500, top = 500, right = 500, bottom = 500)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 90)
        assertEquals(TileRect(left = 500, top = 1300, right = 500, bottom = 1300), result)
    }

    @Test
    fun remapRect_unknownDegrees_noChange() {
        val rect = TileRect(left = 100, top = 200, right = 612, bottom = 712)
        val result = remapRectForOrientation(rect, rawWidth, rawHeight, 45)
        assertEquals(rect, result)
    }
}
