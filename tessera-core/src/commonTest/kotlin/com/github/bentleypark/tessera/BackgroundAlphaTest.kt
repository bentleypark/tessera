package com.github.bentleypark.tessera

import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Verifies the previous-level tile opacity decision used during zoom transitions
 * (issue #49). Background tiles must stay fully opaque until the new level is
 * fully loaded, then EaseOut crossfade out over `animDuration`.
 */
class BackgroundAlphaTest {

    private val tolerance = 0.001f

    private fun assertNear(expected: Float, actual: Float, label: String = "") {
        assertTrue(
            abs(expected - actual) < tolerance,
            "expected ~$expected but got $actual ${if (label.isNotEmpty()) "($label)" else ""}"
        )
    }

    // --- Hold full opacity until coverage is ready ---

    @Test
    fun notReady_returnsFullOpacity() {
        val alpha = decideBackgroundAlpha(
            coverageReady = false,
            coverageCompleteTime = 1000L,
            currentTime = 5000L,
            animDuration = 200L
        )
        assertEquals(1f, alpha)
    }

    @Test
    fun ready_butCompleteTimeNotCaptured_holdsOpacity() {
        // The capture LaunchedEffect hasn't run yet (coverageCompleteTime == 0L).
        // Conservatively hold the previous level visible — fading without an anchor
        // would produce a frame-dependent flicker.
        val alpha = decideBackgroundAlpha(
            coverageReady = true,
            coverageCompleteTime = 0L,
            currentTime = 5000L,
            animDuration = 200L
        )
        assertEquals(1f, alpha)
    }

    // --- Crossfade once ready and captured ---

    @Test
    fun ready_atCaptureMoment_returnsFullOpacity() {
        val alpha = decideBackgroundAlpha(
            coverageReady = true,
            coverageCompleteTime = 1000L,
            currentTime = 1000L,
            animDuration = 200L
        )
        assertEquals(1f, alpha)
    }

    @Test
    fun ready_halfwayThroughAnimation_returnsEaseOutMidpoint() {
        // EaseOut(0.5) = 1 - (0.5)^2 = 0.75 → background alpha = 1 - 0.75 = 0.25
        val alpha = decideBackgroundAlpha(
            coverageReady = true,
            coverageCompleteTime = 1000L,
            currentTime = 1100L,
            animDuration = 200L
        )
        assertNear(0.25f, alpha, "halfway")
    }

    @Test
    fun ready_atAnimationEnd_returnsZero() {
        val alpha = decideBackgroundAlpha(
            coverageReady = true,
            coverageCompleteTime = 1000L,
            currentTime = 1200L,
            animDuration = 200L
        )
        assertEquals(0f, alpha)
    }

    @Test
    fun ready_pastAnimationEnd_clampsToZero() {
        val alpha = decideBackgroundAlpha(
            coverageReady = true,
            coverageCompleteTime = 1000L,
            currentTime = 9999L,
            animDuration = 200L
        )
        assertEquals(0f, alpha)
    }

    // --- Animation-disabled cases ---

    @Test
    fun animationDisabled_returnsZeroAfterReady() {
        // animDuration=0 means user opted out of crossfade — hide instantly once ready.
        val alpha = decideBackgroundAlpha(
            coverageReady = true,
            coverageCompleteTime = 1000L,
            currentTime = 1000L,
            animDuration = 0L
        )
        assertEquals(0f, alpha)
    }

    @Test
    fun animationDisabled_butNotReady_stillHoldsOpacity() {
        // Even with animation off, the previous level must stay visible while the
        // current level is still loading (otherwise blank/blurry regions appear).
        val alpha = decideBackgroundAlpha(
            coverageReady = false,
            coverageCompleteTime = 0L,
            currentTime = 5000L,
            animDuration = 0L
        )
        assertEquals(1f, alpha)
    }

    // --- Defensive: negative elapsed (clock drift / replay) ---

    @Test
    fun negativeElapsed_clampsToFullOpacity() {
        // Should never happen in practice but guard against time going backwards.
        val alpha = decideBackgroundAlpha(
            coverageReady = true,
            coverageCompleteTime = 5000L,
            currentTime = 1000L,
            animDuration = 200L
        )
        assertEquals(1f, alpha)
    }
}
