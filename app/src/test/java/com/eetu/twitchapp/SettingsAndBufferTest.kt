package com.eetu.twitchapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsAndBufferTest {

    @Test
    fun testLowLatencyBufferOffsetCalculations() {
        val testPresets = listOf(
            2000 to "Ultra",
            3500 to "Balanced",
            4500 to "Default Safe",
            5000 to "Safe",
            6500 to "Extra Safe"
        )

        for ((targetMs, name) in testPresets) {
            val clamped = targetMs.coerceIn(1500, 8000)
            val minOffset = (clamped - 1500).coerceAtLeast(1000)
            val maxOffset = clamped + 3500

            assertTrue("Min offset must be >= 1000 for $name", minOffset >= 1000)
            assertTrue("Target offset must be > min offset for $name", clamped > minOffset)
            assertTrue("Max offset must be > target offset for $name", maxOffset > clamped)
        }
    }

    @Test
    fun testLowLatencyBufferClamping() {
        val lowInput = 500
        val clampedLow = lowInput.coerceIn(1500, 8000)
        assertEquals(1500, clampedLow)

        val highInput = 15000
        val clampedHigh = highInput.coerceIn(1500, 8000)
        assertEquals(8000, clampedHigh)

        val normalInput = 4500
        val clampedNormal = normalInput.coerceIn(1500, 8000)
        assertEquals(4500, clampedNormal)
    }

    @Test
    fun testThumbnailSizeRange() {
        val defaultSize = 125
        assertTrue(defaultSize in 90..180)

        val smallSize = 80.coerceIn(90, 180)
        assertEquals(90, smallSize)

        val largeSize = 250.coerceIn(90, 180)
        assertEquals(180, largeSize)
    }

    @Test
    fun testAdDateRangeActiveAndExpiredCalculations() {
        val now = System.currentTimeMillis()
        val activeStartDate = java.time.Instant.ofEpochMilli(now - 10_000L).toString()
        val duration = 30.0

        val startMs = java.time.Instant.parse(activeStartDate).toEpochMilli()
        val endMs = startMs + (duration * 1000L).toLong()

        // Ad started 10s ago with 30s duration -> currently active
        val isExpired = now > endMs + 4000L
        assertEquals(false, isExpired)

        val remainingSec = ((endMs - now) / 1000L).toInt()
        assertTrue("Remaining seconds should be around 20", remainingSec in 18..21)

        // Expired ad: started 40s ago with 30s duration
        val expiredStartDate = java.time.Instant.ofEpochMilli(now - 40_000L).toString()
        val expStartMs = java.time.Instant.parse(expiredStartDate).toEpochMilli()
        val expEndMs = expStartMs + (duration * 1000L).toLong()
        val isReallyExpired = now > expEndMs + 4000L
        assertEquals(true, isReallyExpired)
    }
}
