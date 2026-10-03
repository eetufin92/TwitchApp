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
        val bufferMs = 4500L

        val startMs = java.time.Instant.parse(activeStartDate).toEpochMilli()
        val endMs = startMs + (duration * 1000L).toLong()
        val clientEndMs = endMs + bufferMs

        // Ad started 10s ago with 30s duration + 4.5s buffer -> currently active
        val isExpired = now > clientEndMs
        assertEquals(false, isExpired)

        // Remaining seconds on client includes buffer delay (around 24-25s)
        val remainingSec = kotlin.math.ceil((clientEndMs - now) / 1000.0).toInt()
        assertTrue("Remaining seconds including buffer should be around 24-25, was $remainingSec", remainingSec in 23..26)

        // At the exact moment server finishes ad (now == endMs):
        val isExpiredAtServerEnd = endMs > clientEndMs
        assertEquals(false, isExpiredAtServerEnd)
        val remainingAtServerEnd = kotlin.math.ceil((clientEndMs - endMs) / 1000.0).toInt()
        assertEquals("When server finishes ad, client still has buffer seconds remaining", 5, remainingAtServerEnd)

        // Expired ad: started 40s ago with 30s duration + 4.5s buffer (now is 40s, client end was at 34.5s)
        val expiredStartDate = java.time.Instant.ofEpochMilli(now - 40_000L).toString()
        val expStartMs = java.time.Instant.parse(expiredStartDate).toEpochMilli()
        val expEndMs = expStartMs + (duration * 1000L).toLong()
        val expClientEndMs = expEndMs + bufferMs
        val isReallyExpired = now > expClientEndMs
        assertEquals(true, isReallyExpired)
    }

    @Test
    fun testBufferSecondsConversionAndClamping() {
        // Low latency defaults (4500ms)
        val bufferMs1 = 4500L
        val bufferSec1 = kotlin.math.max(1, kotlin.math.ceil(bufferMs1 / 1000.0).toInt())
        assertEquals(5, bufferSec1)

        // Ultra low latency (2000ms)
        val bufferMs2 = 2000L
        val bufferSec2 = kotlin.math.max(1, kotlin.math.ceil(bufferMs2 / 1000.0).toInt())
        assertEquals(2, bufferSec2)

        // Standard latency (8000ms)
        val bufferMs3 = 8000L
        val bufferSec3 = kotlin.math.max(1, kotlin.math.ceil(bufferMs3 / 1000.0).toInt())
        assertEquals(8, bufferSec3)
    }

    @Test
    fun testCleanStreamBufferDrainRemaining() {
        val bufferMs = 4500L

        // Immediately when clean stream arrives at live edge (timeSinceClean = 0)
        val remaining0 = kotlin.math.max(0, kotlin.math.ceil((bufferMs - 0L) / 1000.0).toInt())
        assertEquals(5, remaining0)

        // 2 seconds after clean stream arrives
        val remaining2 = kotlin.math.max(0, kotlin.math.ceil((bufferMs - 2000L) / 1000.0).toInt())
        assertEquals(3, remaining2)

        // 4.5 seconds after clean stream arrives (buffer fully drained)
        val remaining45 = kotlin.math.max(0, kotlin.math.ceil((bufferMs - 4500L) / 1000.0).toInt())
        assertEquals(0, remaining45)

        // 6 seconds after clean stream arrives
        val remaining6 = kotlin.math.max(0, kotlin.math.ceil((bufferMs - 6000L) / 1000.0).toInt())
        assertEquals(0, remaining6)
    }

    @Test
    fun testTwitchAdParserCleanPlaylistWithTimestamps() {
        val cleanPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-TARGETDURATION:6
            #EXT-X-MEDIA-SEQUENCE:5203
            #EXT-X-DATERANGE:ID="playlist-creation-1791034122",CLASS="timestamp",START-DATE="2026-10-03T13:28:42.072Z",END-ON-NEXT=YES,X-SERVER-TIME="1791034122.07"
            #EXT-X-DATERANGE:ID="playlist-session-1791034122",CLASS="twitch-session",START-DATE="2026-10-03T13:28:42.072Z",END-ON-NEXT=YES,X-TV-TWITCH-SESSIONID="9103132761902595666"
            #EXT-X-DATERANGE:ID="source-1791034088",CLASS="twitch-stream-source",START-DATE="2026-10-03T13:28:08.380Z",END-ON-NEXT=YES,X-TV-TWITCH-STREAM-SOURCE="live"
            #EXT-X-DATERANGE:ID="trigger-1791034088",CLASS="twitch-trigger",START-DATE="2026-10-03T13:28:08.380Z",END-ON-NEXT=YES
            #EXTINF:4.166,live
            https://video-edge-test.ts
            #EXTINF:4.167,live
            https://video-edge-test-2.ts
        """.trimIndent()

        val result = com.eetu.twitchapp.ui.player.TwitchAdParser.parse(cleanPlaylist, bufferMs = 4500L)
        assertEquals(false, result.isAdActive)
        assertEquals("", result.adId)
        assertEquals(0, result.durationSeconds)
        assertEquals(false, result.hasAdSegment)
    }

    @Test
    fun testTwitchAdParserActiveStitchedAd() {
        val now = 100_000L
        val startDateStr = java.time.Instant.ofEpochMilli(now - 5_000L).toString() // Started 5s ago
        val adPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-DATERANGE:ID="playlist-creation-1791034122",CLASS="timestamp",START-DATE="2026-10-03T13:00:00.000Z"
            #EXT-X-DATERANGE:ID="stitched-ad-987654",CLASS="twitch-stitched-ad",START-DATE="$startDateStr",DURATION=30.0,PLANNED-DURATION=30.0
            #EXTINF:2.000,live
            https://video-edge-test.ts/stitched-ad-987654_0.ts
            #EXTINF:2.000,live
            https://video-edge-test.ts/stitched-ad-987654_1.ts
        """.trimIndent()

        val result = com.eetu.twitchapp.ui.player.TwitchAdParser.parse(adPlaylist, bufferMs = 4500L, nowMs = now)
        assertEquals(true, result.isAdActive)
        assertEquals("stitched-ad-987654", result.adId)
        assertEquals(true, result.hasAdSegment)
        // 30s ad started 5s ago + 4.5s buffer = 29.5s remaining -> ceil 30s
        assertTrue("Remaining duration should be around 29-30s, was ${result.durationSeconds}", result.durationSeconds in 29..30)
    }

    @Test
    fun testTwitchAdParserBufferDrainWhenAdEndsOnServer() {
        val now = 100_000L
        // Started 31s ago, 30s duration, so server finished 1s ago!
        val startDateStr = java.time.Instant.ofEpochMilli(now - 31_000L).toString()
        val drainingPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-DATERANGE:ID="playlist-creation-1791034122",CLASS="timestamp",START-DATE="2026-10-03T13:00:00.000Z"
            #EXT-X-DATERANGE:ID="stitched-ad-987654",CLASS="twitch-stitched-ad",START-DATE="$startDateStr",DURATION=30.0
            #EXTINF:2.000,live
            https://video-edge-test.ts/normal-segment-1.ts
            #EXTINF:2.000,live
            https://video-edge-test.ts/normal-segment-2.ts
        """.trimIndent()

        // Buffer is 4.5s (4500ms). Client end is 30s + 4.5s = 34.5s.
        // Ad has been running 31s -> 3.5s remaining on client screen!
        val result = com.eetu.twitchapp.ui.player.TwitchAdParser.parse(drainingPlaylist, bufferMs = 4500L, nowMs = now)
        assertEquals(true, result.isAdActive)
        assertEquals("stitched-ad-987654", result.adId)
        assertEquals(false, result.hasAdSegment)
        assertEquals(4, result.durationSeconds) // ceil(3.5s) = 4s
    }

    @Test
    fun testTwitchAdParserExpiredWhenBufferDrained() {
        val now = 100_000L
        // Started 36s ago, 30s duration + 4.5s buffer = 34.5s client end. Server and buffer both drained!
        val startDateStr = java.time.Instant.ofEpochMilli(now - 36_000L).toString()
        val expiredPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXT-X-DATERANGE:ID="playlist-creation-1791034122",CLASS="timestamp",START-DATE="2026-10-03T13:00:00.000Z"
            #EXT-X-DATERANGE:ID="stitched-ad-987654",CLASS="twitch-stitched-ad",START-DATE="$startDateStr",DURATION=30.0
            #EXTINF:2.000,live
            https://video-edge-test.ts/normal-segment-1.ts
        """.trimIndent()

        val result = com.eetu.twitchapp.ui.player.TwitchAdParser.parse(expiredPlaylist, bufferMs = 4500L, nowMs = now)
        assertEquals(false, result.isAdActive)
        assertEquals("stitched-ad-987654", result.adId)
        assertEquals(false, result.hasAdSegment)
    }

    @Test
    fun testTwitchAdParserSegmentOnlyAd() {
        val segmentOnlyPlaylist = """
            #EXTM3U
            #EXT-X-VERSION:3
            #EXTINF:2.000,live
            https://video-edge-test.ts/stitched-ad-abc-xyz_0.ts
            #EXTINF:2.000,live
            https://video-edge-test.ts/stitched-ad-abc-xyz_1.ts
        """.trimIndent()

        val result = com.eetu.twitchapp.ui.player.TwitchAdParser.parse(segmentOnlyPlaylist, bufferMs = 4500L)
        assertEquals(true, result.isAdActive)
        assertEquals("stitched-ad-abc-xyz", result.adId)
        assertEquals(true, result.hasAdSegment)
        // 30s default + 5s buffer = 35s
        assertEquals(35, result.durationSeconds)
    }
}
