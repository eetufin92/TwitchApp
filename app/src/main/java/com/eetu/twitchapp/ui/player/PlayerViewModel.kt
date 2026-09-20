package com.eetu.twitchapp.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.datasource.DefaultHttpDataSource
import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.data.network.TwitchGqlClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class PlayerViewModel(
    application: Application,
    private val gqlClient: TwitchGqlClient = TwitchGqlClient()
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    val exoPlayer: ExoPlayer by lazy {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_000,  // min buffer 2s for live low latency
                10_000, // max buffer 10s
                1_000,  // buffer for playback 1s
                2_000   // buffer for playback after rebuffer 2s
            )
            .build()

        ExoPlayer.Builder(application)
            .setLoadControl(loadControl)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _playbackState.value = state
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                    }
                })
            }
    }

    private val _currentChannel = MutableStateFlow("")
    val currentChannel: StateFlow<String> = _currentChannel.asStateFlow()

    private val _streamInfo = MutableStateFlow<LiveStreamItem?>(null)
    val streamInfo: StateFlow<LiveStreamItem?> = _streamInfo.asStateFlow()

    private val _isMiniPlayer = MutableStateFlow(false)
    val isMiniPlayer: StateFlow<Boolean> = _isMiniPlayer.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playbackState = MutableStateFlow(Player.STATE_IDLE)
    val playbackState: StateFlow<Int> = _playbackState.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    fun playChannel(channelName: String) {
        val clean = channelName.trim().lowercase()
        if (clean.isEmpty()) return

        if (_currentChannel.value == clean && exoPlayer.playbackState != Player.STATE_IDLE) {
            // Already playing this channel, restore to full player
            _isMiniPlayer.value = false
            return
        }

        _currentChannel.value = clean
        _isLoading.value = true
        _errorMessage.value = null
        _isMiniPlayer.value = false

        viewModelScope.launch {
            try {
                // Fetch stream details (title, avatar, viewer count)
                launch {
                    val details = gqlClient.getChannelDetails(clean)
                    if (details != null) {
                        _streamInfo.value = details
                    }
                }

                // Fetch playback access token and Usher master playlist URL
                val tokenResult = gqlClient.getStreamPlaybackAccessToken(clean)
                if (tokenResult == null) {
                    _errorMessage.value = "Failed to obtain playback token for $clean. Channel might be offline."
                    _isLoading.value = false
                    return@launch
                }

                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .setConnectTimeoutMs(8000)
                    .setReadTimeoutMs(8000)

                val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(MediaItem.fromUri(Uri.parse(tokenResult.masterPlaylistUrl)))

                exoPlayer.setMediaSource(hlsMediaSource)
                exoPlayer.prepare()
                exoPlayer.play()
                _isLoading.value = false
            } catch (e: Exception) {
                Log.e(TAG, "Error playing stream for $clean", e)
                _errorMessage.value = e.localizedMessage ?: "Playback error"
                _isLoading.value = false
            }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun setMiniPlayer(mini: Boolean) {
        _isMiniPlayer.value = mini
    }

    fun closePlayback() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _currentChannel.value = ""
        _streamInfo.value = null
        _isMiniPlayer.value = false
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.release()
    }
}
