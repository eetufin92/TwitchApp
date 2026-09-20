package com.eetu.twitchapp.data.network

import android.net.Uri
import android.util.Log
import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.data.model.PlaybackTokenResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

class TwitchGqlClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    companion object {
        private const val TAG = "TwitchGqlClient"
        private const val GQL_URL = "https://gql.twitch.tv/gql"
        private const val CLIENT_ID = "kimne78kx3ncx6brgo4mv6wki5h1ko"
        private const val USHER_BASE_URL = "https://usher.ttvnw.net/api/channel/hls/"
        private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }

    /**
     * Obtains the streamPlaybackAccessToken and constructs the Usher master .m3u8 playlist URL.
     */
    suspend fun getStreamPlaybackAccessToken(channelName: String): PlaybackTokenResult? = withContext(Dispatchers.IO) {
        val cleanChannel = channelName.trim().lowercase()
        if (cleanChannel.isEmpty()) return@withContext null

        try {
            val payload = JSONObject().apply {
                put("operationName", "PlaybackAccessToken")
                put("variables", JSONObject().apply {
                    put("isLive", true)
                    put("login", cleanChannel)
                    put("isVod", false)
                    put("vodID", "")
                    put("playerType", "site")
                    put("platform", "web")
                })
                put("extensions", JSONObject().apply {
                    put("persistedQuery", JSONObject().apply {
                        put("version", 1)
                        put("sha256Hash", "ed230aa1e33e07eebb8928504583da78a5173989fadfb1ac94be06a04f3cdbe9")
                    })
                })
            }

            val request = Request.Builder()
                .url(GQL_URL)
                .addHeader("Client-ID", CLIENT_ID)
                .addHeader("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.e(TAG, "GQL PlaybackAccessToken failed HTTP ${response.code}")
                    return@withContext null
                }

                val bodyStr = response.body?.string() ?: return@withContext null
                val rootJson = JSONObject(bodyStr)
                val tokenObj = rootJson.optJSONObject("data")
                    ?.optJSONObject("streamPlaybackAccessToken") ?: return@withContext null

                val tokenValue = tokenObj.optString("value")
                val signature = tokenObj.optString("signature")

                if (tokenValue.isEmpty() || signature.isEmpty()) {
                    Log.e(TAG, "Empty token or signature received")
                    return@withContext null
                }

                // Construct Usher master playlist URL
                val masterPlaylistUrl = Uri.parse("$USHER_BASE_URL$cleanChannel.m3u8").buildUpon()
                    .appendQueryParameter("token", tokenValue)
                    .appendQueryParameter("sig", signature)
                    .appendQueryParameter("allow_source", "true")
                    .appendQueryParameter("allow_audio_only", "true")
                    .appendQueryParameter("fast_bread", "true")
                    .appendQueryParameter("player_backend", "mediaplayer")
                    .build()
                    .toString()

                PlaybackTokenResult(
                    token = tokenValue,
                    signature = signature,
                    masterPlaylistUrl = masterPlaylistUrl
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Exception getting playback access token for $channelName", e)
            null
        }
    }

    /**
     * Queries top live streams for discovery on the home page.
     */
    suspend fun getTopStreams(limit: Int = 25): List<LiveStreamItem> = withContext(Dispatchers.IO) {
        val query = """
            query {
                streams(first: $limit) {
                    edges {
                        node {
                            id
                            broadcaster {
                                id
                                login
                                displayName
                                profileImageURL(width: 70)
                            }
                            viewersCount
                            title
                            game {
                                name
                                boxArtURL(width: 144, height: 192)
                            }
                            previewImageURL(width: 640, height: 360)
                        }
                    }
                }
            }
        """.trimIndent()

        executeStreamQuery(query)
    }

    /**
     * Search live channels matching a search term.
     */
    suspend fun searchChannels(searchTerm: String, limit: Int = 20): List<LiveStreamItem> = withContext(Dispatchers.IO) {
        val cleanSearch = searchTerm.trim().replace("\"", "\\\"")
        if (cleanSearch.isEmpty()) return@withContext getTopStreams(limit)

        val query = """
            query {
                searchFor(query: "$cleanSearch", target: CHANNELS, first: $limit) {
                    channels {
                        edges {
                            item {
                                id
                                login
                                displayName
                                profileImageURL(width: 70)
                                stream {
                                    id
                                    viewersCount
                                    title
                                    game {
                                        name
                                        boxArtURL(width: 144, height: 192)
                                    }
                                    previewImageURL(width: 640, height: 360)
                                }
                            }
                        }
                    }
                }
            }
        """.trimIndent()

        try {
            val payload = JSONObject().apply { put("query", query) }
            val request = Request.Builder()
                .url(GQL_URL)
                .addHeader("Client-ID", CLIENT_ID)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext emptyList()
                val bodyStr = response.body?.string() ?: return@withContext emptyList()
                val rootJson = JSONObject(bodyStr)
                val channelsEdges = rootJson.optJSONObject("data")
                    ?.optJSONObject("searchFor")
                    ?.optJSONObject("channels")
                    ?.optJSONArray("edges") ?: return@withContext emptyList()

                val results = mutableListOf<LiveStreamItem>()
                for (i in 0 until channelsEdges.length()) {
                    val item = channelsEdges.getJSONObject(i).optJSONObject("item") ?: continue
                    val stream = item.optJSONObject("stream")
                    val isLive = stream != null

                    results.add(
                        LiveStreamItem(
                            id = stream?.optString("id") ?: item.optString("id"),
                            broadcasterId = item.optString("id"),
                            login = item.optString("login"),
                            displayName = item.optString("displayName").ifEmpty { item.optString("login") },
                            profileImageUrl = item.optString("profileImageURL"),
                            viewersCount = stream?.optInt("viewersCount") ?: 0,
                            title = stream?.optString("title") ?: if (isLive) "Live" else "Offline",
                            gameName = stream?.optJSONObject("game")?.optString("name") ?: "",
                            boxArtUrl = stream?.optJSONObject("game")?.optString("boxArtURL") ?: "",
                            previewImageUrl = stream?.optString("previewImageURL") ?: ""
                        )
                    )
                }
                results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Search channels exception for $searchTerm", e)
            emptyList()
        }
    }

    /**
     * Get details for a specific channel/user login.
     */
    suspend fun getChannelDetails(channelName: String): LiveStreamItem? = withContext(Dispatchers.IO) {
        val clean = channelName.trim().lowercase()
        if (clean.isEmpty()) return@withContext null

        val query = """
            query {
                user(login: "$clean") {
                    id
                    login
                    displayName
                    profileImageURL(width: 150)
                    stream {
                        id
                        viewersCount
                        title
                        game {
                            name
                            boxArtURL(width: 144, height: 192)
                        }
                        previewImageURL(width: 640, height: 360)
                    }
                }
            }
        """.trimIndent()

        try {
            val payload = JSONObject().apply { put("query", query) }
            val request = Request.Builder()
                .url(GQL_URL)
                .addHeader("Client-ID", CLIENT_ID)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val bodyStr = response.body?.string() ?: return@withContext null
                val rootJson = JSONObject(bodyStr)
                val user = rootJson.optJSONObject("data")?.optJSONObject("user") ?: return@withContext null
                val stream = user.optJSONObject("stream")

                LiveStreamItem(
                    id = stream?.optString("id") ?: user.optString("id"),
                    broadcasterId = user.optString("id"),
                    login = user.optString("login"),
                    displayName = user.optString("displayName").ifEmpty { user.optString("login") },
                    profileImageUrl = user.optString("profileImageURL"),
                    viewersCount = stream?.optInt("viewersCount") ?: 0,
                    title = stream?.optString("title") ?: "Offline",
                    gameName = stream?.optJSONObject("game")?.optString("name") ?: "",
                    boxArtUrl = stream?.optJSONObject("game")?.optString("boxArtURL") ?: "",
                    previewImageUrl = stream?.optString("previewImageURL") ?: ""
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Get channel details exception for $channelName", e)
            null
        }
    }

    private fun executeStreamQuery(query: String): List<LiveStreamItem> {
        try {
            val payload = JSONObject().apply { put("query", query) }
            val request = Request.Builder()
                .url(GQL_URL)
                .addHeader("Client-ID", CLIENT_ID)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return emptyList()
                val bodyStr = response.body?.string() ?: return emptyList()
                val rootJson = JSONObject(bodyStr)
                val edges = rootJson.optJSONObject("data")
                    ?.optJSONObject("streams")
                    ?.optJSONArray("edges") ?: return emptyList()

                val results = mutableListOf<LiveStreamItem>()
                for (i in 0 until edges.length()) {
                    val node = edges.getJSONObject(i).optJSONObject("node") ?: continue
                    val broadcaster = node.optJSONObject("broadcaster") ?: continue
                    val game = node.optJSONObject("game")

                    results.add(
                        LiveStreamItem(
                            id = node.optString("id"),
                            broadcasterId = broadcaster.optString("id"),
                            login = broadcaster.optString("login"),
                            displayName = broadcaster.optString("displayName").ifEmpty { broadcaster.optString("login") },
                            profileImageUrl = broadcaster.optString("profileImageURL"),
                            viewersCount = node.optInt("viewersCount"),
                            title = node.optString("title"),
                            gameName = game?.optString("name") ?: "",
                            boxArtUrl = game?.optString("boxArtURL") ?: "",
                            previewImageUrl = node.optString("previewImageURL")
                        )
                    )
                }
                return results
            }
        } catch (e: Exception) {
            Log.e(TAG, "Execute stream query exception", e)
            return emptyList()
        }
    }
}
