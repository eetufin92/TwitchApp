package com.eetu.twitchapp.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.animation.*
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.eetu.twitchapp.ui.chat.ChatViewModel
import com.eetu.twitchapp.ui.chat.NativeChatView
import com.eetu.twitchapp.ui.components.FloatingResizableChat
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun CollapsiblePlayerScaffold(
    playerViewModel: PlayerViewModel,
    onOpenMultiStream: (String) -> Unit,
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

    val chatViewModel = remember { ChatViewModel() }
    val chatMessages by chatViewModel.messages.collectAsState()
    val chatEmotes by chatViewModel.emotes.collectAsState()

    var showFloatingChat by remember { mutableStateOf(false) }
    var showLandscapeSideChat by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }

    LaunchedEffect(currentChannel) {
        if (currentChannel.isNotEmpty()) {
            chatViewModel.setChannel(currentChannel)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Base Content (Home Screen, Channels List, Search, etc.)
        content()

        // Active Player Layer
        if (currentChannel.isNotEmpty()) {
            if (isMiniPlayer) {
                // Docked Miniplayer Bar at the Bottom
                var miniDragOffsetY by remember { mutableFloatStateOf(0f) }

                Surface(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(68.dp)
                        .offset { IntOffset(0, miniDragOffsetY.roundToInt().coerceAtLeast(0)) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                // Swiping up expands to full screen
                                if (delta < -15) {
                                    playerViewModel.setMiniPlayer(false)
                                }
                            }
                        )
                        .shadow(12.dp)
                        .clickable { playerViewModel.setMiniPlayer(false) },
                    color = twitchColors.card
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // 16:9 Video Mini Preview
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
            } else {
                // Full Stream View with Smooth Swipe-Down to dock
                val animatableDragY = remember { Animatable(0f) }
                val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
                val dismissThresholdPx = screenHeightPx * 0.38f

                val playerDragModifier = Modifier.draggable(
                    orientation = Orientation.Vertical,
                    state = rememberDraggableState { delta ->
                        coroutineScope.launch {
                            val next = (animatableDragY.value + delta).coerceAtLeast(0f)
                            animatableDragY.snapTo(next)
                        }
                    },
                    onDragStopped = { velocity ->
                        coroutineScope.launch {
                            if (animatableDragY.value > dismissThresholdPx || velocity > 1400f) {
                                animatableDragY.animateTo(screenHeightPx, tween(180))
                                playerViewModel.setMiniPlayer(true)
                                animatableDragY.snapTo(0f)
                            } else {
                                animatableDragY.animateTo(0f, tween(200))
                            }
                        }
                    }
                )

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, animatableDragY.value.roundToInt().coerceAtLeast(0)) }
                        .background(twitchColors.background)
                ) {
                    if (isLandscape) {
                        // Landscape / Tablet Split: Video on Left, Optional Chat on Right
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
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
                                onTogglePipEnabled = { playerViewModel.setPipEnabled(it) },
                                onToggleBackgroundAudio = { playerViewModel.setBackgroundAudio(it) },
                                onMinimize = { playerViewModel.setMiniPlayer(true) },
                                onToggleFullscreen = {
                                    val act = context as? Activity
                                    act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                },
                                isFullscreen = isFullscreen,
                                showChatToggle = true,
                                isChatVisible = showLandscapeSideChat || showFloatingChat,
                                onToggleChat = {
                                    // Cycles cleanly: None -> Side-Bar -> Floating -> None
                                    if (!showLandscapeSideChat && !showFloatingChat) {
                                        showLandscapeSideChat = true
                                        showFloatingChat = false
                                    } else if (showLandscapeSideChat) {
                                        showLandscapeSideChat = false
                                        showFloatingChat = true
                                    } else {
                                        showLandscapeSideChat = false
                                        showFloatingChat = false
                                    }
                                },
                                modifier = if (showLandscapeSideChat) {
                                    Modifier
                                        .weight(1.8f)
                                        .fillMaxHeight()
                                } else {
                                    Modifier.fillMaxSize()
                                }
                            )

                            // Optional Native IRC Chat on Right
                            if (showLandscapeSideChat) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .background(twitchColors.chatBackground)
                                ) {
                                    ChatHeader(
                                        channel = currentChannel,
                                        onOpenMultiStream = { onOpenMultiStream(currentChannel) },
                                        onSwitchToFloating = {
                                            showLandscapeSideChat = false
                                            showFloatingChat = true
                                        },
                                        onClose = { showLandscapeSideChat = false }
                                    )
                                    NativeChatView(
                                        messages = chatMessages,
                                        emotes = chatEmotes,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    } else {
                        // Portrait: Video at Top (~235dp), Streamer Details + Native Chat below
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .statusBarsPadding()
                        ) {
                            // Draggable video container
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(235.dp)
                                    .then(playerDragModifier)
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
                                    onTogglePipEnabled = { playerViewModel.setPipEnabled(it) },
                                    onToggleBackgroundAudio = { playerViewModel.setBackgroundAudio(it) },
                                    onMinimize = { playerViewModel.setMiniPlayer(true) },
                                    onToggleFullscreen = {
                                        val act = context as? Activity
                                        act?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                    },
                                    isFullscreen = isFullscreen,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            // Streamer Info Bar
                            StreamerDetailBar(
                                streamInfo = streamInfo,
                                channelName = currentChannel,
                                onOpenMultiStream = { onOpenMultiStream(currentChannel) },
                                onToggleFloatingChat = {
                                    showFloatingChat = !showFloatingChat
                                    if (showFloatingChat) {
                                        showLandscapeSideChat = false
                                    }
                                }
                            )

                            // Embedded Native Chat or Floating Notice (never both at same time)
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
                                            onClick = { showFloatingChat = false },
                                            colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple),
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Icon(Icons.Filled.VerticalAlignBottom, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Dock Chat Below Video", fontSize = 12.sp)
                                        }
                                    }
                                }
                            } else {
                                NativeChatView(
                                    messages = chatMessages,
                                    emotes = chatEmotes,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .weight(1f)
                                )
                            }
                        }
                    }

                    // Optional Floating Resizable Chat Overlay
                    if (showFloatingChat) {
                        FloatingResizableChat(
                            channelName = currentChannel,
                            availableChannels = listOf(currentChannel),
                            onDock = {
                                showFloatingChat = false
                                if (isLandscape) showLandscapeSideChat = true
                            },
                            onClose = { showFloatingChat = false },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
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
                modifier = Modifier.weight(1f),
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

                Column {
                    Text(
                        text = streamInfo?.displayName ?: channelName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = streamInfo?.gameName?.ifEmpty { streamInfo.title } ?: "Streaming live",
                        color = TwitchTeal,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
