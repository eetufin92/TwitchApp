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

    init {
        viewModelScope.launch {
            ircClient.messages.collect { newMsg ->
                _messages.value = (_messages.value + newMsg).takeLast(150)
            }
        }
    }

    fun setChannel(channelName: String) {
        val clean = channelName.trim().lowercase()
        if (clean.isEmpty() || clean == _activeChannel.value) return

        _activeChannel.value = clean
        _messages.value = emptyList()

        // Connect to IRC WebSocket
        ircClient.connectAndJoin(clean)

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

    override fun onCleared() {
        super.onCleared()
        ircClient.disconnect()
    }
}
