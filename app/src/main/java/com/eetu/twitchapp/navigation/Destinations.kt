package com.eetu.twitchapp.navigation

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

@Serializable
sealed interface Destination : NavKey {
    @Serializable
    data class Player(val initialChannel: String = "") : Destination

    @Serializable
    data class MultiStream(val initialChannels: List<String> = emptyList()) : Destination

    @Serializable
    data object Settings : Destination

    @Serializable
    data object EmoteSettings : Destination

    @Serializable
    data object AdSettings : Destination

    @Serializable
    data object AdBlockSettings : Destination

    @Serializable
    data object ChatAppearanceSettings : Destination
}
