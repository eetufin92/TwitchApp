package com.eetu.twitchapp.data.auth

import android.content.Context
import android.content.SharedPreferences
import android.webkit.CookieManager
import com.eetu.twitchapp.data.model.TwitchUser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class TwitchAuthManager private constructor(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("twitch_auth_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_AUTH_TOKEN = "auth_token"
        private const val KEY_USER_ID = "user_id"
        private const val KEY_LOGIN = "login"
        private const val KEY_DISPLAY_NAME = "display_name"
        private const val KEY_PROFILE_IMAGE_URL = "profile_image_url"

        @Volatile
        private var INSTANCE: TwitchAuthManager? = null

        fun getInstance(context: Context): TwitchAuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TwitchAuthManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }

    private val _currentUser = MutableStateFlow<TwitchUser?>(loadUserFromPrefs())
    val currentUser: StateFlow<TwitchUser?> = _currentUser.asStateFlow()

    private fun loadUserFromPrefs(): TwitchUser? {
        val token = prefs.getString(KEY_AUTH_TOKEN, null)
        val login = prefs.getString(KEY_LOGIN, null)
        val id = prefs.getString(KEY_USER_ID, null)
        if (!token.isNullOrEmpty() && !login.isNullOrEmpty() && !id.isNullOrEmpty()) {
            return TwitchUser(
                id = id,
                login = login,
                displayName = prefs.getString(KEY_DISPLAY_NAME, login) ?: login,
                profileImageUrl = prefs.getString(KEY_PROFILE_IMAGE_URL, "") ?: ""
            )
        }
        return null
    }

    fun isLoggedIn(): Boolean = _currentUser.value != null

    fun getAuthToken(): String? = prefs.getString(KEY_AUTH_TOKEN, null)

    fun saveSession(token: String, user: TwitchUser) {
        prefs.edit()
            .putString(KEY_AUTH_TOKEN, token)
            .putString(KEY_USER_ID, user.id)
            .putString(KEY_LOGIN, user.login)
            .putString(KEY_DISPLAY_NAME, user.displayName)
            .putString(KEY_PROFILE_IMAGE_URL, user.profileImageUrl)
            .apply()

        _currentUser.value = user
    }

    fun logout() {
        prefs.edit().clear().apply()
        _currentUser.value = null

        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.removeAllCookies(null)
            cookieManager.flush()
        } catch (_: Exception) {
        }
    }
}
