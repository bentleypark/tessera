package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TesseraViewerStateTest {

    @Test
    fun defaultValues() {
        val state = TesseraViewerState()
        assertEquals(1f, state.scale)
        assertTrue(state.isLoading)
        assertNull(state.imageInfo)
        assertNull(state.error)
        assertEquals(-1, state.zoomLevel)
        assertEquals(0, state.cachedTileCount)
        assertFalse(state.isReady)
    }

    @Test
    fun isReady_trueWhenLoadedSuccessfully() {
        val state = TesseraViewerState()
        state.isLoading = false
        state.imageInfo = ImageInfo(width = 4000, height = 3000, mimeType = "image/jpeg")
        state.error = null

        assertTrue(state.isReady)
    }

    @Test
    fun isReady_falseWhileLoading() {
        val state = TesseraViewerState()
        state.isLoading = true
        state.imageInfo = ImageInfo(width = 4000, height = 3000)

        assertFalse(state.isReady)
    }

    @Test
    fun isReady_falseOnError() {
        val state = TesseraViewerState()
        state.isLoading = false
        state.error = "decode failed"
        state.imageInfo = null

        assertFalse(state.isReady)
    }

    @Test
    fun isReady_falseWhenErrorPresentEvenWithImageInfo() {
        val state = TesseraViewerState()
        state.isLoading = false
        state.imageInfo = ImageInfo(width = 4000, height = 3000)
        state.error = "partial failure"

        assertFalse(state.isReady)
    }

    @Test
    fun isReady_falseWhenImageInfoIsNull() {
        val state = TesseraViewerState()
        state.isLoading = false
        state.error = null

        assertFalse(state.isReady)
    }

    @Test
    fun scaleUpdates() {
        val state = TesseraViewerState()
        state.scale = 3.5f
        assertEquals(3.5f, state.scale)
    }

    @Test
    fun zoomLevelUpdates() {
        val state = TesseraViewerState()
        state.zoomLevel = 2
        assertEquals(2, state.zoomLevel)
    }

    @Test
    fun cachedTileCountUpdates() {
        val state = TesseraViewerState()
        state.cachedTileCount = 42
        assertEquals(42, state.cachedTileCount)
    }

    @Test
    fun imageInfoUpdates() {
        val state = TesseraViewerState()
        val info = ImageInfo(width = 8000, height = 6000, mimeType = "image/png")
        state.imageInfo = info
        assertEquals(8000, state.imageInfo?.width)
        assertEquals(6000, state.imageInfo?.height)
        assertEquals("image/png", state.imageInfo?.mimeType)
    }

    @Test
    fun stateCanTransitionBackToLoading() {
        val state = TesseraViewerState()
        state.isLoading = false
        state.imageInfo = ImageInfo(width = 100, height = 100)
        assertTrue(state.isReady)

        state.isLoading = true
        state.imageInfo = null
        assertFalse(state.isReady)
    }

    @Test
    fun syncUpdatesAllFieldsAtomically() {
        val state = TesseraViewerState()
        val info = ImageInfo(width = 2000, height = 1500, mimeType = "image/jpeg")

        state.sync(
            scale = 2.5f,
            zoomLevel = 1,
            cachedTileCount = 10,
            isLoading = false,
            imageInfo = info,
            error = null
        )

        assertEquals(2.5f, state.scale)
        assertEquals(1, state.zoomLevel)
        assertEquals(10, state.cachedTileCount)
        assertFalse(state.isLoading)
        assertEquals(info, state.imageInfo)
        assertNull(state.error)
        assertTrue(state.isReady)
    }

    @Test
    fun syncWithError() {
        val state = TesseraViewerState()

        state.sync(
            scale = 1f,
            zoomLevel = -1,
            cachedTileCount = 0,
            isLoading = false,
            imageInfo = null,
            error = "load failed"
        )

        assertFalse(state.isLoading)
        assertNull(state.imageInfo)
        assertEquals("load failed", state.error)
        assertFalse(state.isReady)
    }
}

class SyncViewerStateTest {

    @Test
    fun syncWithNullTesseraState_noError() {
        val vs = TesseraViewerState()

        syncViewerState(
            vs = vs,
            scale = 1f,
            zoomLevel = -1,
            tileCount = 0,
            tesseraState = null,
            loadError = null
        )

        assertTrue(vs.isLoading)
        assertNull(vs.imageInfo)
        assertNull(vs.error)
    }

    @Test
    fun syncWithNullTesseraState_withLoadError() {
        val vs = TesseraViewerState()

        syncViewerState(
            vs = vs,
            scale = 1f,
            zoomLevel = -1,
            tileCount = 0,
            tesseraState = null,
            loadError = "network error"
        )

        assertFalse(vs.isLoading)
        assertNull(vs.imageInfo)
        assertEquals("network error", vs.error)
    }
}
