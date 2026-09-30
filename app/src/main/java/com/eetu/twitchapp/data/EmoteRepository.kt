package com.eetu.twitchapp.data

import android.content.Context
import android.util.Log
import com.eetu.twitchapp.data.model.EmoteItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class EmoteRepository(private val context: Context? = null) {
    private val client = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .build()

    // In-memory caches
    private val channelStructuredCache = ConcurrentHashMap<String, List<EmoteItem>>()
    private val channelEmoteCache = ConcurrentHashMap<String, Map<String, String>>()
    private val globalEmoteListCache = mutableListOf<EmoteItem>()
    private val channelIdCache = ConcurrentHashMap<String, String>()

    companion object {
        private const val TAG = "EmoteRepository"
        private const val CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
        private const val GQL_URL = "https://gql.twitch.tv/gql"
        private const val CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

        // Standard Twitch global emotes
        val STANDARD_TWITCH_GLOBALS = listOf(
            "Kappa" to "25",
            "PogChamp" to "305954156",
            "LUL" to "425618",
            "BibleThump" to "86",
            "Kreygasm" to "41",
            "4Head" to "354",
            "HeyGuys" to "30259",
            "DansGame" to "33",
            "SMOrc" to "52",
            "WutFace" to "28087",
            "ResidentSleeper" to "245",
            "CoolCat" to "58127",
            "TriHard" to "120232",
            "NotLikeThis" to "58051",
            "VoHiYo" to "81274",
            "BabyRage" to "22639",
            "SeemsGood" to "64138",
            "FailFish" to "360",
            "GivePLZ" to "112291",
            "TakeNRG" to "112292",
            "SwiftRage" to "34",
            "JTCash" to "537840",
            "bleedPurple" to "62835"
        )
    }

    suspend fun getStructuredChannelEmotes(
        channelName: String,
        enable7tv: Boolean = true,
        enableBttv: Boolean = true,
        enableFfz: Boolean = true
    ): List<EmoteItem> = withContext(Dispatchers.IO) {
        val cleanName = channelName.lowercase().trim()
        if (cleanName.isEmpty()) return@withContext emptyList()

        // 1. In-memory cache hit
        channelStructuredCache[cleanName]?.let { return@withContext it }

        // 2. Persistent Disk Cache hit
        val diskCacheResult = loadFromDiskCache(cleanName)
        if (diskCacheResult != null) {
            channelStructuredCache[cleanName] = diskCacheResult.emotes
            channelEmoteCache[cleanName] = diskCacheResult.emotes.associate { it.name to it.url }

            // If disk cache is still fresh, return immediately
            if (System.currentTimeMillis() - diskCacheResult.timestamp < CACHE_TTL_MS) {
                return@withContext diskCacheResult.emotes
            }
        }

        // 3. Network fetch
        val resultList = mutableListOf<EmoteItem>()

        // 0. Include Twitch Global Emotes
        for ((name, id) in STANDARD_TWITCH_GLOBALS) {
            resultList.add(
                EmoteItem(
                    name = name,
                    url = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0",
                    highResUrl = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/3.0",
                    source = "Twitch"
                )
            )
        }

        // 1. Fetch Channel Twitch Subscriber Emotes via GQL
        try {
            val twitchChannelEmotes = fetchTwitchChannelEmotes(cleanName)
            resultList.addAll(twitchChannelEmotes)
        } catch (e: Exception) {
            Log.w(TAG, "Failed fetching Twitch channel emotes for $cleanName", e)
        }

        // 2. Include Global 7TV, BTTV, FFZ emotes
        resultList.addAll(getGlobalEmoteItems(enable7tv, enableBttv, enableFfz))

        // 3. Fetch FFZ Room emotes
        if (enableFfz) {
            try {
                val ffzEmotes = fetchFfzRoomEmoteItems(cleanName)
                resultList.addAll(ffzEmotes)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to fetch FFZ emotes for $cleanName", e)
            }
        }

        // 4. Fetch 7TV and BTTV channel emotes using Twitch User ID
        val userId = getTwitchUserId(cleanName)
        if (!userId.isNullOrEmpty()) {
            if (enable7tv) {
                try {
                    val sevenTvEmotes = fetch7tvChannelEmoteItems(userId)
                    resultList.addAll(sevenTvEmotes)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch 7TV emotes for $cleanName ($userId)", e)
                }
            }

            if (enableBttv) {
                try {
                    val bttvEmotes = fetchBttvChannelEmoteItems(userId)
                    resultList.addAll(bttvEmotes)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to fetch BTTV emotes for $cleanName ($userId)", e)
                }
            }
        }

        // Deduplicate by name, preferring channel specific emotes over global ones
        val deduplicated = resultList.distinctBy { it.name }

        channelStructuredCache[cleanName] = deduplicated
        channelEmoteCache[cleanName] = deduplicated.associate { it.name to it.url }

        // Save to persistent disk cache
        saveToDiskCache(cleanName, deduplicated)

        Log.d(TAG, "Loaded ${deduplicated.size} structured emotes for channel $cleanName")
        deduplicated
    }

    suspend fun getChannelEmotes(
        channelName: String,
        enable7tv: Boolean = true,
        enableBttv: Boolean = true,
        enableFfz: Boolean = true
    ): Map<String, String> = withContext(Dispatchers.IO) {
        val cleanName = channelName.lowercase().trim()
        if (cleanName.isEmpty()) return@withContext emptyMap()

        channelEmoteCache[cleanName]?.let { return@withContext it }

        val structured = getStructuredChannelEmotes(cleanName, enable7tv, enableBttv, enableFfz)
        val map = structured.associate { it.name to it.url }
        channelEmoteCache[cleanName] = map
        map
    }

    private fun fetchTwitchChannelEmotes(channelName: String): List<EmoteItem> {
        val list = mutableListOf<EmoteItem>()
        val query = """
            query {
                user(login: "$channelName") {
                    subscriptionProducts {
                        emotes {
                            id
                            token
                        }
                    }
                }
            }
        """.trimIndent()

        val payload = JSONObject().apply { put("query", query) }
        val req = Request.Builder()
            .url(GQL_URL)
            .addHeader("Client-ID", CLIENT_ID)
            .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
            .build()

        client.newCall(req).execute().use { resp ->
            if (resp.isSuccessful) {
                val body = resp.body?.string() ?: return list
                val json = JSONObject(body)
                val user = json.optJSONObject("data")?.optJSONObject("user") ?: return list
                val prods = user.optJSONArray("subscriptionProducts") ?: return list

                for (i in 0 until prods.length()) {
                    val prod = prods.getJSONObject(i)
                    val emotes = prod.optJSONArray("emotes") ?: continue
                    for (j in 0 until emotes.length()) {
                        val emo = emotes.getJSONObject(j)
                        val id = emo.optString("id")
                        val token = emo.optString("token")
                        if (id.isNotEmpty() && token.isNotEmpty()) {
                            list.add(
                                EmoteItem(
                                    name = token,
                                    url = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/2.0",
                                    highResUrl = "https://static-cdn.jtvnw.net/emoticons/v2/$id/default/dark/3.0",
                                    source = "Twitch"
                                )
                            )
                        }
                    }
                }
            }
        }
        return list
    }

    private fun getGlobalEmoteItems(
        enable7tv: Boolean = true,
        enableBttv: Boolean = true,
        enableFfz: Boolean = true
    ): List<EmoteItem> {
        synchronized(globalEmoteListCache) {
            if (globalEmoteListCache.isNotEmpty()) return globalEmoteListCache

            // 1. BTTV Global
            if (enableBttv) {
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
                                    globalEmoteListCache.add(
                                        EmoteItem(
                                            name = code,
                                            url = "https://cdn.betterttv.net/emote/$id/2x",
                                            highResUrl = "https://cdn.betterttv.net/emote/$id/3x",
                                            source = "BTTV"
                                        )
                                    )
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "BTTV global emotes failed: ${e.message}")
                }
            }

            // 2. 7TV Global
            if (enable7tv) {
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
                                        globalEmoteListCache.add(
                                            EmoteItem(
                                                name = name,
                                                url = "https://cdn.7tv.app/emote/$id/2x.webp",
                                                highResUrl = "https://cdn.7tv.app/emote/$id/4x.webp",
                                                source = "7TV"
                                            )
                                        )
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "7TV global emotes failed: ${e.message}")
                }
            }

            // 3. FFZ Global
            if (enableFfz) {
                try {
                    val req = Request.Builder()
                        .url("https://api.frankerfacez.com/v1/set/global")
                        .header("User-Agent", "TwitchApp-Android")
                        .build()
                    client.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string() ?: ""
                            val obj = JSONObject(body)
                            val sets = obj.optJSONObject("sets")
                            if (sets != null) {
                                val keys = sets.keys()
                                while (keys.hasNext()) {
                                    val key = keys.next()
                                    val setObj = sets.getJSONObject(key)
                                    val emoticons = setObj.optJSONArray("emoticons") ?: continue
                                    for (i in 0 until emoticons.length()) {
                                        val emo = emoticons.getJSONObject(i)
                                        val name = emo.optString("name")
                                        val urls = emo.optJSONObject("urls")
                                        val url = urls?.optString("2") ?: urls?.optString("1")
                                        val highRes = urls?.optString("4") ?: url
                                        if (name.isNotEmpty() && !url.isNullOrEmpty()) {
                                            val fullUrl = if (url.startsWith("//")) "https:$url" else url
                                            val fullHighRes = if (!highRes.isNullOrEmpty() && highRes.startsWith("//")) "https:$highRes" else (highRes ?: fullUrl)
                                            globalEmoteListCache.add(
                                                EmoteItem(
                                                    name = name,
                                                    url = fullUrl,
                                                    highResUrl = fullHighRes,
                                                    source = "FFZ"
                                                )
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "FFZ global emotes failed: ${e.message}")
                }
            }

            return globalEmoteListCache
        }
    }

    private fun getTwitchUserId(channelName: String): String? {
        channelIdCache[channelName]?.let { return it }

        // Primary: IVR API
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
            Log.d(TAG, "IVR lookup failed, trying fallback: ${e.message}")
        }

        // Fallback: decapi.me
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
            Log.d(TAG, "Decapi lookup failed: ${e.message}")
        }

        return null
    }

    private fun fetch7tvChannelEmoteItems(userId: String): List<EmoteItem> {
        val list = mutableListOf<EmoteItem>()
        val request = Request.Builder()
            .url("https://7tv.io/v3/users/twitch/$userId")
            .header("User-Agent", "TwitchApp-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return list
                val json = JSONObject(body)
                val emoteSet = json.optJSONObject("emote_set") ?: return list
                val emotes = emoteSet.optJSONArray("emotes") ?: return list

                for (i in 0 until emotes.length()) {
                    val emote = emotes.getJSONObject(i)
                    val name = emote.getString("name")
                    val id = emote.getString("id")
                    list.add(
                        EmoteItem(
                            name = name,
                            url = "https://cdn.7tv.app/emote/$id/2x.webp",
                            highResUrl = "https://cdn.7tv.app/emote/$id/4x.webp",
                            source = "7TV"
                        )
                    )
                }
            }
        }
        return list
    }

    private fun fetchBttvChannelEmoteItems(userId: String): List<EmoteItem> {
        val list = mutableListOf<EmoteItem>()
        val request = Request.Builder()
            .url("https://api.betterttv.net/3/cached/users/twitch/$userId")
            .header("User-Agent", "TwitchApp-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return list
                val json = JSONObject(body)

                fun parseList(arr: JSONArray?) {
                    if (arr == null) return
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val code = obj.getString("code")
                        val id = obj.getString("id")
                        list.add(
                            EmoteItem(
                                name = code,
                                url = "https://cdn.betterttv.net/emote/$id/2x",
                                highResUrl = "https://cdn.betterttv.net/emote/$id/3x",
                                source = "BTTV"
                            )
                        )
                    }
                }

                parseList(json.optJSONArray("channelEmotes"))
                parseList(json.optJSONArray("sharedEmotes"))
            }
        }
        return list
    }

    private fun fetchFfzRoomEmoteItems(channelName: String): List<EmoteItem> {
        val list = mutableListOf<EmoteItem>()
        val request = Request.Builder()
            .url("https://api.frankerfacez.com/v1/room/$channelName")
            .header("User-Agent", "TwitchApp-Android")
            .build()

        client.newCall(request).execute().use { response ->
            if (response.isSuccessful) {
                val body = response.body?.string() ?: return list
                val json = JSONObject(body)
                val sets = json.optJSONObject("sets") ?: return list
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
                        val highRes = urls?.optString("4") ?: url
                        if (!url.isNullOrEmpty()) {
                            val fullUrl = if (url.startsWith("//")) "https:$url" else url
                            val fullHighRes = if (!highRes.isNullOrEmpty() && highRes.startsWith("//")) "https:$highRes" else (highRes ?: fullUrl)
                            list.add(
                                EmoteItem(
                                    name = name,
                                    url = fullUrl,
                                    highResUrl = fullHighRes,
                                    source = "FFZ"
                                )
                            )
                        }
                    }
                }
            }
        }
        return list
    }

    // ---------------- Disk Cache Helpers ----------------

    private data class DiskCacheEntry(
        val timestamp: Long,
        val emotes: List<EmoteItem>
    )

    private fun getCacheFile(channelName: String): File? {
        val ctx = context ?: return null
        val dir = File(ctx.cacheDir, "emotes")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "${channelName.lowercase()}.json")
    }

    private fun loadFromDiskCache(channelName: String): DiskCacheEntry? {
        val file = getCacheFile(channelName) ?: return null
        if (!file.exists()) return null

        try {
            val content = file.readText()
            val json = JSONObject(content)
            val ts = json.optLong("timestamp", 0L)
            val arr = json.optJSONArray("emotes") ?: return null

            val list = mutableListOf<EmoteItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    EmoteItem(
                        name = obj.getString("name"),
                        url = obj.getString("url"),
                        highResUrl = obj.optString("highResUrl", obj.getString("url")),
                        source = obj.optString("source", "Twitch")
                    )
                )
            }
            return DiskCacheEntry(ts, list)
        } catch (e: Exception) {
            Log.w(TAG, "Failed reading emote disk cache for $channelName", e)
            return null
        }
    }

    private fun saveToDiskCache(channelName: String, emotes: List<EmoteItem>) {
        val file = getCacheFile(channelName) ?: return
        try {
            val json = JSONObject()
            json.put("timestamp", System.currentTimeMillis())

            val arr = JSONArray()
            for (item in emotes) {
                val obj = JSONObject()
                obj.put("name", item.name)
                obj.put("url", item.url)
                obj.put("highResUrl", item.highResUrl)
                obj.put("source", item.source)
                arr.put(obj)
            }
            json.put("emotes", arr)
            file.writeText(json.toString())
        } catch (e: Exception) {
            Log.w(TAG, "Failed writing emote disk cache for $channelName", e)
        }
    }
}
