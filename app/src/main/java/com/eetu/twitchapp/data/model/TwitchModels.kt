package com.eetu.twitchapp.data.model

data class LiveStreamItem(
    val id: String,
    val broadcasterId: String,
    val login: String,
    val displayName: String,
    val profileImageUrl: String,
    val viewersCount: Int,
    val title: String,
    val gameName: String,
    val boxArtUrl: String = "",
    val previewImageUrl: String = ""
)

data class PlaybackTokenResult(
    val token: String,
    val signature: String,
    val masterPlaylistUrl: String
)

data class ChatBadge(
    val name: String,
    val version: String
)

data class ChatMessage(
    val id: String,
    val user: String,
    val displayName: String,
    val color: String,
    val badges: List<ChatBadge> = emptyList(),
    val text: String,
    val twitchEmotes: Map<String, List<IntRange>> = emptyMap(), // emoteId -> ranges
    val timestamp: Long = System.currentTimeMillis()
)
