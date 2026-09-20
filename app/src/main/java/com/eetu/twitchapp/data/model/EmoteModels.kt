package com.eetu.twitchapp.data.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

data class EmoteItem(
    val name: String,
    val url: String,
    val source: String // "7TV", "BTTV", "FFZ"
)

// 7TV models
@JsonClass(generateAdapter = true)
data class SevenTvEmoteSet(
    val id: String?,
    val name: String?,
    val emotes: List<SevenTvEmote>?
)

@JsonClass(generateAdapter = true)
data class SevenTvEmote(
    val id: String,
    val name: String,
    val data: SevenTvEmoteData?
)

@JsonClass(generateAdapter = true)
data class SevenTvEmoteData(
    val host: SevenTvHost?
)

@JsonClass(generateAdapter = true)
data class SevenTvHost(
    val url: String?,
    val files: List<SevenTvFile>?
)

@JsonClass(generateAdapter = true)
data class SevenTvFile(
    val name: String?,
    val format: String?
)

@JsonClass(generateAdapter = true)
data class SevenTvUserResponse(
    val id: String?,
    val username: String?,
    @Json(name = "emote_set") val emoteSet: SevenTvEmoteSet?
)

// BTTV models
@JsonClass(generateAdapter = true)
data class BttvEmote(
    val id: String,
    val code: String,
    val imageType: String?
)

@JsonClass(generateAdapter = true)
data class BttvUserResponse(
    val channelEmotes: List<BttvEmote>?,
    val sharedEmotes: List<BttvEmote>?
)

// FFZ models
@JsonClass(generateAdapter = true)
data class FfzRoomResponse(
    val sets: Map<String, FfzEmoteSet>?
)

@JsonClass(generateAdapter = true)
data class FfzEmoteSet(
    val emoticons: List<FfzEmote>?
)

@JsonClass(generateAdapter = true)
data class FfzEmote(
    val id: Long,
    val name: String,
    val urls: Map<String, String>?
)
