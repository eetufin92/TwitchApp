package com.eetu.twitchapp.ui.player

import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt

data class AdDetectionResult(
    val isAdActive: Boolean,
    val adId: String,
    val durationSeconds: Int,
    val hasAdSegment: Boolean = false
)

object TwitchAdParser {

    /**
     * Parses an HLS media playlist (.m3u8) to detect whether Twitch Server-Side Ad Insertion (SSAI)
     * is currently broadcasting or draining buffered ad frames on the viewer's screen.
     *
     * In Twitch HLS playlists:
     * - Every playlist starts with #EXT-X-DATERANGE:ID="playlist-creation-...",CLASS="timestamp"
     *   which must be ignored.
     * - Stitched ads are identified by:
     *   1) #EXT-X-DATERANGE containing CLASS="twitch-stitched-ad" or ID containing "stitched-ad"
     *   2) Segment URLs containing "stitched-ad" or "twitch-stitched-ad"
     *   3) Explicit ad tags like #EXT-X-TWITCH-PREVIEW-AD
     */
    fun parse(
        content: String,
        bufferMs: Long,
        nowMs: Long = System.currentTimeMillis()
    ): AdDetectionResult {
        val lines = content.lines()

        var adDateRangeLine: String? = null
        var hasAdSegment = false
        var segmentAdId: String? = null
        var hasPreviewAdTag = false

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.startsWith("#EXT-X-DATERANGE:")) {
                // Ignore playlist-creation, twitch-session, twitch-stream-source, twitch-trigger
                if (trimmed.contains("twitch-stitched-ad") ||
                    trimmed.contains("stitched-ad") ||
                    trimmed.contains("EXT-X-TWITCH-PREVIEW-AD")
                ) {
                    adDateRangeLine = trimmed
                }
            } else if (trimmed.startsWith("#EXT-X-TWITCH-PREVIEW-AD") || trimmed.contains("EXT-X-TWITCH-PREVIEW-AD")) {
                hasPreviewAdTag = true
            } else if (!trimmed.startsWith("#") && (trimmed.contains("stitched-ad") || trimmed.contains("twitch-stitched-ad"))) {
                hasAdSegment = true
                if (segmentAdId == null) {
                    val match = Regex("""stitched-ad-([a-zA-Z0-9-]+?)(?:_[0-9]+|\.ts|\?|$)""").find(trimmed)
                    if (match != null) {
                        segmentAdId = "stitched-ad-${match.groupValues[1]}"
                    }
                }
            }
        }

        val hasAnyAdTag = (adDateRangeLine != null) || hasAdSegment || hasPreviewAdTag
        if (!hasAnyAdTag) {
            return AdDetectionResult(
                isAdActive = false,
                adId = "",
                durationSeconds = 0,
                hasAdSegment = false
            )
        }

        var adId = ""
        if (adDateRangeLine != null) {
            val idMatch = Regex("""ID="([^"]+)"""").find(adDateRangeLine)
            if (idMatch != null) {
                adId = idMatch.groupValues[1]
            }
        }
        if (adId.isEmpty()) {
            adId = segmentAdId ?: (if (hasPreviewAdTag) "twitch-preview-ad" else "stitched-ad")
        }

        var duration = 30
        if (adDateRangeLine != null) {
            val durMatch = Regex("""(?:PLANNED-)?DURATION=([0-9.]+)""").find(adDateRangeLine)
            if (durMatch != null) {
                durMatch.groupValues[1].toDoubleOrNull()?.roundToInt()?.let {
                    if (it in 5..180) duration = it
                }
            }
        }

        val bufferSec = max(1, ceil(bufferMs / 1000.0).toInt())

        var isExpired = false
        val startDateStr = if (adDateRangeLine != null) {
            Regex("""START-DATE="([^"]+)"""").find(adDateRangeLine)?.groupValues?.get(1)
        } else null

        val endDateStr = if (adDateRangeLine != null) {
            Regex("""END-DATE="([^"]+)"""").find(adDateRangeLine)?.groupValues?.get(1)
        } else null

        if (!startDateStr.isNullOrEmpty()) {
            try {
                val startMs = java.time.Instant.parse(startDateStr).toEpochMilli()
                val endMs = if (!endDateStr.isNullOrEmpty()) {
                    try {
                        java.time.Instant.parse(endDateStr).toEpochMilli()
                    } catch (e: Exception) {
                        startMs + (duration * 1000L)
                    }
                } else {
                    startMs + (duration * 1000L)
                }

                // The ad playback on client screen ends at endMs + bufferMs
                val clientEndMs = endMs + bufferMs
                if (nowMs > clientEndMs && !hasAdSegment) {
                    // Only mark expired if there are ALSO no ad segments in the playlist sliding window!
                    isExpired = true
                } else {
                    val remainingSec = ceil((clientEndMs - nowMs) / 1000.0).toInt()
                    val maxAllowed = duration + bufferSec + 5
                    if (remainingSec in 1..maxAllowed) {
                        duration = remainingSec
                    }
                }
            } catch (e: Exception) {
                // If timestamp parsing fails, trust hasAdSegment or default
                isExpired = false
            }
        } else {
            // When no explicit date range timestamps exist, account for buffer delay
            duration += bufferSec
        }

        val isAdActive = !isExpired
        return AdDetectionResult(
            isAdActive = isAdActive,
            adId = adId,
            durationSeconds = duration,
            hasAdSegment = hasAdSegment
        )
    }
}
