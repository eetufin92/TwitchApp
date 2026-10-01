package com.eetu.twitchapp.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.MainActivity
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.data.auth.TwitchAuthManager
import com.eetu.twitchapp.ui.auth.TwitchLoginDialog
import com.eetu.twitchapp.ui.chat.ChatViewModel
import com.eetu.twitchapp.ui.chat.NativeChatView
import com.eetu.twitchapp.ui.components.FloatingResizableChat
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CollapsiblePlayerScaffold(
    playerViewModel: PlayerViewModel,
    onOpenMultiStream: (String) -> Unit = {},
    onOpenMultiStreamWithChannels: (List<String>) -> Unit = { channels -> onOpenMultiStream(channels.firstOrNull() ?: "") },
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val twitchColors = LocalTwitchColors.current
    val settingsManager = remember { TwitchSettingsManager(context) }

    val currentChannel by playerViewModel.currentChannel.collectAsState()
    val streamInfo by playerViewModel.streamInfo.collectAsState()
    val isMiniPlayer by playerViewModel.isMiniPlayer.collectAsState()
    val isLoading by playerViewModel.isLoading.collectAsState()
    val errorMessage by playerViewModel.errorMessage.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val availableQualities by playerViewModel.availableQualities.collectAsState()
    val selectedQuality by playerViewModel.selectedQuality.collectAsState()
    val isAudioOnly by playerViewModel.isAudioOnly.collectAsState()
    val isLowLatency by playerViewModel.isLowLatency.collectAsState()
    val isPipEnabled by playerViewModel.isPipEnabled.collectAsState()
    val backgroundAudioEnabled by playerViewModel.backgroundAudioEnabled.collectAsState()

    var isOledMode by remember { mutableStateOf(settingsManager.isOledMode()) }

    val authManager = remember { TwitchAuthManager.getInstance(context) }
    val currentUser by authManager.currentUser.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }

    val chatViewModel = remember { ChatViewModel() }
    val chatListState = rememberLazyListState()
    val chatMessages by chatViewModel.messages.collectAsState()
    val chatEmotes by chatViewModel.emotes.collectAsState()
    val chatStructuredEmotes by chatViewModel.structuredEmotes.collectAsState()

    var showFloatingChat by rememberSaveable { mutableStateOf(false) }
    var showLandscapeSideChat by rememberSaveable { mutableStateOf(settingsManager.isSideChatVisible()) }
    var showPortraitChat by rememberSaveable { mutableStateOf(true) }
    var isFullscreen by remember { mutableStateOf(false) }

    var landscapeChatWidthDp by rememberSaveable { mutableFloatStateOf(340f) }
    val defaultVideoHeightDp = remember(configuration.screenWidthDp) {
        configuration.screenWidthDp * 9f / 16f
    }
    var portraitVideoHeightDp by rememberSaveable { mutableFloatStateOf(defaultVideoHeightDp) }

    fun updateLandscapeSideChat(visible: Boolean) {
        showLandscapeSideChat = visible
        settingsManager.setSideChatVisible(visible)
    }

    fun cycleLandscapeChatMode(): String {
        return when {
            showLandscapeSideChat && !showFloatingChat -> {
                // Currently Sidebar -> switch to Floating
                updateLandscapeSideChat(false)
                showFloatingChat = true
                "Chat: Floating"
            }
            showFloatingChat -> {
                // Currently Floating -> switch to Off
                updateLandscapeSideChat(false)
                showFloatingChat = false
                "Chat: Off"
            }
            else -> {
                // Currently Off -> switch to Sidebar
                updateLandscapeSideChat(true)
                showFloatingChat = false
                "Chat: Sidebar"
            }
        }
    }

    fun cyclePortraitChatMode(): String {
        return when {
            showPortraitChat && !showFloatingChat -> {
                // Currently Sidebar (docked) -> switch to Floating
                showPortraitChat = false
                showFloatingChat = true
                "Chat: Floating"
            }
            showFloatingChat -> {
                // Currently Floating -> switch to Off
                showFloatingChat = false
                showPortraitChat = false
                "Chat: Off"
            }
            else -> {
                // Currently Off -> switch to Sidebar (docked)
                showFloatingChat = false
                showPortraitChat = true
                "Chat: Sidebar"
            }
        }
    }

    LaunchedEffect(currentChannel, currentUser) {
        if (currentChannel.isNotEmpty()) {
            chatViewModel.setChannel(
                channelName = currentChannel,
                authToken = authManager.getAuthToken(),
                userLogin = currentUser?.login
            )
        }
    }

    val adBreakActive by playerViewModel.adBreakActive.collectAsState()
    val adBreakRemaining by playerViewModel.adBreakRemaining.collectAsState()
    val isAutoMuteAds by playerViewModel.isAutoMuteAds.collectAsState()
    val isShowAdOverlay by playerViewModel.isShowAdOverlay.collectAsState()
    val isAuto360pAds by playerViewModel.isAuto360pAds.collectAsState()
    val lowLatencyBufferMs by playerViewModel.lowLatencyBufferMs.collectAsState()

    var showMultiStreamPicker by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        TwitchLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = { showLoginDialog = false }
        )
    }

    if (showMultiStreamPicker && currentChannel.isNotEmpty()) {
        com.eetu.twitchapp.ui.multistream.AddStreamerDialog(
            currentStreams = listOf(currentChannel),
            title = "Watch Multistream with ${streamInfo?.displayName ?: currentChannel}",
            onAddChannel = { secondChannel ->
                val clean = secondChannel.trim().lowercase()
                if (clean.isNotEmpty()) {
                    showMultiStreamPicker = false
                    onOpenMultiStreamWithChannels(listOf(currentChannel, clean))
                }
            },
            onDismiss = { showMultiStreamPicker = false }
        )
    }

    val activity = context as? Activity
    val isTablet = configuration.smallestScreenWidthDp >= 600
    val orientationManager = remember(activity) {
        activity?.let { DeviceOrientationManager(it) }
    }

    DisposableEffect(orientationManager) {
        orientationManager?.start()
        onDispose {
            orientationManager?.stop()
        }
    }

    val collapseFraction = remember { Animatable(0f) }
    var isDraggingUpFromMini by remember { mutableStateOf(false) }

    val minimizeToMiniPlayer: () -> Unit = {
        coroutineScope.launch {
            collapseFraction.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 260, easing = FastOutSlowInEasing)
            )
            playerViewModel.setMiniPlayer(true)
            collapseFraction.snapTo(0f)
        }
    }

    val expandFromMiniPlayer: () -> Unit = {
        coroutineScope.launch {
            isDraggingUpFromMini = false
            collapseFraction.snapTo(1f)
            playerViewModel.setMiniPlayer(false)
            collapseFraction.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
            )
        }
    }

    LaunchedEffect(playerViewModel) {
        playerViewModel.expandPlayerEvent.collect {
            if (isMiniPlayer) {
                expandFromMiniPlayer()
            } else if (collapseFraction.value > 0.01f) {
                coroutineScope.launch {
                    collapseFraction.animateTo(
                        targetValue = 0f,
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    )
                }
            }
        }
    }

    LaunchedEffect(isMiniPlayer) {
        if (!isMiniPlayer) {
            isDraggingUpFromMini = false
            if (collapseFraction.value > 0.01f) {
                collapseFraction.snapTo(0f)
            }
        }
    }

    // System Back Gesture handling:
    // 1. If in landscape:
    //    - If in fullscreen, exit fullscreen
    //    - If on tablet, collapse to miniplayer (or close if already miniplayer), do NOT force portrait
    //    - If on phone, return to portrait
    BackHandler(enabled = isLandscape) {
        if (isFullscreen) {
            isFullscreen = false
            updateLandscapeSideChat(true)
            (context as? MainActivity)?.toggleFullscreen(false)
        } else if (isTablet) {
            if (currentChannel.isNotEmpty() && !isMiniPlayer) {
                minimizeToMiniPlayer()
            } else if (currentChannel.isNotEmpty() && isMiniPlayer) {
                playerViewModel.closePlayback()
            }
        } else {
            orientationManager?.requestPortrait() ?: run {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            }
        }
    }

    // 2. If full player is active in portrait, collapse to bottom miniplayer bar
    BackHandler(enabled = !isLandscape && currentChannel.isNotEmpty() && !isMiniPlayer) {
        minimizeToMiniPlayer()
    }

    // 3. If docked in miniplayer, back closes playback
    BackHandler(enabled = !isLandscape && currentChannel.isNotEmpty() && isMiniPlayer) {
        playerViewModel.closePlayback()
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        // Base Content (Home Screen, Channels List, Search, etc.)
        content()

        // Active Player Layer
        if (currentChannel.isNotEmpty()) {
            val containerWidthPx = constraints.maxWidth.toFloat()
            val containerHeightPx = constraints.maxHeight.toFloat()
            val statusBarTopPx = with(density) { WindowInsets.statusBars.asPaddingValues().calculateTopPadding().toPx() }
            val maxDragDistancePx = (containerHeightPx * 0.45f).coerceAtLeast(with(density) { 280.dp.toPx() })

            fun handleVerticalDragProgress(delta: Float) {
                coroutineScope.launch {
                    val currentDragPx = collapseFraction.value * maxDragDistancePx
                    val nextDragPx = (currentDragPx + delta).coerceIn(0f, maxDragDistancePx)
                    val nextFraction = nextDragPx / maxDragDistancePx
                    collapseFraction.snapTo(nextFraction)
                }
            }

            fun handleVerticalDragEnd(velocity: Float, totalDragY: Float) {
                coroutineScope.launch {
                    val shouldDismiss = (collapseFraction.value > 0.50f) || (velocity > 1600f && totalDragY > 60f)
                    if (shouldDismiss && totalDragY > 0f) {
                        collapseFraction.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
                        playerViewModel.setMiniPlayer(true)
                        collapseFraction.snapTo(0f)
                    } else {
                        collapseFraction.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                    }
                }
            }

            // Geometry mapping for targeting miniplayer video slot in bottom-left
            val miniWidthPx = with(density) { 96.dp.toPx() }
            val miniCenterX = with(density) { 56.dp.toPx() } // 8.dp padding + 48.dp half-width
            // Use containerHeightPx directly so the target center lands exactly at the bottom on the miniplayer Surface
            val miniCenterY = containerHeightPx - with(density) { 34.dp.toPx() } // 68.dp / 2 from bottom of container

            val landscapeVideoWidthPx = if (showLandscapeSideChat && !isFullscreen) {
                with(density) { (configuration.screenWidthDp - landscapeChatWidthDp).coerceAtLeast(300f).dp.toPx() }
            } else {
                containerWidthPx
            }

            val videoWidthPx = if (isLandscape) landscapeVideoWidthPx else containerWidthPx
            val videoCenterX = if (isLandscape) (landscapeVideoWidthPx / 2f) else (containerWidthPx / 2f)
            val videoCenterY = if (isLandscape) (containerHeightPx / 2f) else (statusBarTopPx + (with(density) { portraitVideoHeightDp.dp.toPx() } / 2f))

            val targetScale = (miniWidthPx / videoWidthPx).coerceIn(0.10f, 0.40f)
            val originXFraction = (videoCenterX / containerWidthPx).coerceIn(0.05f, 0.95f)
            val originYFraction = (videoCenterY / containerHeightPx).coerceIn(0.05f, 0.95f)
            val targetDeltaX = miniCenterX - videoCenterX
            val targetDeltaY = miniCenterY - videoCenterY

            // 1. DOCKED MINIPLAYER or PLACEHOLDER MINI BAR AT BOTTOM
            // When in miniplayer mode, this bar is at the bottom with the mini preview and streamer info.
            // Dragging UP on this bar seamlessly transitions into the scaling full player!
            if (isMiniPlayer || collapseFraction.value > 0.01f || isDraggingUpFromMini) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(68.dp)
                        .graphicsLayer {
                            val p = if (isMiniPlayer && !isDraggingUpFromMini) 1f else collapseFraction.value
                            alpha = if (isMiniPlayer && !isDraggingUpFromMini) 1f else ((p - 0.15f) / 0.85f).coerceIn(0f, 1f)
                        }
                        .shadow(12.dp)
                        .pointerInput(isMiniPlayer) {
                            if (!isMiniPlayer) return@pointerInput
                            var totalDragY = 0f
                            var isDragging = false
                            var velocityTracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = {
                                    totalDragY = 0f
                                    isDragging = false
                                    velocityTracker = VelocityTracker()
                                },
                                onDrag = { change, dragAmount ->
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    totalDragY += dragAmount.y
                                    if (!isDragging) {
                                        if (totalDragY < -10f) {
                                            isDragging = true
                                            isDraggingUpFromMini = true
                                            change.consume()
                                            coroutineScope.launch {
                                                val progress = (-totalDragY / maxDragDistancePx).coerceIn(0f, 1f)
                                                collapseFraction.snapTo((1f - progress).coerceIn(0f, 1f))
                                            }
                                        }
                                    } else {
                                        change.consume()
                                        coroutineScope.launch {
                                            val progress = (-totalDragY / maxDragDistancePx).coerceIn(0f, 1f)
                                            collapseFraction.snapTo((1f - progress).coerceIn(0f, 1f))
                                        }
                                    }
                                },
                                onDragEnd = {
                                    if (isDragging) {
                                        val yVelocity = velocityTracker.calculateVelocity().y
                                        coroutineScope.launch {
                                            val shouldExpand = collapseFraction.value <= 0.50f || (yVelocity < -1400f && totalDragY < -40f)
                                            if (shouldExpand) {
                                                collapseFraction.animateTo(0f, tween(240, easing = FastOutSlowInEasing))
                                                playerViewModel.setMiniPlayer(false)
                                                isDraggingUpFromMini = false
                                            } else {
                                                collapseFraction.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
                                                isDraggingUpFromMini = false
                                            }
                                        }
                                    }
                                },
                                onDragCancel = {
                                    if (isDragging) {
                                        coroutineScope.launch {
                                            collapseFraction.animateTo(1f, tween(240, easing = FastOutSlowInEasing))
                                            isDraggingUpFromMini = false
                                        }
                                    }
                                }
                            )
                        }
                        .clickable {
                            if (isMiniPlayer && !isDraggingUpFromMini) {
                                expandFromMiniPlayer()
                            }
                        },
                    color = twitchColors.card
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (isMiniPlayer && !isDraggingUpFromMini) {
                            // 16:9 Video Mini Preview while docked
                            Box(
                                modifier = Modifier
                                    .width(96.dp)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color.Black)
                            ) {
                                AndroidView(
                                    factory = { ctx ->
                                        PlayerView(ctx).apply {
                                            player = playerViewModel.exoPlayer
                                            useController = false
                                            keepScreenOn = true
                                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                            setBackgroundColor(android.graphics.Color.BLACK)
                                        }
                                    },
                                    update = { pv ->
                                        pv.player = playerViewModel.exoPlayer
                                        pv.keepScreenOn = true
                                    },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                        } else {
                            // Reserved space for the scaling video to land / expand from
                            Spacer(
                                modifier = Modifier
                                    .width(96.dp)
                                    .fillMaxHeight()
                            )
                        }

                        // Stream info (Streamer + Title)
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = streamInfo?.displayName ?: currentChannel,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = streamInfo?.title ?: (streamInfo?.gameName ?: "Live"),
                                color = TwitchTextDim,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Play / Pause Toggle
                        IconButton(
                            onClick = { playerViewModel.togglePlayPause() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                                contentDescription = "Play/Pause",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        // Dismiss / Close Stream Button
                        IconButton(
                            onClick = { playerViewModel.closePlayback() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close stream",
                                tint = TwitchTextDim,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 2. FULL STREAM VIEW WITH SMOOTH SCALE-DOWN / SCALE-UP
            // Rendered when in full player or while dragging up from miniplayer
            if (!isMiniPlayer || isDraggingUpFromMini) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = collapseFraction.value
                            scaleX = 1f - p * (1f - targetScale)
                            scaleY = 1f - p * (1f - targetScale)
                            translationX = p * targetDeltaX
                            translationY = p * targetDeltaY
                            transformOrigin = TransformOrigin(originXFraction, originYFraction)
                            shape = RoundedCornerShape((p * 6).dp)
                            clip = p > 0.01f
                        }
                        .drawBehind {
                            val p = collapseFraction.value
                            val bgAlpha = (1f - p * 1.5f).coerceIn(0f, 1f)
                            drawRect(twitchColors.background.copy(alpha = bgAlpha))
                        }
                ) {
                    if (isLandscape) {
                        // Landscape / Tablet Split: Video on Left, Optional Chat on Right
                        Row(
                            modifier = Modifier.fillMaxSize()
                        ) {
                            NativeTwitchPlayer(
                                exoPlayer = playerViewModel.exoPlayer,
                                streamInfo = streamInfo,
                                channelName = currentChannel,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                availableQualities = availableQualities,
                                selectedQuality = selectedQuality,
                                isAudioOnly = isAudioOnly,
                                isLowLatency = isLowLatency,
                                lowLatencyBufferMs = lowLatencyBufferMs,
                                isPipEnabled = isPipEnabled,
                                backgroundAudioEnabled = backgroundAudioEnabled,
                                isOledMode = isOledMode,
                                onToggleOledMode = {
                                    isOledMode = it
                                    settingsManager.setOledMode(it)
                                },
                                onSelectQuality = { playerViewModel.selectQuality(it) },
                                onToggleAudioOnly = { playerViewModel.toggleAudioOnly() },
                                onToggleLowLatency = { playerViewModel.toggleLowLatency() },
                                onSelectLowLatencyBuffer = { playerViewModel.setLowLatencyBuffer(it) },
                                onTogglePipEnabled = { playerViewModel.setPipEnabled(it) },
                                onToggleBackgroundAudio = { playerViewModel.setBackgroundAudio(it) },
                                onMinimize = { minimizeToMiniPlayer() },
                                onToggleFullscreen = {
                                    if (isTablet) {
                                        if (isFullscreen) {
                                            isFullscreen = false
                                            updateLandscapeSideChat(true)
                                            (context as? MainActivity)?.toggleFullscreen(false)
                                        } else {
                                            isFullscreen = true
                                            updateLandscapeSideChat(false)
                                            (context as? MainActivity)?.toggleFullscreen(true)
                                        }
                                    } else {
                                        if (isFullscreen) {
                                            isFullscreen = false
                                            (context as? MainActivity)?.toggleFullscreen(false)
                                        } else {
                                            orientationManager?.requestPortrait() ?: run {
                                                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                            }
                                        }
                                    }
                                },
                                isFullscreen = isFullscreen,
                                showChatToggle = true,
                                isChatVisible = (showLandscapeSideChat || showFloatingChat) && !isFullscreen,
                                onToggleChat = {
                                    if (showLandscapeSideChat || showFloatingChat) {
                                        updateLandscapeSideChat(false)
                                        showFloatingChat = false
                                    } else {
                                        updateLandscapeSideChat(true)
                                        showFloatingChat = false
                                    }
                                },
                                onCycleChatMode = { cycleLandscapeChatMode() },
                                onToggleFloatingChat = {
                                    if (showFloatingChat) {
                                        showFloatingChat = false
                                        updateLandscapeSideChat(true)
                                    } else {
                                        showFloatingChat = true
                                        updateLandscapeSideChat(false)
                                    }
                                },
                                onVerticalDragProgress = { handleVerticalDragProgress(it) },
                                onVerticalDragEnd = { vel, total -> handleVerticalDragEnd(vel, total) },
                                adBreakActive = adBreakActive,
                                adBreakRemainingSeconds = adBreakRemaining,
                                isAutoMuteAds = isAutoMuteAds,
                                isShowAdOverlay = isShowAdOverlay,
                                isAuto360pAds = isAuto360pAds,
                                onToggleAutoMuteAds = { playerViewModel.setAutoMuteAds(it) },
                                onToggleShowAdOverlay = { playerViewModel.setShowAdOverlay(it) },
                                onToggleAuto360pAds = { playerViewModel.setAuto360pAds(it) },
                                onDismissAdBreak = { playerViewModel.dismissAdBreak() },
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                            )

                            // Optional Native IRC Chat on Right with Draggable Splitter
                            if (showLandscapeSideChat && !isFullscreen) {
                                val maxChatWidth = (configuration.screenWidthDp * 0.65f).coerceAtLeast(240f)

                                Row(
                                    modifier = Modifier
                                        .fillMaxHeight()
                                        .graphicsLayer {
                                            alpha = (1f - collapseFraction.value * 2.8f).coerceIn(0f, 1f)
                                        }
                                ) {
                                    // Draggable Vertical Splitter Handle
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .width(12.dp)
                                            .background(Color.Black.copy(alpha = 0.5f))
                                            .pointerInput(density) {
                                                detectDragGestures { change, dragAmount ->
                                                    change.consume()
                                                    val deltaDp = dragAmount.x / density.density
                                                    landscapeChatWidthDp = (landscapeChatWidthDp - deltaDp).coerceIn(200f, maxChatWidth)
                                                }
                                            }
                                            .pointerInput(Unit) {
                                                detectTapGestures(
                                                    onDoubleTap = {
                                                        landscapeChatWidthDp = 340f
                                                    }
                                                )
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .width(3.dp)
                                                .height(36.dp)
                                                .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(2.dp))
                                        )
                                    }

                                    Column(
                                        modifier = Modifier
                                            .width(landscapeChatWidthDp.dp)
                                            .fillMaxHeight()
                                            .background(twitchColors.chatBackground)
                                    ) {
                                        ChatHeader(
                                            channel = currentChannel,
                                            onOpenMultiStream = { showMultiStreamPicker = true },
                                            onSwitchToFloating = {
                                                updateLandscapeSideChat(false)
                                                showFloatingChat = true
                                            },
                                            onClose = { updateLandscapeSideChat(false) }
                                        )
                                        NativeChatView(
                                            messages = chatMessages,
                                            emotes = chatEmotes,
                                            structuredEmotes = chatStructuredEmotes,
                                            currentUser = currentUser,
                                            onSendMessage = { text -> chatViewModel.sendMessage(text, currentUser) },
                                            onOpenLogin = { showLoginDialog = true },
                                            listState = chatListState,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        // Portrait: Video at Top (16:9), Streamer Details + Native Chat below
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                                .imePadding()
                        ) {
                            // Video container centered with black background
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(portraitVideoHeightDp.dp)
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                Box(
                                    modifier = Modifier
                                        .aspectRatio(16f / 9f, matchHeightConstraintsFirst = portraitVideoHeightDp < defaultVideoHeightDp)
                                ) {
                                    NativeTwitchPlayer(
                                        exoPlayer = playerViewModel.exoPlayer,
                                        streamInfo = streamInfo,
                                        channelName = currentChannel,
                                        isLoading = isLoading,
                                        errorMessage = errorMessage,
                                        availableQualities = availableQualities,
                                        selectedQuality = selectedQuality,
                                        isAudioOnly = isAudioOnly,
                                        isLowLatency = isLowLatency,
                                        lowLatencyBufferMs = lowLatencyBufferMs,
                                        isPipEnabled = isPipEnabled,
                                        backgroundAudioEnabled = backgroundAudioEnabled,
                                        isOledMode = isOledMode,
                                        onToggleOledMode = {
                                            isOledMode = it
                                            settingsManager.setOledMode(it)
                                        },
                                        onSelectQuality = { playerViewModel.selectQuality(it) },
                                        onToggleAudioOnly = { playerViewModel.toggleAudioOnly() },
                                        onToggleLowLatency = { playerViewModel.toggleLowLatency() },
                                        onSelectLowLatencyBuffer = { playerViewModel.setLowLatencyBuffer(it) },
                                        onTogglePipEnabled = { playerViewModel.setPipEnabled(it) },
                                        onToggleBackgroundAudio = { playerViewModel.setBackgroundAudio(it) },
                                        onMinimize = { minimizeToMiniPlayer() },
                                        onToggleFullscreen = {
                                            orientationManager?.requestLandscape() ?: run {
                                                (context as? Activity)?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                            }
                                        },
                                        isFullscreen = isFullscreen,
                                        showChatToggle = true,
                                        isChatVisible = showPortraitChat && !showFloatingChat,
                                        onToggleChat = {
                                            if (showFloatingChat) {
                                                showFloatingChat = false
                                                showPortraitChat = true
                                            } else {
                                                showPortraitChat = !showPortraitChat
                                            }
                                        },
                                        onCycleChatMode = { cyclePortraitChatMode() },
                                        onToggleFloatingChat = {
                                            if (showFloatingChat) {
                                                showFloatingChat = false
                                                showPortraitChat = true
                                            } else {
                                                showFloatingChat = true
                                            }
                                        },
                                        onVerticalDragProgress = { handleVerticalDragProgress(it) },
                                        onVerticalDragEnd = { vel, total -> handleVerticalDragEnd(vel, total) },
                                        adBreakActive = adBreakActive,
                                        adBreakRemainingSeconds = adBreakRemaining,
                                        isAutoMuteAds = isAutoMuteAds,
                                        isShowAdOverlay = isShowAdOverlay,
                                        isAuto360pAds = isAuto360pAds,
                                        onToggleAutoMuteAds = { playerViewModel.setAutoMuteAds(it) },
                                        onToggleShowAdOverlay = { playerViewModel.setShowAdOverlay(it) },
                                        onToggleAuto360pAds = { playerViewModel.setAuto360pAds(it) },
                                        onDismissAdBreak = { playerViewModel.dismissAdBreak() },
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                                    .graphicsLayer {
                                        val p = collapseFraction.value
                                        alpha = (1f - p * 2.8f).coerceIn(0f, 1f)
                                        translationY = p * with(density) { 40.dp.toPx() }
                                        translationX = -p * with(density) { 20.dp.toPx() }
                                    }
                            ) {
                                // Draggable Horizontal Splitter Handle
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(14.dp)
                                        .background(twitchColors.background)
                                        .pointerInput(density) {
                                            detectDragGestures { change, dragAmount ->
                                                change.consume()
                                                val deltaDp = dragAmount.y / density.density
                                                val minH = 120f
                                                val maxH = (configuration.screenHeightDp * 0.65f).coerceAtLeast(minH)
                                                portraitVideoHeightDp = (portraitVideoHeightDp + deltaDp).coerceIn(minH, maxH)
                                            }
                                        }
                                        .pointerInput(Unit) {
                                            detectTapGestures(
                                                onDoubleTap = {
                                                    portraitVideoHeightDp = defaultVideoHeightDp
                                                }
                                            )
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .width(44.dp)
                                            .height(4.dp)
                                            .background(Color.White.copy(alpha = 0.35f), RoundedCornerShape(2.dp))
                                    )
                                }

                                // Streamer Info Bar (hidden while soft keyboard is visible to preserve space and keep video completely visible)
                                val isImeVisible = WindowInsets.isImeVisible
                                if (!isImeVisible) {
                                    StreamerDetailBar(
                                        streamInfo = streamInfo,
                                        channelName = currentChannel,
                                        onOpenMultiStream = { showMultiStreamPicker = true },
                                        onToggleFloatingChat = {
                                            showFloatingChat = !showFloatingChat
                                            if (showFloatingChat) {
                                                updateLandscapeSideChat(false)
                                            }
                                        }
                                    )
                                }

                                // Embedded Native Chat, Floating Notice, or Hidden Chat (never blocks video)
                                if (showFloatingChat) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(twitchColors.background),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(10.dp),
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.PictureInPicture,
                                                contentDescription = null,
                                                tint = TwitchPurple,
                                                modifier = Modifier.size(44.dp)
                                            )
                                            Text(
                                                text = "Chat is floating on screen",
                                                color = Color.White,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "You can drag, resize, or minimize the floating chat window",
                                                color = TwitchTextDim,
                                                fontSize = 12.sp,
                                                textAlign = TextAlign.Center
                                            )
                                            Button(
                                                onClick = {
                                                    showFloatingChat = false
                                                    showPortraitChat = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Icon(Icons.Filled.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(16.dp))
                                                Spacer(Modifier.width(6.dp))
                                                Text("Dock Chat Below Video", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                } else if (showPortraitChat) {
                                    NativeChatView(
                                        messages = chatMessages,
                                        emotes = chatEmotes,
                                        structuredEmotes = chatStructuredEmotes,
                                        currentUser = currentUser,
                                        onSendMessage = { text -> chatViewModel.sendMessage(text, currentUser) },
                                        onOpenLogin = { showLoginDialog = true },
                                        listState = chatListState,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                    )
                                } else {
                                    // Chat is hidden in portrait
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .weight(1f)
                                            .background(twitchColors.background),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.padding(24.dp)
                                        ) {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Chat,
                                                contentDescription = null,
                                                tint = TwitchTextDim,
                                                modifier = Modifier.size(40.dp)
                                            )
                                            Text(
                                                text = "Chat is hidden",
                                                color = Color.White,
                                                fontWeight = FontWeight.SemiBold,
                                                fontSize = 15.sp
                                            )
                                            Text(
                                                text = "Double-tap video to show chat",
                                                color = TwitchTextDim,
                                                fontSize = 12.sp
                                            )
                                            Spacer(Modifier.height(4.dp))
                                            Button(
                                                onClick = { showPortraitChat = true },
                                                colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple),
                                                shape = RoundedCornerShape(8.dp)
                                            ) {
                                                Text("Show Chat", fontSize = 12.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Optional Floating Resizable Chat Overlay
                if (showFloatingChat) {
                    FloatingResizableChat(
                        channelName = currentChannel,
                        availableChannels = listOf(currentChannel),
                        messages = chatMessages,
                        emotes = chatEmotes,
                        structuredEmotes = chatStructuredEmotes,
                        currentUser = currentUser,
                        onSendMessage = { text -> chatViewModel.sendMessage(text, currentUser) },
                        onDock = {
                            showFloatingChat = false
                            if (isLandscape) updateLandscapeSideChat(true) else showPortraitChat = true
                        },
                        onClose = { showFloatingChat = false },
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                alpha = (1f - collapseFraction.value * 2.8f).coerceIn(0f, 1f)
                            }
                    )
                }
            }
        }
    }
}



@Composable
private fun StreamerDetailBar(
    streamInfo: com.eetu.twitchapp.data.model.LiveStreamItem?,
    channelName: String,
    onOpenMultiStream: () -> Unit,
    onToggleFloatingChat: () -> Unit
) {
    val twitchColors = LocalTwitchColors.current
    var isTitleExpanded by remember { mutableStateOf(false) }

    Surface(
        color = twitchColors.card,
        shadowElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clickable { isTitleExpanded = !isTitleExpanded },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (streamInfo != null && streamInfo.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(streamInfo.profileImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = streamInfo?.displayName ?: channelName,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (streamInfo != null && streamInfo.gameName.isNotEmpty()) {
                            Text(
                                text = "• ${streamInfo.gameName}",
                                color = TwitchTeal,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (streamInfo != null && streamInfo.title.isNotEmpty()) {
                        Text(
                            text = streamInfo.title,
                            color = TwitchTextDim,
                            fontSize = 11.sp,
                            maxLines = if (isTitleExpanded) 4 else 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = if (!isTitleExpanded) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier
                        )
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                // Multistream quick button
                IconButton(onClick = onOpenMultiStream) {
                    Icon(
                        Icons.Filled.GridView,
                        contentDescription = "Multistream",
                        tint = TwitchTeal,
                        modifier = Modifier.size(20.dp)
                    )
                }

                // Floating Chat button
                IconButton(onClick = onToggleFloatingChat) {
                    Icon(
                        Icons.AutoMirrored.Filled.Chat,
                        contentDescription = "Toggle Floating Chat",
                        tint = TwitchPurple,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatHeader(
    channel: String,
    onOpenMultiStream: () -> Unit,
    onSwitchToFloating: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null
) {
    val twitchColors = LocalTwitchColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(twitchColors.card)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "Stream Chat",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            // Switch to floating chat button
            if (onSwitchToFloating != null) {
                IconButton(
                    onClick = onSwitchToFloating,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.PictureInPicture,
                        contentDescription = "Float Chat",
                        tint = TwitchPurple,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            IconButton(
                onClick = onOpenMultiStream,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    Icons.Filled.GridView,
                    contentDescription = "Multistream",
                    tint = TwitchTeal,
                    modifier = Modifier.size(18.dp)
                )
            }

            if (onClose != null) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Hide Chat",
                        tint = TwitchTextDim,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
