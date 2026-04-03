package com.github.bentleypark.tessera

import kotlinx.coroutines.test.runTest
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class DesktopImageLoaderTest {

    private fun createTestFile(): File {
        val image = BufferedImage(100, 100, BufferedImage.TYPE_INT_RGB)
        val g = image.createGraphics()
        g.color = Color.RED
        g.fillRect(0, 0, 100, 100)
        g.dispose()
        val file = File.createTempFile("tessera_loader_test_", ".jpg")
        file.deleteOnExit()
        ImageIO.write(image, "JPEG", file)
        return file
    }

    // --- file:// URL ---

    @Test
    fun loadImageSource_fileUrl_returnsFileSource() = runTest {
        val file = createTestFile()
        val loader = DesktopImageLoader()

        val result = loader.loadImageSource("file://${file.absolutePath}")

        assertTrue(result.isSuccess)
        assertIs<ImageSource.FileSource>(result.getOrNull())
        val source = result.getOrNull() as ImageSource.FileSource
        assertTrue(source.file.exists())
    }

    @Test
    fun loadImageSource_nonExistentFile_returnsFailure() = runTest {
        val loader = DesktopImageLoader()

        val result = loader.loadImageSource("file:///nonexistent/path/image.jpg")

        assertTrue(result.isFailure)
    }

    // --- Local file path ---

    @Test
    fun loadImageSource_localPath_returnsFileSource() = runTest {
        val file = createTestFile()
        val loader = DesktopImageLoader()

        val result = loader.loadImageSource(file.absolutePath)

        assertTrue(result.isSuccess)
        assertIs<ImageSource.FileSource>(result.getOrNull())
    }

    @Test
    fun loadImageSource_nonExistentLocalPath_returnsFailure() = runTest {
        val loader = DesktopImageLoader()

        val result = loader.loadImageSource("/nonexistent/image.jpg")

        assertTrue(result.isFailure)
    }

    // --- Unsupported scheme ---

    @Test
    fun loadImageSource_unknownScheme_returnsFailure() = runTest {
        val loader = DesktopImageLoader()

        val result = loader.loadImageSource("ftp://example.com/image.jpg")

        assertTrue(result.isFailure)
    }

    // --- clearCache ---

    @Test
    fun clearCache_noError() = runTest {
        val loader = DesktopImageLoader()
        loader.clearCache() // should not throw even with empty cache
    }

    @Test
    fun clearCache_deletesCachedFiles() = runTest {
        val cacheDir = File(System.getProperty("java.io.tmpdir"), "tessera_cache")
        cacheDir.mkdirs()

        // Create fake cache files
        val cacheFile = File(cacheDir, "tessera_fakehash123")
        cacheFile.writeText("fake image data")
        assertTrue(cacheFile.exists())

        val loader = DesktopImageLoader()
        loader.clearCache()

        assertFalse(cacheFile.exists(), "Cache file should be deleted after clearCache")
    }

    // --- Integration: load → decode ---

    @Test
    fun loadThenDecode_fileUrl_works() = runTest {
        val file = createTestFile()
        val loader = DesktopImageLoader()

        val result = loader.loadImageSource("file://${file.absolutePath}")
        assertTrue(result.isSuccess)

        val source = result.getOrNull()!!
        val decoder = DesktopRegionDecoder(source)
        decoder.initialize()

        val tile = decoder.decodeTile(TileRect(0, 0, 50, 50), sampleSize = 1)
        assertNotNull(tile)

        val preview = decoder.decodePreview(maxSize = 64)
        assertNotNull(preview)

        decoder.close()
    }
}
