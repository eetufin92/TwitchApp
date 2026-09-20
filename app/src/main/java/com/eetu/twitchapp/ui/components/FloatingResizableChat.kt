package com.eetu.twitchapp.ui.components

import android.annotation.SuppressLint
import android.content.Context
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eetu.twitchapp.ui.chat.ChatViewModel
import com.eetu.twitchapp.ui.chat.NativeChatView
import com.eetu.twitchapp.ui.theme.*
import kotlin.math.roundToInt

@Composable
fun FloatingResizableChat(
    channelName: String,
    availableChannels: List<String> = emptyList(),
    onChannelSelected: (String) -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current

    var activeChannel by remember(channelName) { mutableStateOf(channelName.ifEmpty { "twitch" }) }
    var isMinimized by remember { mutableStateOf(false) }
    var opacity by remember { mutableFloatStateOf(0.92f) }

    val chatViewModel = remember { ChatViewModel() }
    val messages by chatViewModel.messages.collectAsState()
    val emotes by chatViewModel.emotes.collectAsState()

    LaunchedEffect(activeChannel) {
        chatViewModel.setChannel(activeChannel)
    }

    // Position state in pixels
    var offsetX by remember { mutableFloatStateOf(60f) }
    var offsetY by remember { mutableFloatStateOf(160f) }

    // Size state in dp
    var chatWidth by remember { mutableStateOf(340.dp) }
    var chatHeight by remember { mutableStateOf(480.dp) }

    val minWidth = 260.dp
    val maxWidth = 600.dp
    val minHeight = 300.dp
    val maxHeight = 800.dp

    if (isMinimized) {
        // Minimized floating bubble
        Box(
            modifier = modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .pointerInput(Unit) {
                        detectDragGestures { change, dragAmount ->
                            change.consume()
                            offsetX += dragAmount.x
                            offsetY += dragAmount.y
                        }
                    }
                    .clip(CircleShape)
                    .background(TwitchPurple)
                    .border(2.dp, Color.White.copy(alpha = 0.8f), CircleShape)
                    .clickable { isMinimized = false }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Chat,
                        contentDescription = "Restore Chat",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = activeChannel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        }
    } else {
        // Expanded floating resizable chat window
        Box(
            modifier = modifier.fillMaxSize()
        ) {
            Surface(
                modifier = Modifier
                    .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                    .size(width = chatWidth, height = chatHeight)
                    .alpha(opacity)
                    .border(1.dp, TwitchPurple.copy(alpha = 0.5f), RoundedCornerShape(14.dp)),
                shape = RoundedCornerShape(14.dp),
                color = TwitchDarkSurface,
                tonalElevation = 8.dp,
                shadowElevation = 12.dp
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Header (Drag handle)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(TwitchDarkCard)
                            .pointerInput(Unit) {
                                detectDragGestures { change, dragAmount ->
                                    change.consume()
                                    offsetX += dragAmount.x
                                    offsetY += dragAmount.y
                                }
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag Window",
                                tint = TwitchTextDim,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Chat: $activeChannel",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            // Opacity toggle
                            IconButton(
                                onClick = {
                                    opacity = when {
                                        opacity > 0.85f -> 0.65f
                                        opacity > 0.55f -> 0.40f
                                        else -> 0.95f
                                    }
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Opacity,
                                    contentDescription = "Toggle Opacity",
                                    tint = TwitchTextDim,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Minimize button
                            IconButton(
                                onClick = { isMinimized = true },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Remove,
                                    contentDescription = "Minimize",
                                    tint = TwitchTextDim,
                                    modifier = Modifier.size(16.dp)
                                )
                            }

                            // Close button
                            IconButton(
                                onClick = onClose,
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Close",
                                    tint = TwitchTextDim,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }

                    // Channel selector tabs if multistreaming
                    if (availableChannels.size > 1) {
                        ScrollableTabRow(
                            selectedTabIndex = availableChannels.indexOf(activeChannel).coerceAtLeast(0),
                            containerColor = TwitchDark,
                            contentColor = TwitchPurple,
                            edgePadding = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availableChannels.forEach { ch ->
                                Tab(
                                    selected = activeChannel == ch,
                                    onClick = {
                                        activeChannel = ch
                                        onChannelSelected(ch)
                                    },
                                    text = {
                                        Text(
                                            text = ch,
                                            fontSize = 11.sp,
                                            color = if (activeChannel == ch) Color.White else TwitchTextDim
                                        )
                                    }
                                )
                            }
                        }
                    }

                    // Native IRC Chat Content
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        NativeChatView(
                            messages = messages,
                            emotes = emotes,
                            modifier = Modifier.fillMaxSize()
                        )

                        // Bottom-right resize handle
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(32.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        val deltaWidth = with(density) { dragAmount.x.toDp() }
                                        val deltaHeight = with(density) { dragAmount.y.toDp() }

                                        chatWidth = (chatWidth + deltaWidth).coerceIn(minWidth, maxWidth)
                                        chatHeight = (chatHeight + deltaHeight).coerceIn(minHeight, maxHeight)
                                    }
                                }
                                .padding(4.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Icon(
                                Icons.Filled.OpenInFull,
                                contentDescription = "Resize Chat",
                                tint = TwitchPurple,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
