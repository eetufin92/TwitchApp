package com.eetu.twitchapp.data.chat

import android.util.Log
import com.eetu.twitchapp.data.model.ChatBadge
import com.eetu.twitchapp.data.model.ChatMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import okhttp3.*
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlin.random.Random

class TwitchIrcClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS) // infinite for WebSockets
        .pingInterval(30, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "TwitchIrcClient"
        private const val WSS_URL = "wss://irc-ws.chat.twitch.tv:443"
    }

    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var webSocket: WebSocket? = null
    private var currentChannel: String? = null

    private val _messages = MutableSharedFlow<ChatMessage>(extraBufferCapacity = 100)
    val messages: SharedFlow<ChatMessage> = _messages.asSharedFlow()

    fun connectAndJoin(channelName: String) {
        val cleanChannel = channelName.trim().lowercase()
        if (cleanChannel.isEmpty()) return

        if (webSocket != null && currentChannel == cleanChannel) {
            return // already connected to this channel
        }

        if (webSocket != null) {
            val old = currentChannel
            if (old != null) {
                webSocket?.send("PART #$old")
            }
            currentChannel = cleanChannel
            webSocket?.send("JOIN #$cleanChannel")
            return
        }

        currentChannel = cleanChannel
        val request = Request.Builder().url(WSS_URL).build()

        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "Connected to Twitch IRC WebSocket for #$cleanChannel")
                webSocket.send("CAP REQ :twitch.tv/tags twitch.tv/commands")
                webSocket.send("PASS SCHMOOPIIE")
                val randomNick = "justinfan" + Random.nextInt(10000, 99999)
                webSocket.send("NICK $randomNick")
                webSocket.send("JOIN #$cleanChannel")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                text.lines().forEach { line ->
                    handleIrcLine(webSocket, line.trim())
                }
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "IRC WebSocket failure: ${t.message}")
                this@TwitchIrcClient.webSocket = null
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                Log.d(TAG, "IRC WebSocket closed: $reason")
                this@TwitchIrcClient.webSocket = null
            }
        })
    }

    fun disconnect() {
        try {
            currentChannel?.let { webSocket?.send("PART #$it") }
            webSocket?.close(1000, "Leaving chat")
        } catch (e: Exception) {
            Log.w(TAG, "Error disconnecting IRC", e)
        } finally {
            webSocket = null
            currentChannel = null
        }
    }

    private fun handleIrcLine(ws: WebSocket, line: String) {
        if (line.isEmpty()) return

        // Handle PING / PONG keepalive
        if (line.startsWith("PING")) {
            ws.send("PONG :tmi.twitch.tv")
            return
        }

        // Only parse PRIVMSG messages
        if (!line.contains("PRIVMSG")) return

        try {
            val chatMsg = parsePrivMsg(line)
            if (chatMsg != null) {
                scope.launch {
                    _messages.emit(chatMsg)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing IRC message line: $line", e)
        }
    }

    private fun parsePrivMsg(line: String): ChatMessage? {
        // Format: @tags :user!user@user.tmi.twitch.tv PRIVMSG #channel :message
        var tagsStr = ""
        var rest = line

        if (line.startsWith("@")) {
            val spaceIdx = line.indexOf(' ')
            if (spaceIdx > 0) {
                tagsStr = line.substring(1, spaceIdx)
                rest = line.substring(spaceIdx + 1)
            }
        }

        val privMsgIdx = rest.indexOf("PRIVMSG")
        if (privMsgIdx == -1) return null

        // Extract message text (after "PRIVMSG #channel :")
        val colonIdx = rest.indexOf(':', privMsgIdx)
        if (colonIdx == -1) return null
        val messageText = rest.substring(colonIdx + 1)

        // Parse tags
        val tagMap = parseTags(tagsStr)

        val id = tagMap["id"] ?: UUID.randomUUID().toString()
        val displayName = tagMap["display-name"]?.ifEmpty { null }
            ?: extractNickFromPrefix(rest.substring(0, privMsgIdx).trim())
        val color = tagMap["color"]?.ifEmpty { null } ?: "#9146FF"

        // Badges: e.g. "broadcaster/1,subscriber/12"
        val badges = tagMap["badges"]?.split(",")?.mapNotNull { badgeStr ->
            val parts = badgeStr.split("/")
            if (parts.size >= 2) ChatBadge(parts[0], parts[1]) else null
        } ?: emptyList()

        // Twitch emotes: e.g. "25:0-4,12-16/1902:6-10"
        val twitchEmotes = parseTwitchEmotes(tagMap["emotes"] ?: "")

        return ChatMessage(
            id = id,
            user = displayName.lowercase(),
            displayName = displayName,
            color = color,
            badges = badges,
            text = messageText,
            twitchEmotes = twitchEmotes
        )
    }

    private fun parseTags(tagsStr: String): Map<String, String> {
        if (tagsStr.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, String>()
        tagsStr.split(";").forEach { item ->
            val eqIdx = item.indexOf('=')
            if (eqIdx > 0) {
                val key = item.substring(0, eqIdx)
                val value = item.substring(eqIdx + 1)
                map[key] = value
            }
        }
        return map
    }

    private fun extractNickFromPrefix(prefix: String): String {
        val start = if (prefix.startsWith(":")) 1 else 0
        val excl = prefix.indexOf('!')
        return if (excl > start) {
            prefix.substring(start, excl)
        } else {
            "TwitchUser"
        }
    }

    private fun parseTwitchEmotes(emotesTag: String): Map<String, List<IntRange>> {
        if (emotesTag.isEmpty()) return emptyMap()
        val map = mutableMapOf<String, MutableList<IntRange>>()
        try {
            // "25:0-4,12-16/1902:6-10"
            emotesTag.split("/").forEach { emoteEntry ->
                val colon = emoteEntry.indexOf(':')
                if (colon > 0) {
                    val emoteId = emoteEntry.substring(0, colon)
                    val ranges = emoteEntry.substring(colon + 1).split(",")
                    val list = map.getOrPut(emoteId) { mutableListOf() }
                    ranges.forEach { rangeStr ->
                        val dash = rangeStr.indexOf('-')
                        if (dash > 0) {
                            val s = rangeStr.substring(0, dash).toIntOrNull()
                            val e = rangeStr.substring(dash + 1).toIntOrNull()
                            if (s != null && e != null) {
                                list.add(s..e)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed parsing Twitch emote tag: $emotesTag", e)
        }
        return map
    }
}
