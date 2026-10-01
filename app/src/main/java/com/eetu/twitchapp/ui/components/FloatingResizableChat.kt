package com.eetu.twitchapp.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Opacity
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.data.model.ChatMessage
import com.eetu.twitchapp.data.model.EmoteItem
import com.eetu.twitchapp.data.model.TwitchUser
import com.eetu.twitchapp.ui.chat.ChatViewModel
import com.eetu.twitchapp.ui.chat.NativeChatView
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun FloatingResizableChat(
    channelName: String,
    availableChannels: List<String> = emptyList(),
    messages: List<ChatMessage>? = null,
    emotes: Map<String, String>? = null,
    structuredEmotes: List<EmoteItem>? = null,
    currentUser: TwitchUser? = null,
    onSendMessage: ((String) -> Unit)? = null,
    onChannelSelected: (String) -> Unit = {},
    onDock: (() -> Unit)? = null,
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val settingsManager = remember { TwitchSettingsManager(context) }

    var activeChannel by remember(channelName) { mutableStateOf(channelName.ifEmpty { "twitch" }) }
    var opacity by remember { mutableFloatStateOf(settingsManager.getChatOpacity()) }
    val chatFontSizeSp = settingsManager.getChatFontSizeSp()

    // Inactivity timer state: auto-hides title bar, border, and resize handle
    var isControlsVisible by remember { mutableStateOf(true) }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Dropdown slider state: button toggles slider, auto-closes after inactivity
    var showSlider by remember { mutableStateOf(false) }
    var lastSliderInteraction by remember { mutableLongStateOf(0L) }

    LaunchedEffect(lastInteractionTime, isControlsVisible) {
        if (isControlsVisible) {
            delay(3500)
            isControlsVisible = false
            showSlider = false
        }
    }

    LaunchedEffect(showSlider, lastSliderInteraction) {
        if (showSlider) {
            delay(3500)
            showSlider = false
        }
    }

    val internalChatViewModel = remember(activeChannel) { ChatViewModel() }
    val internalMessages by internalChatViewModel.messages.collectAsState()
    val internalEmotes by internalChatViewModel.emotes.collectAsState()
    val internalStructuredEmotes by internalChatViewModel.structuredEmotes.collectAsState()

    LaunchedEffect(activeChannel) {
        if (messages == null) {
            internalChatViewModel.setChannel(activeChannel)
        }
    }

    val displayMessages = messages ?: internalMessages
    val displayEmotes = emotes ?: internalEmotes
    val displayStructuredEmotes = structuredEmotes ?: internalStructuredEmotes

    // Position state in pixels
    var offsetX by remember { mutableFloatStateOf(60f) }
    var offsetY by remember { mutableFloatStateOf(160f) }

    // Size state in dp
    var chatWidth by remember { mutableStateOf(240.dp) }
    var chatHeight by remember { mutableStateOf(240.dp) }

    val minWidth = 160.dp
    val maxWidth = 600.dp
    val minHeight = 140.dp
    val maxHeight = 800.dp

    val borderModifier = if (isControlsVisible) {
        Modifier.border(0.5.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(8.dp))
    } else {
        Modifier
    }

    Box(modifier = modifier.fillMaxSize()) {
        Surface(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
                .size(width = chatWidth, height = chatHeight)
                .then(borderModifier)
                .pointerInput(Unit) {
                    awaitPointerEventScope {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.any { it.pressed }) {
                                lastInteractionTime = System.currentTimeMillis()
                                isControlsVisible = true
                            }
                        }
                    }
                },
            shape = RoundedCornerShape(8.dp),
            color = if (opacity > 0f) TwitchDark.copy(alpha = opacity) else Color.Transparent,
            tonalElevation = 0.dp,
            shadowElevation = 0.dp
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Title Bar / Header (auto-hides on inactivity, wakes up on touch)
                AnimatedVisibility(
                    visible = isControlsVisible,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val headerBg = if (opacity > 0f) {
                        Color.Black.copy(alpha = 0.55f)
                    } else {
                        Color.Black.copy(alpha = 0.4f)
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(headerBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        // Drag handle & channel name (spans full width to make dragging effortless)
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        lastInteractionTime = System.currentTimeMillis()
                                        isControlsVisible = true
                                        offsetX += dragAmount.x
                                        offsetY += dragAmount.y
                                    }
                                }
                                .padding(vertical = 4.dp)
                        ) {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Drag Window",
                                tint = Color.White.copy(alpha = 0.45f),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = activeChannel,
                                color = Color.White.copy(alpha = 0.8f),
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        // Compact button to open the transparency slider tray
                        Surface(
                            onClick = {
                                lastInteractionTime = System.currentTimeMillis()
                                isControlsVisible = true
                                showSlider = !showSlider
                                if (showSlider) {
                                    lastSliderInteraction = System.currentTimeMillis()
                                }
                            },
                            shape = RoundedCornerShape(4.dp),
                            color = if (showSlider) TwitchPurple.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.1f),
                            border = BorderStroke(0.5.dp, if (showSlider) TwitchPurple else Color.White.copy(alpha = 0.2f)),
                            modifier = Modifier.height(22.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Opacity,
                                    contentDescription = "Transparency",
                                    tint = if (opacity > 0f) TwitchPurple else Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(12.dp)
                                )
                                Text(
                                    text = "${(opacity * 100).toInt()}%",
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Dropdown Transparency Slider Tray (auto-closes after a while of inactivity)
                AnimatedVisibility(
                    visible = isControlsVisible && showSlider,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.85f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Opacity,
                                contentDescription = null,
                                tint = if (opacity > 0f) TwitchPurple else Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(15.dp)
                            )
                            Slider(
                                value = opacity,
                                onValueChange = {
                                    lastSliderInteraction = System.currentTimeMillis()
                                    lastInteractionTime = System.currentTimeMillis()
                                    opacity = it
                                    settingsManager.setChatOpacity(it)
                                },
                                valueRange = 0.0f..1.0f,
                                colors = SliderDefaults.colors(
                                    thumbColor = TwitchPurple,
                                    activeTrackColor = TwitchPurple,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                ),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(22.dp)
                            )
                            Text(
                                text = "${(opacity * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(32.dp),
                                textAlign = TextAlign.End
                            )
                        }
                    }
                }

                // Channel selector tabs if multistreaming (also auto-hides when idle)
                if (availableChannels.size > 1) {
                    AnimatedVisibility(
                        visible = isControlsVisible,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        ScrollableTabRow(
                            selectedTabIndex = availableChannels.indexOf(activeChannel).coerceAtLeast(0),
                            containerColor = if (opacity > 0f) TwitchDark.copy(alpha = 0.7f) else Color.Black.copy(alpha = 0.35f),
                            contentColor = TwitchPurple,
                            edgePadding = 4.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            availableChannels.forEach { ch ->
                                Tab(
                                    selected = activeChannel == ch,
                                    onClick = {
                                        lastInteractionTime = System.currentTimeMillis()
                                        isControlsVisible = true
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
                }

                // Native IRC Chat Content (Text only / Transparent, no input box)
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    NativeChatView(
                        messages = displayMessages,
                        emotes = displayEmotes,
                        structuredEmotes = displayStructuredEmotes,
                        fontSizeSp = chatFontSizeSp,
                        currentUser = null,
                        onSendMessage = null,
                        showInput = false,
                        backgroundColor = Color.Transparent,
                        modifier = Modifier.fillMaxSize()
                    )

                    // Bottom-right resize handle (auto-hides with controls)
                    if (isControlsVisible) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomEnd)
                                .size(24.dp)
                                .pointerInput(Unit) {
                                    detectDragGestures { change, dragAmount ->
                                        change.consume()
                                        lastInteractionTime = System.currentTimeMillis()
                                        isControlsVisible = true
                                        val deltaWidth = with(density) { dragAmount.x.toDp() }
                                        val deltaHeight = with(density) { dragAmount.y.toDp() }

                                        chatWidth = (chatWidth + deltaWidth).coerceIn(minWidth, maxWidth)
                                        chatHeight = (chatHeight + deltaHeight).coerceIn(minHeight, maxHeight)
                                    }
                                }
                                .padding(3.dp),
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            Icon(
                                Icons.Filled.OpenInFull,
                                contentDescription = "Resize Chat",
                                tint = Color.White.copy(alpha = 0.35f),
                                modifier = Modifier.size(11.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
