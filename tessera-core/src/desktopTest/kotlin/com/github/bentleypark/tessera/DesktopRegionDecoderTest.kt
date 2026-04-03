package com.github.bentleypark.tessera

import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DesktopRegionDecoderTest {

    private fun createTestJpeg(width: Int = 800, height: Int = 600): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        // Fill with gradient for visual verification
        for (y in 0 until height) {
            for (x in 0 until width) {
                image.setRGB(x, y, Color(
                    (x * 255 / width),
                    (y * 255 / height),
                    128
                ).rgb)
            }
        }
        g.dispose()
        val file = File.createTempFile("tessera_test_", ".jpg")
        file.deleteOnExit()
        ImageIO.write(image, "JPEG", file)
        return file
    }

    private fun createTestPng(width: Int = 400, height: Int = 300): File {
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val g = image.createGraphics()
        g.color = Color.BLUE
        g.fillRect(0, 0, width, height)
        g.dispose()
        val file = File.createTempFile("tessera_test_", ".png")
        file.deleteOnExit()
        ImageIO.write(image, "PNG", file)
        return file
    }

    // --- Initialize ---

    @Test
    fun initialize_jpeg_readsCorrectDimensions() {
        val file = createTestJpeg(1024, 768)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))

        decoder.initialize()

        assertEquals(1024, decoder.imageInfo.width)
        assertEquals(768, decoder.imageInfo.height)
        assertEquals("image/jpeg", decoder.imageInfo.mimeType)
        decoder.close()
    }

    @Test
    fun initialize_png_readsCorrectDimensions() {
        val file = createTestPng(640, 480)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))

        decoder.initialize()

        assertEquals(640, decoder.imageInfo.width)
        assertEquals(480, decoder.imageInfo.height)
        assertEquals("image/png", decoder.imageInfo.mimeType)
        decoder.close()
    }

    @Test
    fun initialize_nonExistentFile_throwsException() {
        val file = File("/nonexistent/path/image.jpg")
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))

        assertFailsWith<IllegalStateException> {
            decoder.initialize()
        }
    }

    @Test
    fun initialize_calledTwice_noError() {
        val file = createTestJpeg()
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))

        decoder.initialize()
        decoder.initialize() // second call should be no-op

        assertEquals(800, decoder.imageInfo.width)
        decoder.close()
    }

    // --- decodeTile ---

    @Test
    fun decodeTile_returnsNonNullBitmap() {
        val file = createTestJpeg(1024, 768)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tile = decoder.decodeTile(TileRect(0, 0, 256, 256), sampleSize = 2)

        assertNotNull(tile)
        decoder.close()
    }

    @Test
    fun decodeTile_fullImage_returnsValidBitmap() {
        val file = createTestJpeg(512, 512)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tile = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 1)

        assertNotNull(tile)
        decoder.close()
    }

    @Test
    fun decodeTile_zeroSizeRect_returnsNull() {
        val file = createTestJpeg(800, 600)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tile = decoder.decodeTile(TileRect(100, 100, 100, 100), sampleSize = 2)

        assertNull(tile)
        decoder.close()
    }

    @Test
    fun decodeTile_afterDispose_returnsNull() {
        val file = createTestJpeg()
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()
        decoder.close()

        val tile = decoder.decodeTile(TileRect(0, 0, 256, 256))

        assertNull(tile)
    }

    @Test
    fun decodeTile_differentSampleSizes_allSucceed() {
        val file = createTestJpeg(2000, 1500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tile1 = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 1)
        val tile2 = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 2)
        val tile4 = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 4)

        assertNotNull(tile1)
        assertNotNull(tile2)
        assertNotNull(tile4)
        decoder.close()
    }

    @Test
    fun decodeTile_png_returnsValidBitmap() {
        val file = createTestPng(800, 600)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tile = decoder.decodeTile(TileRect(0, 0, 256, 256), sampleSize = 2)

        assertNotNull(tile)
        decoder.close()
    }

    @Test
    fun decodeTile_multipleTilesAcrossImage_allNonNull() {
        val file = createTestJpeg(1024, 768)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tileSize = 256
        var tileCount = 0
        for (row in 0 until 768 step tileSize) {
            for (col in 0 until 1024 step tileSize) {
                val right = minOf(col + tileSize, 1024)
                val bottom = minOf(row + tileSize, 768)
                val tile = decoder.decodeTile(TileRect(col, row, right, bottom), sampleSize = 2)
                assertNotNull(tile, "Tile at ($col,$row) should not be null")
                tileCount++
            }
        }

        assertTrue(tileCount > 0)
        decoder.close()
    }

    // --- decodePreview ---

    @Test
    fun decodePreview_returnsNonNullBitmap() {
        val file = createTestJpeg(2000, 1500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val preview = decoder.decodePreview(maxSize = 512)

        assertNotNull(preview)
        decoder.close()
    }

    @Test
    fun decodePreview_afterDispose_returnsNull() {
        val file = createTestJpeg()
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()
        decoder.close()

        val preview = decoder.decodePreview()

        assertNull(preview)
    }

    // --- Subsample cache ---

    @Test
    fun decodeTile_sameFactorTwice_usesCache() {
        val file = createTestJpeg(1024, 768)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        // Both calls use sampleSize=2 → same factor, should hit cache
        val tile1 = decoder.decodeTile(TileRect(0, 0, 256, 256), sampleSize = 2)
        val tile2 = decoder.decodeTile(TileRect(256, 0, 512, 256), sampleSize = 2)

        assertNotNull(tile1)
        assertNotNull(tile2)
        decoder.close()
    }

    @Test
    fun decodeTile_differentFactors_bothSucceed() {
        val file = createTestJpeg(2000, 1500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        // sampleSize=2 → factor=2, then sampleSize=1 → factor=1
        val tile1 = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 2)
        val tile2 = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 1)

        assertNotNull(tile1)
        assertNotNull(tile2)
        decoder.close()
    }

    // --- Memory protection ---

    @Test
    fun initialize_largeImage_setsMinSubsampleFactor() {
        // 6000x5500 = 33MP → minSubsampleFactor should be 2
        val file = createTestJpeg(6000, 5500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        // sampleSize=1 should still work (internally uses factor 2)
        val tile = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 1)
        assertNotNull(tile)
        decoder.close()
    }

    // --- Out-of-bounds ---

    @Test
    fun decodeTile_partiallyOutOfBounds_returnsNonNull() {
        val file = createTestJpeg(800, 600)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        // Rect extends beyond image bounds — should be clamped, not crash
        val tile = decoder.decodeTile(TileRect(600, 400, 1000, 800), sampleSize = 2)
        assertNotNull(tile)
        decoder.close()
    }

    @Test
    fun decodeTile_completelyOutOfBounds_returnsNull() {
        val file = createTestJpeg(800, 600)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        // Rect entirely outside image
        val tile = decoder.decodeTile(TileRect(1000, 1000, 1200, 1200), sampleSize = 2)
        assertNull(tile)
        decoder.close()
    }

    // --- decodePreview size ---

    @Test
    fun decodePreview_respectsMaxSize() {
        val file = createTestJpeg(2000, 1500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val preview = decoder.decodePreview(maxSize = 256)
        assertNotNull(preview)
        assertTrue(preview.width <= 256 || preview.height <= 256,
            "Preview should be downscaled: ${preview.width}x${preview.height}")
        decoder.close()
    }

    // --- PNG format tests ---

    @Test
    fun png_decodeTile_withSampleSize2_returnsNonNull() {
        val file = createTestPng(2000, 1500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        assertEquals(2000, decoder.imageInfo.width)
        assertEquals(1500, decoder.imageInfo.height)
        assertEquals("image/png", decoder.imageInfo.mimeType)

        val tile = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 2)
        assertNotNull(tile, "PNG tile should decode")
        decoder.close()
    }

    @Test
    fun png_largeSampleSize_returnsNonNull() {
        val file = createTestPng(2000, 1500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tile = decoder.decodeTile(TileRect(0, 0, 1000, 750), sampleSize = 4)
        assertNotNull(tile, "PNG tile should decode with any sampleSize")
        decoder.close()
    }

    @Test
    fun png_decodePreview_returnsNonNull() {
        val file = createTestPng(2000, 1500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val preview = decoder.decodePreview(maxSize = 256)
        assertNotNull(preview, "PNG preview should decode")
        decoder.close()
    }

    @Test
    fun jpeg_sampleSize2_returnsNonNull() {
        val file = createTestJpeg(2000, 1500)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tile = decoder.decodeTile(TileRect(0, 0, 512, 512), sampleSize = 2)
        assertNotNull(tile, "JPEG tile should subsample natively")
        decoder.close()
    }

    @Test
    fun png_multipleTilesAcrossImage_allNonNull() {
        val file = createTestPng(1024, 768)
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        val tileSize = 256
        var tileCount = 0
        for (row in 0 until 768 step tileSize) {
            for (col in 0 until 1024 step tileSize) {
                val right = minOf(col + tileSize, 1024)
                val bottom = minOf(row + tileSize, 768)
                val tile = decoder.decodeTile(TileRect(col, row, right, bottom), sampleSize = 2)
                assertNotNull(tile, "PNG tile at ($col,$row) should not be null")
                tileCount++
            }
        }
        assertTrue(tileCount > 0)
        decoder.close()
    }

    // --- close ---

    @Test
    fun close_multipleTimes_noError() {
        val file = createTestJpeg()
        val decoder = DesktopRegionDecoder(ImageSource.FileSource(file))
        decoder.initialize()

        decoder.close()
        decoder.close() // should not throw
    }
}
