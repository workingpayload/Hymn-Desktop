package com.example.musicsmd.support

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class SupportPromptScheduleTest {

    private val shown = 1_000_000_000_000L

    @Test
    fun dueTwoDaysAfterItLastShowedNotBefore() {
        assertFalse(SupportPromptSchedule.isDue(shown, shown + SupportPromptSchedule.INTERVAL_MS - 1))
        assertTrue(SupportPromptSchedule.isDue(shown, shown + SupportPromptSchedule.INTERVAL_MS))
    }

    @Test
    fun waitsARandomMomentWithinTheWindow() {
        val random = Random(42)
        val delays = List(500) { SupportPromptSchedule.delayMs(random) }
        assertTrue(delays.all { it in SupportPromptSchedule.MIN_DELAY_MS..SupportPromptSchedule.MAX_DELAY_MS })
        assertTrue("delays should vary", delays.toSet().size > 1)
    }
}
