package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BackgroundDebounceTest {

    @Test
    fun `returns false when backgroundAt is zero`() {
        assertFalse(shouldClearAfterBackground(backgroundAt = 0L, now = 100_000L))
    }

    @Test
    fun `returns false when elapsed is below threshold`() {
        val start = 1_000L
        val now = start + BACKGROUND_DEBOUNCE_MS - 1
        assertFalse(shouldClearAfterBackground(backgroundAt = start, now = now))
    }

    @Test
    fun `returns true when elapsed equals threshold`() {
        val start = 1_000L
        val now = start + BACKGROUND_DEBOUNCE_MS
        assertTrue(shouldClearAfterBackground(backgroundAt = start, now = now))
    }

    @Test
    fun `returns true when elapsed exceeds threshold`() {
        val start = 1_000L
        val now = start + BACKGROUND_DEBOUNCE_MS * 10
        assertTrue(shouldClearAfterBackground(backgroundAt = start, now = now))
    }

    @Test
    fun `honours custom threshold`() {
        val start = 0L
        assertFalse(shouldClearAfterBackground(start, now = 500L, thresholdMs = 1_000L))
        assertTrue(shouldClearAfterBackground(start + 1, now = 1_500L, thresholdMs = 1_000L))
    }
}
