package com.eetu.twitchapp.data

import android.content.Context
import android.content.SharedPreferences

class TwitchSettingsManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("twitch_app_prefs", Context.MODE_PRIVATE)

    companion object {
        const val KEY_AUTO_MUTE_ADS = "auto_mute_ads"
        const val KEY_SHOW_AD_OVERLAY = "show_ad_overlay"
        const val KEY_ENABLE_7TV = "enable_7tv"
        const val KEY_ENABLE_BTTV = "enable_bttv"
        const val KEY_ENABLE_FFZ = "enable_ffz"
        const val KEY_CHAT_OPACITY = "chat_opacity"
        const val KEY_DESKTOP_MODE = "desktop_mode"
        const val KEY_USER_AGENT = "user_agent"
        const val KEY_FLOATING_CHAT_ENABLED = "floating_chat_enabled"
        const val KEY_LOW_LATENCY = "low_latency"
        const val KEY_PIP_ENABLED = "pip_enabled"
        const val KEY_BACKGROUND_AUDIO = "background_audio"
        const val KEY_PREFERRED_QUALITY = "preferred_quality"
        const val KEY_AUDIO_ONLY = "audio_only"
        const val KEY_SIDE_CHAT_VISIBLE = "side_chat_visible"
    }

    fun isAutoMuteAds(): Boolean = prefs.getBoolean(KEY_AUTO_MUTE_ADS, true)
    fun setAutoMuteAds(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUTO_MUTE_ADS, enabled).apply()

    fun isShowAdOverlay(): Boolean = prefs.getBoolean(KEY_SHOW_AD_OVERLAY, true)
    fun setShowAdOverlay(enabled: Boolean) = prefs.edit().putBoolean(KEY_SHOW_AD_OVERLAY, enabled).apply()

    fun is7tvEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_7TV, true)
    fun set7tvEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_7TV, enabled).apply()

    fun isBttvEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_BTTV, true)
    fun setBttvEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_BTTV, enabled).apply()

    fun isFfzEnabled(): Boolean = prefs.getBoolean(KEY_ENABLE_FFZ, true)
    fun setFfzEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_ENABLE_FFZ, enabled).apply()

    fun getChatOpacity(): Float = prefs.getFloat(KEY_CHAT_OPACITY, 0.9f)
    fun setChatOpacity(opacity: Float) = prefs.edit().putFloat(KEY_CHAT_OPACITY, opacity).apply()

    fun isDesktopMode(): Boolean = prefs.getBoolean(KEY_DESKTOP_MODE, false)
    fun setDesktopMode(enabled: Boolean) = prefs.edit().putBoolean(KEY_DESKTOP_MODE, enabled).apply()

    fun getUserAgent(): String = prefs.getString(KEY_USER_AGENT, "") ?: ""
    fun setUserAgent(ua: String) = prefs.edit().putString(KEY_USER_AGENT, ua).apply()

    fun isFloatingChatEnabled(): Boolean = prefs.getBoolean(KEY_FLOATING_CHAT_ENABLED, true)
    fun setFloatingChatEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_FLOATING_CHAT_ENABLED, enabled).apply()

    fun isLowLatency(): Boolean = prefs.getBoolean(KEY_LOW_LATENCY, true)
    fun setLowLatency(enabled: Boolean) = prefs.edit().putBoolean(KEY_LOW_LATENCY, enabled).apply()

    fun isPipEnabled(): Boolean = prefs.getBoolean(KEY_PIP_ENABLED, true)
    fun setPipEnabled(enabled: Boolean) = prefs.edit().putBoolean(KEY_PIP_ENABLED, enabled).apply()

    fun isBackgroundAudio(): Boolean = prefs.getBoolean(KEY_BACKGROUND_AUDIO, true)
    fun setBackgroundAudio(enabled: Boolean) = prefs.edit().putBoolean(KEY_BACKGROUND_AUDIO, enabled).apply()

    fun getPreferredQuality(): String = prefs.getString(KEY_PREFERRED_QUALITY, "auto") ?: "auto"
    fun setPreferredQuality(quality: String) = prefs.edit().putString(KEY_PREFERRED_QUALITY, quality).apply()

    fun isAudioOnly(): Boolean = prefs.getBoolean(KEY_AUDIO_ONLY, false)
    fun setAudioOnly(enabled: Boolean) = prefs.edit().putBoolean(KEY_AUDIO_ONLY, enabled).apply()

    fun isSideChatVisible(): Boolean = prefs.getBoolean(KEY_SIDE_CHAT_VISIBLE, false)
    fun setSideChatVisible(visible: Boolean) = prefs.edit().putBoolean(KEY_SIDE_CHAT_VISIBLE, visible).apply()
}
