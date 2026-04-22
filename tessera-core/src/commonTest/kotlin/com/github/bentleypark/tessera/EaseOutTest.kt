package com.github.bentleypark.tessera

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EaseOutTest {

    @Test
    fun easeOut_zero_returnsZero() {
        assertEquals(0f, easeOut(0f))
    }

    @Test
    fun easeOut_one_returnsOne() {
        assertEquals(1f, easeOut(1f))
    }

    @Test
    fun easeOut_midpoint_greaterThanLinear() {
        // EaseOut at t=0.5 should be > 0.5 (fast start)
        val result = easeOut(0.5f)
        assertTrue(result > 0.5f, "easeOut(0.5) = $result should be > 0.5")
    }

    @Test
    fun easeOut_monotonicallyIncreasing() {
        var prev = 0f
        for (i in 1..10) {
            val t = i / 10f
            val value = easeOut(t)
            assertTrue(value >= prev, "easeOut($t) = $value should be >= easeOut(${(i-1)/10f}) = $prev")
            prev = value
        }
    }
}
