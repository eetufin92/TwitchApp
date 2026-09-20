package com.eetu.twitchapp

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmoteMatchingTest {

    @Test
    fun testEmoteTokenExtraction() {
        val emoteMap = mapOf(
            "KEKW" to "https://cdn.7tv.app/emote/60afb5d8e09f5db760920ef0/2x.webp",
            "Pog" to "https://cdn.7tv.app/emote/60ae3e620583b28b704cbf9b/2x.webp",
            "catJAM" to "https://cdn.7tv.app/emote/60ae3fd40583b28b704cc03b/2x.webp"
        )

        val message = "omg that was insane KEKW look at him Pog"
        val words = message.split("\\s+".toRegex())

        val matchedEmotes = words.filter { emoteMap.containsKey(it) }

        assertEquals(2, matchedEmotes.size)
        assertEquals("KEKW", matchedEmotes[0])
        assertEquals("Pog", matchedEmotes[1])
    }

    @Test
    fun testMultiStreamMaxCapacity() {
        val streams = mutableListOf("shroud", "tarik", "xqc")
        val maxStreams = 4

        val newStream = "kaicenat"
        if (streams.size < maxStreams && !streams.contains(newStream)) {
            streams.add(newStream)
        }

        assertEquals(4, streams.size)
        assertTrue(streams.contains("kaicenat"))

        // Attempting to add a 5th stream should be blocked
        val extraStream = "pokimane"
        if (streams.size < maxStreams && !streams.contains(extraStream)) {
            streams.add(extraStream)
        }
        assertEquals(4, streams.size)
    }

    @Test
    fun testAudioSwitchingInMultiStream() {
        val streams = listOf("stream1", "stream2", "stream3")
        var activeAudioChannel = streams[0]

        // Switch audio to stream 2
        activeAudioChannel = streams[1]
        assertEquals("stream2", activeAudioChannel)

        // Switch audio to stream 3
        activeAudioChannel = streams[2]
        assertEquals("stream3", activeAudioChannel)
    }
}
