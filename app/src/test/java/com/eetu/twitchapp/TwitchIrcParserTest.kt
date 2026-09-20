package com.eetu.twitchapp

import com.eetu.twitchapp.data.chat.TwitchIrcClient
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TwitchIrcParserTest {

    @Test
    fun testIrcPrivMsgParsing() {
        val client = TwitchIrcClient()
        // Use reflection to test private parsePrivMsg method
        val parseMethod = TwitchIrcClient::class.java.getDeclaredMethod("parsePrivMsg", String::class.java)
        parseMethod.isAccessible = true

        val rawIrcLine = "@badge-info=subscriber/3;badges=broadcaster/1,subscriber/3003;color=#00FF7F;display-name=StreamerPro;emotes=25:0-4;id=123e4567-e89b-12d3-a456-426614174000;mod=0;room-id=12345;subscriber=1;tmi-sent-ts=1611277844517;turbo=0;user-id=12345;user-type= :streamerpro!streamerpro@streamerpro.tmi.twitch.tv PRIVMSG #streamerpro :Kappa hello chat!"

        val result = parseMethod.invoke(client, rawIrcLine) as? com.eetu.twitchapp.data.model.ChatMessage

        assertNotNull(result)
        assertEquals("StreamerPro", result?.displayName)
        assertEquals("#00FF7F", result?.color)
        assertEquals("Kappa hello chat!", result?.text)
        assertEquals(2, result?.badges?.size)
        assertEquals("broadcaster", result?.badges?.get(0)?.name)
        assertEquals("subscriber", result?.badges?.get(1)?.name)
        assertEquals(true, result?.twitchEmotes?.containsKey("25"))
    }

    @Test
    fun testIrcPrivMsgWithoutColorFallsBack() {
        val client = TwitchIrcClient()
        val parseMethod = TwitchIrcClient::class.java.getDeclaredMethod("parsePrivMsg", String::class.java)
        parseMethod.isAccessible = true

        val rawLine = "@display-name=NoColorUser;id=abc-123 :nocoloruser!user@user.tmi.twitch.tv PRIVMSG #testchannel :Hey guys"
        val result = parseMethod.invoke(client, rawLine) as? com.eetu.twitchapp.data.model.ChatMessage

        assertNotNull(result)
        assertEquals("NoColorUser", result?.displayName)
        assertEquals("#9146FF", result?.color) // Default fallback color
        assertEquals("Hey guys", result?.text)
    }
}
