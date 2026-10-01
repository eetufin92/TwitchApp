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
import androidx.media3.exoplayer.DefaultLivePlaybackSpeedControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.data.auth.TwitchAuthManager
import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.data.network.TwitchGqlClient
import java.io.ByteArrayOutputStream
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
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
        val hasAnyAdTag = content.contains("stitched-ad") ||
                content.contains("twitch-stitched-ad") ||
                content.contains("EXT-X-TWITCH-PREVIEW-AD")

        if (!hasAnyAdTag) {
            onAdDetected(false, "", 0)
            return
        }

        var adId = ""
        var duration = 30
        val idMatch = Regex("""ID="([^"]+)"""").find(content)
        if (idMatch != null) {
            adId = idMatch.groupValues[1]
        } else {
            val segMatch = Regex("""stitched-ad-([a-zA-Z0-9_-]+)""").find(content)
            if (segMatch != null) {
                adId = segMatch.groupValues[0]
            }
        }
        val match = Regex("""(?:PLANNED-)?DURATION=([0-9.]+)""").find(content)
        if (match != null) {
            duration = match.groupValues[1].toDoubleOrNull()?.roundToInt() ?: 30
        }

        // Check START-DATE and END-DATE timestamps in #EXT-X-DATERANGE:
        var isExpired = false
        val startDateStr = Regex("""START-DATE="([^"]+)"""").find(content)?.groupValues?.get(1)
        val endDateStr = Regex("""END-DATE="([^"]+)"""").find(content)?.groupValues?.get(1)

        if (!startDateStr.isNullOrEmpty()) {
            try {
                val startMs = java.time.Instant.parse(startDateStr).toEpochMilli()
                val endMs = if (!endDateStr.isNullOrEmpty()) {
                    try {
                        java.time.Instant.parse(endDateStr).toEpochMilli()
                    } catch (e: Exception) {
                        startMs + (duration * 1000L)
                    }
                } else {
                    startMs + (duration * 1000L)
                }

                val now = System.currentTimeMillis()
                // If now is past endMs + 4000ms (buffer delay margin), this ad has already finished airing
                if (now > endMs + 4000L) {
                    isExpired = true
                } else {
                    val remainingSec = ((endMs - now) / 1000L).toInt()
                    if (remainingSec in 1..duration) {
                        duration = remainingSec
                    }
                }
            } catch (e: Exception) {
                // Ignore parse errors and fall back to active duration
            }
        }

        val isAdActive = !isExpired
        Log.d("PlayerViewModel", "Ad detection: hasTag=$hasAnyAdTag, adId=$adId, dur=$duration, isAdActive=$isAdActive (expired=$isExpired)")
        onAdDetected(isAdActive, adId, duration)
    }
}

data class VideoTrackOption(
    val id: String,
    val label: String,
    val width: Int = 0,
    val height: Int = 0,
    val frameRate: Float = 0f,
    val bitrate: Int = 0,
    val isSource: Boolean = false
)

class PlayerViewModel(
    application: Application,
    private val gqlClient: TwitchGqlClient = TwitchGqlClient()
) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "PlayerViewModel"
    }

    private val settingsManager = TwitchSettingsManager(application)
    private val authManager = TwitchAuthManager.getInstance(application)

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

    private val _lowLatencyBufferMs = MutableStateFlow(settingsManager.getLowLatencyBufferMs())
    val lowLatencyBufferMs: StateFlow<Int> = _lowLatencyBufferMs.asStateFlow()

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

    private val _isAuto360pAds = MutableStateFlow(settingsManager.isAuto360pAds())
    val isAuto360pAds: StateFlow<Boolean> = _isAuto360pAds.asStateFlow()

    private val _isShowAdOverlay = MutableStateFlow(settingsManager.isShowAdOverlay())
    val isShowAdOverlay: StateFlow<Boolean> = _isShowAdOverlay.asStateFlow()

    private var adCountdownJob: Job? = null
    private var currentAdId: String = ""
    private var previousVolume: Float = 1.0f
    private var preAdQuality: String? = null
    private var stallRecoveryJob: Job? = null
    private var consecutiveCleanPlaylists = 0
    private var lastAdEndedTimestamp = 0L
    private var adBreakStartTime = 0L
    private val completedAdIds = LinkedHashSet<String>()

    val exoPlayer: ExoPlayer by lazy {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2_500,  // min buffer 2.5s (realistic for low-latency live HLS with 2s chunks)
                15_000, // max buffer 15s
                1_000,  // buffer for playback 1s (fast startup)
                1_500   // buffer for playback after rebuffer 1.5s (quick recovery from stall)
            )
            .setBackBuffer(3_000, true)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val livePlaybackSpeedControl = DefaultLivePlaybackSpeedControl.Builder()
            .setFallbackMinPlaybackSpeed(1.0f)
            .setFallbackMaxPlaybackSpeed(1.01f)
            .setMaxLiveOffsetErrorMsForUnitSpeed(2_000L)
            .setTargetLiveOffsetIncrementOnRebufferMs(1_000L)
            .setMinUpdateIntervalMs(1000L)
            .setProportionalControlFactor(0.1f)
            .build()

        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .build()

        ExoPlayer.Builder(application)
            .setLoadControl(loadControl)
            .setLivePlaybackSpeedControl(livePlaybackSpeedControl)
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

    private val _expandPlayerEvent = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val expandPlayerEvent: SharedFlow<Unit> = _expandPlayerEvent.asSharedFlow()

    fun expandPlayer() {
        if (_isMiniPlayer.value) {
            _expandPlayerEvent.tryEmit(Unit)
        }
    }

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

        // Sort descending by height, then fps, then bitrate
        val sortedFormats = videoFormats
            .distinctBy { "${it.height}p${it.frameRate.toInt()}" }
            .sortedWith(
                compareByDescending<Format> { it.height }
                    .thenByDescending { it.frameRate }
                    .thenByDescending { it.bitrate }
            )

        sortedFormats.forEachIndexed { index, format ->
            val fps = if (format.frameRate > 30f) "${format.frameRate.toInt()}" else ""
            val isSource = index == 0 ||
                format.id?.equals("chunked", ignoreCase = true) == true ||
                format.label?.contains("source", ignoreCase = true) == true

            val resStr = if (format.height > 0) "${format.height}p$fps" else ""
            val label = if (isSource) {
                if (resStr.isNotEmpty()) "$resStr (Source)" else (format.label ?: "Source")
            } else {
                if (resStr.isNotEmpty()) resStr else (format.label ?: "Video Track")
            }

            qualities.add(
                VideoTrackOption(
                    id = if (isSource) "source" else "${format.height}p$fps",
                    label = label,
                    width = format.width,
                    height = format.height,
                    frameRate = format.frameRate,
                    bitrate = format.bitrate,
                    isSource = isSource
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
        if (_adBreakActive.value) {
            preAdQuality = qualityId
        }
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
            "source" -> {
                _isAudioOnly.value = false
                settingsManager.setAudioOnly(false)
                val sourceOpt = _availableQualities.value.find { it.id == "source" || it.isSource }
                val targetHeight = sourceOpt?.height?.takeIf { it > 0 } ?: Int.MAX_VALUE
                exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
                    .buildUpon()
                    .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                    .setMaxVideoSize(Int.MAX_VALUE, targetHeight)
                    .setMinVideoSize(0, 0)
                    .build()
            }
            else -> {
                _isAudioOnly.value = false
                settingsManager.setAudioOnly(false)
                val opt = _availableQualities.value.find { it.id == qualityId }
                    ?: _availableQualities.value.find { it.isSource && qualityId.startsWith("${it.height}p") }
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
            playChannel(_currentChannel.value, forceReload = true)
        }
    }

    fun setLowLatencyBuffer(bufferMs: Int) {
        val clamped = bufferMs.coerceIn(1500, 8000)
        _lowLatencyBufferMs.value = clamped
        settingsManager.setLowLatencyBufferMs(clamped)
        if (_isLowLatency.value && _currentChannel.value.isNotEmpty()) {
            playChannel(_currentChannel.value, forceReload = true)
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

    fun setAuto360pAds(enabled: Boolean) {
        _isAuto360pAds.value = enabled
        settingsManager.setAuto360pAds(enabled)
    }

    private fun applyAd360pTrackSelection() {
        if (_isAudioOnly.value) return
        val opt360 = _availableQualities.value.find { it.id.contains("360p") || (it.height in 1..360) }
            ?: _availableQualities.value.find { it.id.contains("480p") || (it.height in 1..480) }
        val targetHeight = opt360?.height?.takeIf { it > 0 } ?: 360
        exoPlayer.trackSelectionParameters = exoPlayer.trackSelectionParameters
            .buildUpon()
            .setTrackTypeDisabled(C.TRACK_TYPE_VIDEO, false)
            .setMaxVideoSize(Int.MAX_VALUE, targetHeight)
            .setMinVideoSize(0, 0)
            .build()
    }

    private fun handleAdDetection(hasAd: Boolean, adId: String, durationSeconds: Int) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            if (hasAd) {
                consecutiveCleanPlaylists = 0

                // If this ad has already finished playing recently, ignore it!
                if (adId.isNotEmpty() && completedAdIds.contains(adId)) {
                    return@launch
                }

                // If ad break is already active:
                if (_adBreakActive.value) {
                    // Safety timeout: if ad break exceeds 150 seconds, end it
                    if (now - adBreakStartTime > 150_000L) {
                        Log.d(TAG, "Ad break safety timeout reached (150s), ending ad break")
                        endAdBreak()
                        return@launch
                    }

                    // If a distinct new ad ID arrives during an ongoing ad pod:
                    if (adId.isNotEmpty() && currentAdId.isNotEmpty() && adId != currentAdId) {
                        Log.d(TAG, "New ad in pod detected: $currentAdId -> $adId")
                        completedAdIds.add(currentAdId)
                        currentAdId = adId
                        val dur = if (durationSeconds in 5..120) durationSeconds else 30
                        _adBreakDuration.value = dur
                        _adBreakRemaining.value = dur
                        startCountdown(dur)
                    }
                    return@launch
                }

                // If ad break is NOT active:
                // Cooldown: prevent re-triggering within 4s of previous ad end
                if (now - lastAdEndedTimestamp < 4_000L) {
                    return@launch
                }

                // Begin new ad break
                _adBreakActive.value = true
                adBreakStartTime = now
                currentAdId = adId
                val dur = if (durationSeconds in 5..120) durationSeconds else 30
                _adBreakDuration.value = dur
                _adBreakRemaining.value = dur

                // Only apply 360p track swap if user explicitly opted in
                if (_isAuto360pAds.value && preAdQuality == null) {
                    preAdQuality = _selectedQuality.value
                    applyAd360pTrackSelection()
                }

                if (_isAutoMuteAds.value) {
                    previousVolume = exoPlayer.volume
                    exoPlayer.volume = 0f
                }

                startCountdown(dur)
            } else {
                // hasAd is false (ad is no longer at live edge)
                if (_adBreakActive.value) {
                    consecutiveCleanPlaylists++
                    // If live segments are playing without ads for 2 playlists (~3s), end ad break immediately!
                    if (consecutiveCleanPlaylists >= 2) {
                        endAdBreak()
                    }
                }
            }
        }
    }

    private fun startCountdown(dur: Int) {
        adCountdownJob?.cancel()
        adCountdownJob = viewModelScope.launch {
            var remaining = dur
            while (remaining > 0) {
                delay(1000)
                remaining--
                _adBreakRemaining.value = remaining
            }
            // Once countdown finishes, end ad break immediately so stream is not stuck muted!
            endAdBreak()
        }
    }

    private fun endAdBreak() {
        if (_adBreakActive.value) {
            _adBreakActive.value = false
            _adBreakRemaining.value = 0
            if (currentAdId.isNotEmpty()) {
                completedAdIds.add(currentAdId)
                if (completedAdIds.size > 50) {
                    val first = completedAdIds.firstOrNull()
                    if (first != null) completedAdIds.remove(first)
                }
            }
            currentAdId = ""
            lastAdEndedTimestamp = System.currentTimeMillis()
            consecutiveCleanPlaylists = 0
            adCountdownJob?.cancel()
            adCountdownJob = null

            // Restore user's original quality after ad break ends
            preAdQuality?.let { prevQuality ->
                applyTrackSelection(prevQuality)
                preAdQuality = null
            }

            if (_isAutoMuteAds.value && exoPlayer.volume == 0f) {
                exoPlayer.volume = if (previousVolume > 0f) previousVolume else 1.0f
            }
        }
    }

    fun dismissAdBreak() {
        endAdBreak()
    }

    fun playChannel(channelName: String, forceReload: Boolean = false) {
        val clean = channelName.trim().lowercase().removePrefix("@")
        if (clean.isEmpty()) return

        val currentClean = _currentChannel.value.trim().lowercase().removePrefix("@")
        if (!forceReload && currentClean == clean && currentClean.isNotEmpty()) {
            if (_isMiniPlayer.value) {
                _expandPlayerEvent.tryEmit(Unit)
            }
            if (!exoPlayer.isPlaying && exoPlayer.playbackState != Player.STATE_IDLE) {
                exoPlayer.play()
            } else if (exoPlayer.playbackState == Player.STATE_IDLE && exoPlayer.mediaItemCount > 0) {
                exoPlayer.prepare()
                exoPlayer.play()
            }
            return
        }

        completedAdIds.clear()
        endAdBreak()
        preAdQuality = null
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

                val tokenResult = gqlClient.getStreamPlaybackAccessToken(clean, authManager.getAuthToken())
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
                            val target = _lowLatencyBufferMs.value.coerceIn(2000, 8000)
                            val minOffset = (target - 1500).coerceAtLeast(1500)
                            val maxOffset = (target + 4000).coerceAtLeast(6000)
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(target.toLong())
                                .setMinOffsetMs(minOffset.toLong())
                                .setMaxOffsetMs(maxOffset.toLong())
                                .setMinPlaybackSpeed(1.0f)
                                .setMaxPlaybackSpeed(1.005f)
                                .build()
                        } else {
                            MediaItem.LiveConfiguration.Builder()
                                .setTargetOffsetMs(8000)
                                .setMinOffsetMs(5000)
                                .setMaxOffsetMs(15000)
                                .setMinPlaybackSpeed(0.99f)
                                .setMaxPlaybackSpeed(1.01f)
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
