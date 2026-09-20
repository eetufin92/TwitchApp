package com.eetu.twitchapp.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.data.network.TwitchGqlClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VideoTrackOption(
    val id: String,
    val label: String,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val bitrate: Int = 0
)

class PlayerViewModel(
    application: Application,
    private val gqlClient: TwitchGqlClient = TwitchGqlClient()
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val settingsManager = TwitchSettingsManager(application)

    private val _availableQualities = MutableStateFlow<List<VideoTrackOption>>(
        listOf(VideoTrackOption("auto", "Auto"))
    )
    val availableQualities: StateFlow<List<VideoTrackOption>> = _availableQualities.asStateFlow()

    private val _selectedQuality = MutableStateFlow(settingsManager.getPreferredQuality())
    val selectedQuality: StateFlow<String> = _selectedQuality.asStateFlow()

    private val _isAudioOnly = MutableStateFlow(settingsManager.isAudioOnly())
    val isAudioOnly: StateFlow<Boolean> = _isAudioOnly.asStateFlow()

    private val _isLowLatency = MutableStateFlow(settingsManager.isLowLatency())
    val isLowLatency: StateFlow<Boolean> = _isLowLatency.asStateFlow()

    private val _backgroundAudioEnabled = MutableStateFlow(settingsManager.isBackgroundAudio())
    val backgroundAudioEnabled: StateFlow<Boolean> = _backgroundAudioEnabled.asStateFlow()

    private val _isPipEnabled = MutableStateFlow(settingsManager.isPipEnabled())
    val isPipEnabled: StateFlow<Boolean> = _isPipEnabled.asStateFlow()

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

                    override fun onTracksChanged(tracks: Tracks) {
                        extractVideoQualities(tracks)
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

    private fun extractVideoQualities(tracks: Tracks) {
        val qualities = mutableListOf<VideoTrackOption>()
        qualities.add(VideoTrackOption("auto", "Auto"))

        val videoFormats = mutableListOf<Format>()
        for (group in tracks.groups) {
            if (group.type == C.TRACK_TYPE_VIDEO) {
                val mediaTrackGroup = group.mediaTrackGroup
                for (i in 0 until mediaTrackGroup.length) {
                    videoFormats.add(mediaTrackGroup.getFormat(i))
                }
            }
        }

        // Sort descending by height, then fps
        videoFormats.distinctBy { "${it.height}p${it.frameRate.toInt()}" }
            .sortedWith(compareByDescending<Format> { it.height }.thenByDescending { it.frameRate })
            .forEach { format ->
                val fps = if (format.frameRate > 30f) "${format.frameRate.toInt()}" else ""
                val label = if (format.height > 0) "${format.height}p$fps" else (format.label ?: "Video Track")
                qualities.add(
                    VideoTrackOption(
                        id = "${format.height}p$fps",
                        label = label,
                        width = format.width,
                        height = format.height,
                        frameRate = format.frameRate,
                        bitrate = format.bitrate
                    )
                )
            }

        qualities.add(VideoTrackOption("audio_only", "Audio Only"))
        _availableQualities.value = qualities

        // Reapply selected quality if needed
        applyTrackSelection(_selectedQuality.value)
    }

    fun selectQuality(qualityId: String) {
        _selectedQuality.value = qualityId
        settingsManager.setPreferredQuality(qualityId)
        applyTrackSelection(qualityId)
    }

    private fun applyTrackSelection(qualityId: String) {
        when (qualityId) {
            "auto" -> {
                _isAudioOnly.value = false
                settingsManager.setAudioOnly(false)
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .setMaxVideoSize(Int.MAX_VALUE, Int.MAX_VALUE)
                    .setMinVideoSize(0, 0)
                    .build()
            }
            "audio_only" -> {
                _isAudioOnly.value = true
                settingsManager.setAudioOnly(true)
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, true)
                    .build()
            }
            else -> {
                _isAudioOnly.value = false
                settingsManager.setAudioOnly(false)
                val opt = _availableQualities.value.find { it.id == qualityId }
                if (opt != null && opt.height > 0) {
                    exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                        .setMaxVideoSize(Int.MAX_VALUE, opt.height)
                        .setMinVideoSize(0, opt.height)
                        .build()
                }
            }
        }
    }

    fun toggleAudioOnly() {
        if (_isAudioOnly.value) {
            selectQuality("auto")
        } else {
            selectQuality("audio_only")
        }
    }

    fun toggleLowLatency() {
        val newMode = !_isLowLatency.value
        _isLowLatency.value = newMode
        settingsManager.setLowLatency(newMode)
        // Refresh current channel playback with updated low latency settings
        if (_currentChannel.value.isNotEmpty()) {
            val ch = _currentChannel.value
            _currentChannel.value = ""
            playChannel(ch)
        }
    }

    fun setBackgroundAudio(enabled: Boolean) {
        _backgroundAudioEnabled.value = enabled
        settingsManager.setBackgroundAudio(enabled)
    }

    fun setPipEnabled(enabled: Boolean) {
        _isPipEnabled.value = enabled
        settingsManager.setPipEnabled(enabled)
    }

    fun playChannel(channelName: String) {
        val clean = channelName.trim().lowercase()
        if (clean.isEmpty()) return

        if (_currentChannel.value == clean && exoPlayer.playbackState != Player.STATE_IDLE) {
            _isMiniPlayer.value = false
            return
        }

        _currentChannel.value = clean
        _isLoading.value = true
        _errorMessage.value = null
        _isMiniPlayer.value = false

        viewModelScope.launch {
            try {
                launch {
                    val details = gqlClient.getChannelDetails(clean)
                    if (details != null) {
                        _streamInfo.value = details
                    }
                }

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

                val playlistUrl = if (!_isLowLatency.value) {
                    tokenResult.masterPlaylistUrl.replace("fast_bread=true", "fast_bread=false")
                } else {
                    tokenResult.masterPlaylistUrl
                }

                val mediaItem = MediaItem.Builder()
                    .setUri(Uri.parse(playlistUrl))
                    .setLiveConfiguration(
                        if (_isLowLatency.value) {
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(1500)
                                .setMinPlaybackSpeed(0.97f)
                                .setMaxPlaybackSpeed(1.03f)
                                .build()
                        } else {
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(6000)
                                .build()
                        }
                    )
                    .build()

                val hlsMediaSource = HlsMediaSource.Factory(dataSourceFactory)
                    .setAllowChunklessPreparation(true)
                    .createMediaSource(mediaItem)

                exoPlayer.setMediaSource(hlsMediaSource)
                exoPlayer.prepare()
                applyTrackSelection(_selectedQuality.value)
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
