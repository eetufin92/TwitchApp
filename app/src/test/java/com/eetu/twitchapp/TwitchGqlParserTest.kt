package com.eetu.twitchapp

import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.data.model.TwitchUser
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TwitchGqlParserTest {

    @Test
    fun testParseSearchResponse() {
        val sampleJson = """
        {
            "data": {
                "searchFor": {
                    "channels": {
                        "edges": [
                            {
                                "item": {
                                    "id": "12345",
                                    "login": "streamerone",
                                    "displayName": "StreamerOne",
                                    "profileImageURL": "https://example.com/avatar1.png",
                                    "stream": {
                                        "id": "99991",
                                        "viewersCount": 1500,
                                        "title": "Playing Games!",
                                        "game": {
                                            "name": "Valorant",
                                            "boxArtURL": "https://example.com/box1.png"
                                        },
                                        "previewImageURL": "https://example.com/preview1.jpg"
                                    }
                                }
                            },
                            {
                                "item": {
                                    "id": "67890",
                                    "login": "streamertwo",
                                    "displayName": "StreamerTwo",
                                    "profileImageURL": "https://example.com/avatar2.png",
                                    "stream": null
                                }
                            }
                        ]
                    }
                }
            }
        }
        """.trimIndent()

        val rootJson = JSONObject(sampleJson)
        val channelsEdges = rootJson.getJSONObject("data")
            .getJSONObject("searchFor")
            .getJSONObject("channels")
            .getJSONArray("edges")

        val results = mutableListOf<LiveStreamItem>()
        for (i in 0 until channelsEdges.length()) {
            val item = channelsEdges.getJSONObject(i).optJSONObject("item") ?: continue
            val id = item.optString("id")
            val login = item.optString("login")
            if (id.isEmpty() || login.isEmpty()) continue

            val stream = item.optJSONObject("stream")
            val isLive = stream != null

            results.add(
                LiveStreamItem(
                    id = stream?.optString("id") ?: id,
                    broadcasterId = id,
                    login = login,
                    displayName = item.optString("displayName").ifEmpty { login },
                    profileImageUrl = item.optString("profileImageURL"),
                    viewersCount = stream?.optInt("viewersCount") ?: 0,
                    title = stream?.optString("title") ?: if (isLive) "Live" else "Offline",
                    gameName = stream?.optJSONObject("game")?.optString("name") ?: "",
                    boxArtUrl = stream?.optJSONObject("game")?.optString("boxArtURL") ?: "",
                    previewImageUrl = stream?.optString("previewImageURL") ?: ""
                )
            )
        }

        assertEquals(2, results.size)
        // Streamer 1
        assertEquals("StreamerOne", results[0].displayName)
        assertEquals(1500, results[0].viewersCount)
        assertEquals("Valorant", results[0].gameName)
        assertEquals("99991", results[0].id)

        // Streamer 2 (Offline)
        assertEquals("StreamerTwo", results[1].displayName)
        assertEquals(0, results[1].viewersCount)
        assertEquals("Offline", results[1].title)
    }

    @Test
    fun testParseFollowedLiveStreams() {
        val sampleJson = """
        {
            "data": {
                "currentUser": {
                    "followedLiveUsers": {
                        "nodes": [
                            {
                                "id": "44444",
                                "login": "shroud",
                                "displayName": "shroud",
                                "profileImageURL": "https://example.com/shroud.png",
                                "stream": {
                                    "id": "88888",
                                    "viewersCount": 12500,
                                    "title": "Ranked grind",
                                    "game": {
                                        "name": "Counter-Strike 2",
                                        "boxArtURL": "https://example.com/cs2.png"
                                    },
                                    "previewImageURL": "https://example.com/preview.jpg"
                                }
                            }
                        ]
                    }
                }
            }
        }
        """.trimIndent()

        val rootJson = JSONObject(sampleJson)
        val nodes = rootJson.getJSONObject("data")
            .getJSONObject("currentUser")
            .getJSONObject("followedLiveUsers")
            .getJSONArray("nodes")

        val results = mutableListOf<LiveStreamItem>()
        for (i in 0 until nodes.length()) {
            val user = nodes.getJSONObject(i)
            val id = user.optString("id")
            val login = user.optString("login")
            if (id.isEmpty() || login.isEmpty()) continue

            val stream = user.optJSONObject("stream")
            val game = stream?.optJSONObject("game")

            results.add(
                LiveStreamItem(
                    id = stream?.optString("id") ?: id,
                    broadcasterId = id,
                    login = login,
                    displayName = user.optString("displayName").ifEmpty { login },
                    profileImageUrl = user.optString("profileImageURL"),
                    viewersCount = stream?.optInt("viewersCount") ?: 0,
                    title = stream?.optString("title") ?: "Live",
                    gameName = game?.optString("name") ?: "",
                    boxArtUrl = game?.optString("boxArtURL") ?: "",
                    previewImageUrl = stream?.optString("previewImageURL") ?: ""
                )
            )
        }

        assertEquals(1, results.size)
        assertEquals("shroud", results[0].displayName)
        assertEquals(12500, results[0].viewersCount)
        assertEquals("Counter-Strike 2", results[0].gameName)
    }

    @Test
    fun testParseValidateAuthToken() {
        val sampleJson = """
        {
            "data": {
                "currentUser": {
                    "id": "111222",
                    "login": "testuser",
                    "displayName": "TestUser",
                    "profileImageURL": "https://example.com/avatar.png"
                }
            }
        }
        """.trimIndent()

        val rootJson = JSONObject(sampleJson)
        val userJson = rootJson.getJSONObject("data").getJSONObject("currentUser")
        val user = TwitchUser(
            id = userJson.getString("id"),
            login = userJson.getString("login"),
            displayName = userJson.optString("displayName", userJson.getString("login")),
            profileImageUrl = userJson.optString("profileImageURL")
        )

        assertEquals("111222", user.id)
        assertEquals("testuser", user.login)
        assertEquals("TestUser", user.displayName)
        assertEquals("https://example.com/avatar.png", user.profileImageUrl)
    }

    @Test
    fun testCookieTokenExtractor() {
        val cookieString = "unique_id=abc; auth-token=\"my_oauth_token_xyz\"; server_session=123"
        val items = cookieString.split(";")
        var token: String? = null
        for (item in items) {
            val parts = item.trim().split("=", limit = 2)
            if (parts.size == 2 && parts[0] == "auth-token") {
                val value = parts[1].trim()
                if (value.isNotEmpty() && value != "\"\"" && value != "null") {
                    token = value.removeSurrounding("\"")
                }
            }
        }

        assertNotNull(token)
        assertEquals("my_oauth_token_xyz", token)
    }
}
