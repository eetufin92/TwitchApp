package com.eetu.twitchapp.data

import android.util.Log
import com.eetu.twitchapp.data.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class EmoteRepository {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // In-memory cache for channel -> emote map
    private val channelEmoteCache = ConcurrentHashMap<String, Map<String, String>>()
    private val globalEmoteCache = ConcurrentHashMap<String, String>()
    private val channelIdCache = ConcurrentHashMap<String, String>()

    suspend fun getChannelEmotes(
        channelName: String,
        enable7tv: Boolean = true,
        enableBttv: Boolean = true,
        enableFfz: Boolean = true
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val cleanName = channelName.lowercase().trim()
        if (cleanName.isEmpty()) return@withContext emptyMap()

        channelEmoteCache[cleanName]?.let { return@withContext it }

        val resultMap = mutableMapOf<String, String>()

        // 0. Include global emotes (BTTV & 7TV global sets)
        resultMap.putAll(getGlobalEmotes())

        // 1. Fetch channel ID
        val userId = getTwitchUserId(cleanName)

        // 2. Fetch FFZ Room (works directly with channel name!)
        if (enableFfz) {
            try {
                val ffzEmotes = fetchFfzRoomEmotes(cleanName)
                resultMap.putAll(ffzEmotes)
            } catch (e: Exception) {
                Log.w("EmoteRepository", "Failed to fetch FFZ emotes for $cleanName", e)
            }
        }

        // 3. Fetch 7TV and BTTV if user ID is resolved
        if (!userId.isNullOrEmpty()) {
            if (enable7tv) {
                try {
                    val sevenTvEmotes = fetch7tvChannelEmotes(userId)
                    resultMap.putAll(sevenTvEmotes)
                } catch (e: Exception) {
                    Log.w("EmoteRepository", "Failed to fetch 7TV emotes for $cleanName ($userId)", e)
                }
            }

            if (enableBttv) {
                try {
                    val bttvEmotes = fetchBttvChannelEmotes(userId)
                    resultMap.putAll(bttvEmotes)
                } catch (e: Exception) {
                    Log.w("EmoteRepository", "Failed to fetch BTTV emotes for $cleanName ($userId)", e)
                }
            }
        }

        channelEmoteCache[cleanName] = resultMap
        Log.d("EmoteRepository", "Loaded ${resultMap.size} custom emotes for channel $cleanName")
        resultMap
    }

    private fun getGlobalEmotes(): Map<String, String> {
        if (globalEmoteCache.isNotEmpty()) return globalEmoteCache

        // Fetch BTTV global emotes
        try {
            val req = Request.Builder()
                .url("https://api.betterttv.net/3/cached/emotes/global")
                .header("User-Agent", "TwitchApp-Android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val arr = JSONArray(body)
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val code = obj.optString("code")
                        val id = obj.optString("id")
                        if (code.isNotEmpty() && id.isNotEmpty()) {
                            globalEmoteCache[code] = "https://cdn.betterttv.net/emote/$id/2x"
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("EmoteRepository", "BTTV global emotes failed: ${e.message}")
        }

        // Fetch 7TV global emotes
        try {
            val req = Request.Builder()
                .url("https://7tv.io/v3/emote-sets/global")
                .header("User-Agent", "TwitchApp-Android")
                .build()
            client.newCall(req).execute().use { resp ->
                if (resp.isSuccessful) {
                    val body = resp.body?.string() ?: ""
                    val obj = JSONObject(body)
                    val emotes = obj.optJSONArray("emotes")
                    if (emotes != null) {
                        for (i in 0 until emotes.length()) {
                            val emote = emotes.getJSONObject(i)
                            val name = emote.optString("name")
                            val id = emote.optString("id")
                            if (name.isNotEmpty() && id.isNotEmpty()) {
                                globalEmoteCache[name] = "https://cdn.7tv.app/emote/$id/2x.webp"
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("EmoteRepository", "7TV global emotes failed: ${e.message}")
        }

        return globalEmoteCache
    }

    private fun getTwitchUserId(channelName: String): String? {
        channelIdCache[channelName]?.let { return it }

        // Primary: Use IVR API (login parameter)
        try {
            val request = Request.Builder()
                .url("https://api.ivr.fi/v2/twitch/user?login=$channelName")
                .header("User-Agent", "TwitchApp-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: return null
                    val jsonArray = JSONArray(body)
                    if (jsonArray.length() > 0) {
                        val userObj = jsonArray.getJSONObject(0)
                        val id = userObj.optString("id")
                        if (id.isNotEmpty()) {
                            channelIdCache[channelName] = id
                            return id
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("EmoteRepository", "IVR lookup failed, trying fallback: ${e.message}")
        }

        // Fallback: Use decapi.me
        try {
            val request = Request.Builder()
                .url("https://decapi.me/twitch/id/$channelName")
                .header("User-Agent", "TwitchApp-Android")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val id = response.body?.string()?.trim() ?: ""
                    if (id.isNotEmpty() && id.all { it.isDigit() }) {
                        channelIdCache[channelName] = id
                        return id
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("EmoteRepository", "Decapi lookup failed: ${e.message}")
        }

        return null
    }

    private fun fetch7tvChannelEmotes(userId: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val request = Request.Builder()
            .url("https://7tv.io/v3/users/twitch/$userId")
            .header("User-Agent", "TwitchApp-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return map
                val json = JSONObject(body)
                val emoteSet = json.optJSONObject("emote_set") ?: return map
                val emotes = emoteSet.optJSONArray("emotes") ?: return map

                for (i in 0 until emotes.length()) {
                    val emote = emotes.getJSONObject(i)
                    val name = emote.getString("name")
                    val id = emote.getString("id")
                    // 7TV CDN 2x webp
                    map[name] = "https://cdn.7tv.app/emote/$id/2x.webp"
                }
            }
        }
        return map
    }

    private fun fetchBttvChannelEmotes(userId: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val request = Request.Builder()
            .url("https://api.betterttv.net/3/cached/users/twitch/$userId")
            .header("User-Agent", "TwitchApp-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return map
                val json = JSONObject(body)

                fun parseList(arr: JSONArray?) {
                    if (arr == null) return
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val code = obj.getString("code")
                        val id = obj.getString("id")
                        map[code] = "https://cdn.betterttv.net/emote/$id/2x"
                    }
                }

                parseList(json.optJSONArray("channelEmotes"))
                parseList(json.optJSONArray("sharedEmotes"))
            }
        }
        return map
    }

    private fun fetchFfzRoomEmotes(channelName: String): Map<String, String> {
        val map = mutableMapOf<String, String>()
        val request = Request.Builder()
            .url("https://api.frankerfacez.com/v1/room/$channelName")
            .header("User-Agent", "TwitchApp-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return map
                val json = JSONObject(body)
                val sets = json.optJSONObject("sets") ?: return map
                val keys = sets.keys()

                while (keys.hasNext()) {
                    val setKey = keys.next()
                    val setObj = sets.getJSONObject(setKey)
                    val emoticons = setObj.optJSONArray("emoticons") ?: continue

                    for (i in 0 until emoticons.length()) {
                        val emote = emoticons.getJSONObject(i)
                        val name = emote.getString("name")
                        val urls = emote.optJSONObject("urls")
                        val url = urls?.optString("2") ?: urls?.optString("1")
                        if (!url.isNullOrEmpty()) {
                            val fullUrl = if (url.startsWith("//")) "https:$url" else url
                            map[name] = fullUrl
                        }
                    }
                }
            }
        }
        return map
    }
}
