package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class EvictZoomLevelTest {

    private fun tiles(vararg entries: Pair<String, Int>): MutableMap<String, TileLoadInfo> =
        entries.associate { (key, zoom) -> key to TileLoadInfo(loadTime = 0L, zoomLevel = zoom) }
            .toMutableMap()

    @Test
    fun removesOnlyMatchingZoomLevel() {
        val map = tiles("a" to 1, "b" to 1, "c" to 2, "d" to 3)
        evictZoomLevel(map, 1)
        assertEquals(setOf("c", "d"), map.keys)
        assertFalse("a" in map)
        assertFalse("b" in map)
    }

    @Test
    fun noMatch_leavesMapUnchanged() {
        val map = tiles("a" to 2, "b" to 3)
        evictZoomLevel(map, 1)
        assertEquals(setOf("a", "b"), map.keys)
    }

    @Test
    fun allMatch_clearsMap() {
        val map = tiles("a" to 0, "b" to 0)
        evictZoomLevel(map, 0)
        assertTrue(map.isEmpty())
    }

    @Test
    fun emptyMap_isNoop() {
        val map = mutableMapOf<String, TileLoadInfo>()
        evictZoomLevel(map, 0)
        assertTrue(map.isEmpty())
    }

    @Test
    fun sentinelNoTransition_removesNothing() {
        // previousZoomLevel == -1 (no transition in progress) must be a safe no-op —
        // the integration calls evictZoomLevel(..., previousZoomLevel) unconditionally.
        val map = tiles("a" to 0, "b" to 1)
        evictZoomLevel(map, -1)
        assertEquals(setOf("a", "b"), map.keys)
    }
}
