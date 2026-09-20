package com.eetu.twitchapp.ui.player

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.ui.chat.ChatViewModel
import com.eetu.twitchapp.ui.chat.NativeChatView
import com.eetu.twitchapp.ui.components.FloatingResizableChat
import com.eetu.twitchapp.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun CollapsiblePlayerScaffold(
    playerViewModel: PlayerViewModel,
    onOpenMultiStream: (String) -> Unit,
    onOpenSettings: () -> Unit,
    content: @Composable () -> Unit
) {
    val currentChannel by playerViewModel.currentChannel.collectAsState()
    val streamInfo by playerViewModel.streamInfo.collectAsState()
    val isMiniPlayer by playerViewModel.isMiniPlayer.collectAsState()
    val isLoading by playerViewModel.isLoading.collectAsState()
    val errorMessage by playerViewModel.errorMessage.collectAsState()
    val isPlaying by playerViewModel.isPlaying.collectAsState()

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val chatViewModel = remember { ChatViewModel() }
    val chatMessages by chatViewModel.messages.collectAsState()
    val chatEmotes by chatViewModel.emotes.collectAsState()

    var showFloatingChat by remember { mutableStateOf(false) }

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
                    color = TwitchDarkCard
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
                                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                                        setBackgroundColor(android.graphics.Color.BLACK)
                                    }
                                },
                                update = { pv ->
                                    pv.player = playerViewModel.exoPlayer
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
                // Full Stream View with Swipe-Down to dock
                var dragOffsetY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .offset { IntOffset(0, dragOffsetY.roundToInt().coerceAtLeast(0)) }
                        .draggable(
                            orientation = Orientation.Vertical,
                            state = rememberDraggableState { delta ->
                                dragOffsetY += delta
                                // If dragged down past threshold, snap into miniplayer!
                                if (dragOffsetY > 160f) {
                                    dragOffsetY = 0f
                                    playerViewModel.setMiniPlayer(true)
                                }
                            },
                            onDragStopped = {
                                dragOffsetY = 0f
                            }
                        )
                        .background(TwitchDark)
                ) {
                    if (isLandscape) {
                        // Landscape / Tablet Split: Video on Left, Chat on Right
                        Row(modifier = Modifier.fillMaxSize()) {
                            NativeTwitchPlayer(
                                exoPlayer = playerViewModel.exoPlayer,
                                streamInfo = streamInfo,
                                channelName = currentChannel,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onMinimize = { playerViewModel.setMiniPlayer(true) },
                                modifier = Modifier
                                    .weight(1.8f)
                                    .fillMaxHeight()
                            )

                            // Native IRC Chat on Right
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .background(TwitchDarkChat)
                            ) {
                                ChatHeader(
                                    channel = currentChannel,
                                    onOpenMultiStream = { onOpenMultiStream(currentChannel) }
                                )
                                NativeChatView(
                                    messages = chatMessages,
                                    emotes = chatEmotes,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    } else {
                        // Portrait: Video at Top (~230dp), Streamer Details + Native Chat below
                        Column(modifier = Modifier.fillMaxSize()) {
                            NativeTwitchPlayer(
                                exoPlayer = playerViewModel.exoPlayer,
                                streamInfo = streamInfo,
                                channelName = currentChannel,
                                isLoading = isLoading,
                                errorMessage = errorMessage,
                                onMinimize = { playerViewModel.setMiniPlayer(true) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(235.dp)
                            )

                            // Streamer Info Bar
                            StreamerDetailBar(
                                streamInfo = streamInfo,
                                channelName = currentChannel,
                                onOpenMultiStream = { onOpenMultiStream(currentChannel) },
                                onToggleFloatingChat = { showFloatingChat = !showFloatingChat }
                            )

                            // Embedded Native Chat
                            NativeChatView(
                                messages = chatMessages,
                                emotes = chatEmotes,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f)
                            )
                        }
                    }

                    // Optional Floating Resizable Chat Overlay
                    if (showFloatingChat) {
                        FloatingResizableChat(
                            channelName = currentChannel,
                            availableChannels = listOf(currentChannel),
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
    Surface(
        color = TwitchDarkCard,
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
                            .clip(androidx.compose.foundation.shape.CircleShape)
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
                        Icons.Filled.Chat,
                        contentDescription = "Floating Chat",
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
    onOpenMultiStream: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(TwitchDarkCard)
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
    }
}
