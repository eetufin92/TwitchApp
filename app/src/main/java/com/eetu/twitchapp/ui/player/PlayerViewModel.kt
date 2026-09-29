package com.eetu.twitchapp.ui.player

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.TransferListener
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.data.network.TwitchGqlClient
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class TwitchAdDetectingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val onAdDetected: (Boolean, String, Int) -> Unit
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return TwitchAdDetectingDataSource(upstreamFactory.createDataSource(), onAdDetected)
    }
}

class TwitchAdDetectingDataSource(
    private val upstream: DataSource,
    private val onAdDetected: (Boolean, String, Int) -> Unit
) : DataSource {
    private var isPlaylist = false
    private val playlistBuffer = ByteArrayOutputStream()

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val path = dataSpec.uri.path ?: ""
        isPlaylist = path.endsWith(".m3u8") || path.contains("m3u8")
        if (isPlaylist) {
            playlistBuffer.reset()
        }
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(buffer, offset, length)
        if (isPlaylist && bytesRead > 0) {
            playlistBuffer.write(buffer, offset, bytesRead)
        }
        return bytesRead
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        if (isPlaylist && playlistBuffer.size() > 0) {
            try {
                val content = playlistBuffer.toString("UTF-8")
                parsePlaylistForAds(content)
            } catch (e: Exception) {
                // Ignore parse errors
            }
        }
        upstream.close()
    }

    private fun parsePlaylistForAds(content: String) {
        val hasAd = content.contains("stitched-ad") ||
                content.contains("twitch-stitched-ad") ||
                content.contains("EXT-X-TWITCH-PREVIEW-AD")

        var adId = ""
        var duration = 30
        if (hasAd) {
            val idMatch = Regex("""ID="([^"]+)"""").find(content)
            if (idMatch != null) {
                adId = idMatch.groupValues[1]
            }
            val match = Regex("""(?:PLANNED-)?DURATION=([0-9.]+)""").find(content)
            if (match != null) {
                duration = match.groupValues[1].toDoubleOrNull()?.roundToInt() ?: 30
            }
        }
        onAdDetected(hasAd, adId, duration)
    }
}

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

    private val _adBreakActive = MutableStateFlow(false)
    val adBreakActive: StateFlow<Boolean> = _adBreakActive.asStateFlow()

    private val _adBreakRemaining = MutableStateFlow(0)
    val adBreakRemaining: StateFlow<Int> = _adBreakRemaining.asStateFlow()

    private val _adBreakDuration = MutableStateFlow(0)
    val adBreakDuration: StateFlow<Int> = _adBreakDuration.asStateFlow()

    private val _isAutoMuteAds = MutableStateFlow(settingsManager.isAutoMuteAds())
    val isAutoMuteAds: StateFlow<Boolean> = _isAutoMuteAds.asStateFlow()

    private val _isShowAdOverlay = MutableStateFlow(settingsManager.isShowAdOverlay())
    val isShowAdOverlay: StateFlow<Boolean> = _isShowAdOverlay.asStateFlow()

    private var adCountdownJob: Job? = null
    private var currentAdId: String = ""
    private var previousVolume: Float = 1.0f
    private var stallRecoveryJob: Job? = null

    val exoPlayer: ExoPlayer by lazy {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                15_000, // min buffer 15s (allows buffering ahead available segments in live window)
                30_000, // max buffer 30s
                1_000,  // buffer for playback 1s (fast startup)
                1_500   // buffer for playback after rebuffer 1.5s (quick recovery from stall)
            )
            .setBackBuffer(5_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(application)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .setHandleAudioBecomingNoisy(true)
            .build().apply {
                repeatMode = Player.REPEAT_MODE_OFF
                playWhenReady = true
                addListener(object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        _playbackState.value = state
                        if (state == Player.STATE_BUFFERING && playWhenReady) {
                            scheduleStallRecovery()
                        } else {
                            cancelStallRecovery()
                        }
                    }

                    override fun onIsPlayingChanged(isPlaying: Boolean) {
                        _isPlaying.value = isPlaying
                        if (isPlaying) {
                            cancelStallRecovery()
                        }
                    }

                    override fun onTracksChanged(tracks: Tracks) {
                        extractVideoQualities(tracks)
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "ExoPlayer playback error: ${error.errorCodeName}", error)
                        if (error.errorCode == PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW) {
                            seekToDefaultPosition()
                            prepare()
                            play()
                        } else {
                            // Retry playback after a brief delay for transient network drops
                            prepare()
                            play()
                        }
                    }
                })
            }
    }

    private fun scheduleStallRecovery() {
        stallRecoveryJob?.cancel()
        stallRecoveryJob = viewModelScope.launch {
            delay(5000)
            if (_playbackState.value == Player.STATE_BUFFERING && exoPlayer.playWhenReady && _currentChannel.value.isNotEmpty()) {
                Log.w(TAG, "Stall detected in live stream, auto-seeking to live edge...")
                exoPlayer.seekToDefaultPosition()
                exoPlayer.prepare()
                exoPlayer.play()
            }
        }
    }

    private fun cancelStallRecovery() {
        stallRecoveryJob?.cancel()
        stallRecoveryJob = null
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
                        .setMinVideoSize(0, 0)
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

    fun setAutoMuteAds(enabled: Boolean) {
        _isAutoMuteAds.value = enabled
        settingsManager.setAutoMuteAds(enabled)
    }

    fun setShowAdOverlay(enabled: Boolean) {
        _isShowAdOverlay.value = enabled
        settingsManager.setShowAdOverlay(enabled)
    }

    private fun handleAdDetection(hasAd: Boolean, adId: String, durationSeconds: Int) {
        viewModelScope.launch {
            if (hasAd) {
                // If this is a new ad break or a distinct second ad in a pod
                val isNewAd = !_adBreakActive.value || (adId.isNotEmpty() && adId != currentAdId)
                if (isNewAd) {
                    if (adId.isNotEmpty()) {
                        currentAdId = adId
                    }
                    _adBreakActive.value = true
                    val dur = if (durationSeconds > 0) durationSeconds else 30
                    _adBreakDuration.value = dur
                    _adBreakRemaining.value = dur

                    if (_isAutoMuteAds.value) {
                        previousVolume = exoPlayer.volume
                        exoPlayer.volume = 0f
                    }

                    adCountdownJob?.cancel()
                    adCountdownJob = launch {
                        var remaining = dur
                        while (remaining > 0) {
                            delay(1000)
                            remaining--
                            _adBreakRemaining.value = remaining
                        }
                        // Allow brief grace period for next segment, then auto-dismiss if still 0
                        delay(4000)
                        if (_adBreakRemaining.value == 0 && _adBreakActive.value) {
                            endAdBreak()
                        }
                    }
                }
            } else {
                if (_adBreakActive.value) {
                    endAdBreak()
                }
            }
        }
    }

    private fun endAdBreak() {
        if (_adBreakActive.value) {
            _adBreakActive.value = false
            _adBreakRemaining.value = 0
            currentAdId = ""
            adCountdownJob?.cancel()
            adCountdownJob = null
            if (_isAutoMuteAds.value && exoPlayer.volume == 0f) {
                exoPlayer.volume = if (previousVolume > 0f) previousVolume else 1.0f
            }
        }
    }

    fun playChannel(channelName: String) {
        val clean = channelName.trim().lowercase()
        if (clean.isEmpty()) return

        if (_currentChannel.value == clean && exoPlayer.playbackState != Player.STATE_IDLE) {
            _isMiniPlayer.value = false
            return
        }

        endAdBreak()
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

                val httpFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                    .setConnectTimeoutMs(8000)
                    .setReadTimeoutMs(8000)

                val dataSourceFactory = TwitchAdDetectingDataSourceFactory(httpFactory) { hasAd, adId, dur ->
                    handleAdDetection(hasAd, adId, dur)
                }

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
                                .setTargetOffsetMs(3000)
                                .setMinOffsetMs(2000)
                                .setMaxOffsetMs(6000)
                                .setMinPlaybackSpeed(0.98f)
                                .setMaxPlaybackSpeed(1.02f)
                                .build()
                        } else {
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(8000)
                                .setMinOffsetMs(5000)
                                .setMaxOffsetMs(15000)
                                .setMinPlaybackSpeed(0.98f)
                                .setMaxPlaybackSpeed(1.02f)
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
        cancelStallRecovery()
        endAdBreak()
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        _currentChannel.value = ""
        _streamInfo.value = null
        _isMiniPlayer.value = false
    }

    override fun onCleared() {
        super.onCleared()
        cancelStallRecovery()
        endAdBreak()
        exoPlayer.release()
    }
}
