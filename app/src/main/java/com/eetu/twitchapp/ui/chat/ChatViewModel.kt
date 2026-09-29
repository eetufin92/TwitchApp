package com.eetu.twitchapp.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eetu.twitchapp.data.EmoteRepository
import com.eetu.twitchapp.data.chat.TwitchIrcClient
import com.eetu.twitchapp.data.model.ChatMessage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

import com.eetu.twitchapp.data.model.TwitchUser
import java.util.UUID

class ChatViewModel(
    private val ircClient: TwitchIrcClient = TwitchIrcClient(),
    private val emoteRepository: EmoteRepository = EmoteRepository()
) : ViewModel() {

    private val _messages = MutableStateFlow<List<ChatMessage>>(emptyList())
    val messages: StateFlow<List<ChatMessage>> = _messages.asStateFlow()

    private val _emotes = MutableStateFlow<Map<String, String>>(emptyMap())
    val emotes: StateFlow<Map<String, String>> = _emotes.asStateFlow()

    private val _activeChannel = MutableStateFlow("")
    val activeChannel: StateFlow<String> = _activeChannel.asStateFlow()

    private val _isLoadingEmotes = MutableStateFlow(false)
    val isLoadingEmotes: StateFlow<Boolean> = _isLoadingEmotes.asStateFlow()

    private var currentAuthToken: String? = null
    private var currentUserLogin: String? = null

    init {
        viewModelScope.launch {
            ircClient.messages.collect { newMsg ->
                _messages.value = (_messages.value + newMsg).takeLast(150)
            }
        }
    }

    fun setChannel(channelName: String, authToken: String? = null, userLogin: String? = null) {
        val clean = channelName.trim().lowercase()
        if (clean.isEmpty()) return

        val authChanged = authToken != currentAuthToken || userLogin != currentUserLogin
        if (clean == _activeChannel.value && !authChanged) return

        _activeChannel.value = clean
        currentAuthToken = authToken
        currentUserLogin = userLogin
        _messages.value = emptyList()

        // Connect to IRC WebSocket with auth if available
        ircClient.connectAndJoin(clean, authToken, userLogin)

        // Fetch 7TV, BTTV, FFZ emotes
        viewModelScope.launch {
            _isLoadingEmotes.value = true
            try {
                val customEmotes = emoteRepository.getChannelEmotes(clean)
                _emotes.value = customEmotes
            } catch (e: Exception) {
                // Keep existing emotes on error
            } finally {
                _isLoadingEmotes.value = false
            }
        }
    }

    fun sendMessage(message: String, user: TwitchUser? = null): Boolean {
        val clean = message.trim()
        if (clean.isEmpty()) return false
        val sent = ircClient.sendMessage(clean)
        if (sent && user != null) {
            val myMsg = ChatMessage(
                id = UUID.randomUUID().toString(),
                user = user.login,
                displayName = user.displayName,
                color = "#9146FF",
                text = clean,
                timestamp = System.currentTimeMillis()
            )
            viewModelScope.launch {
                _messages.value = (_messages.value + myMsg).takeLast(150)
            }
        }
        return sent
    }

    override fun onCleared() {
        super.onCleared()
        ircClient.disconnect()
    }
}
