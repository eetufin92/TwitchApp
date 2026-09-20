package com.eetu.twitchapp.ui.chat

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.data.model.ChatBadge
import com.eetu.twitchapp.data.model.ChatMessage
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.launch

@Composable
fun NativeChatView(
    messages: List<ChatMessage>,
    emotes: Map<String, String>,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 13f
) {
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    var userScrolledUp by remember { mutableStateOf(false) }

    // Check if user is at or very near the bottom
    val isAtBottom by remember {
        derivedStateOf {
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val totalItems = listState.layoutInfo.totalItemsCount
            if (visibleItems.isEmpty() || totalItems == 0) true
            else {
                val lastVisible = visibleItems.last().index
                lastVisible >= totalItems - 2
            }
        }
    }

    // Whenever at bottom, reset userScrolledUp
    LaunchedEffect(listState) {
        snapshotFlow { isAtBottom }.collect { atBottom ->
            if (atBottom) {
                userScrolledUp = false
            }
        }
    }

    // When scrolling and not at bottom, mark as user scrolled up
    LaunchedEffect(listState.isScrollInProgress) {
        if (listState.isScrollInProgress && !isAtBottom) {
            userScrolledUp = true
        }
    }

    // Auto-scroll to bottom on new messages if user hasn't scrolled up
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty() && !userScrolledUp) {
            listState.scrollToItem(messages.size - 1)
        }
    }

    val showScrollButton by remember {
        derivedStateOf {
            userScrolledUp && !isAtBottom && messages.isNotEmpty()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(TwitchDarkChat)
    ) {
        if (messages.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Welcome to the chat room!",
                    color = TwitchTextDim,
                    fontSize = 12.sp
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageRow(
                        message = message,
                        emotes = emotes,
                        fontSizeSp = fontSizeSp
                    )
                }
            }
        }

        // Floating "More messages below" Button
        AnimatedVisibility(
            visible = showScrollButton,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 8.dp)
        ) {
            Surface(
                color = TwitchPurple,
                shape = CircleShape,
                shadowElevation = 4.dp,
                modifier = Modifier.clickable {
                    userScrolledUp = false
                    coroutineScope.launch {
                        if (messages.isNotEmpty()) {
                            listState.scrollToItem(messages.size - 1)
                        }
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        Icons.Filled.ArrowDownward,
                        contentDescription = "Scroll to bottom",
                        tint = Color.White,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "More messages below",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChatMessageRow(
    message: ChatMessage,
    emotes: Map<String, String>,
    fontSizeSp: Float
) {
    val userColor = remember(message.color) {
        parseHexColor(message.color)
    }

    // Split message into words to check against 7TV/BTTV/FFZ emotes
    val words = remember(message.text) {
        message.text.split(" ").filter { it.isNotEmpty() }
    }

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalArrangement = Arrangement.Center
    ) {
        // Badges
        message.badges.forEach { badge ->
            BadgeIcon(badge)
        }

        // Username
        Text(
            text = "${message.displayName}:",
            color = userColor,
            fontWeight = FontWeight.Bold,
            fontSize = fontSizeSp.sp
        )

        // Message text words & inline emotes
        words.forEach { word ->
            val emoteUrl = emotes[word]
            if (emoteUrl != null) {
                // 7TV, BTTV, or FFZ Emote!
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(emoteUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = word,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(26.dp)
                        .widthIn(min = 18.dp, max = 56.dp)
                )
            } else {
                Text(
                    text = word,
                    color = Color.White,
                    fontSize = fontSizeSp.sp,
                    lineHeight = (fontSizeSp + 4).sp
                )
            }
        }
    }
}

@Composable
private fun BadgeIcon(badge: ChatBadge) {
    when (badge.name.lowercase()) {
        "moderator" -> {
            Icon(
                Icons.Filled.Shield,
                contentDescription = "Mod",
                tint = TwitchGreen,
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
        "vip" -> {
            Icon(
                Icons.Filled.Star,
                contentDescription = "VIP",
                tint = Color(0xFFE040FB),
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(2.dp))
            )
        }
        "subscriber" -> {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TwitchPurple),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "★",
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        "broadcaster" -> {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(TwitchRed),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 7.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

private fun parseHexColor(hex: String): Color {
    return try {
        val clean = if (hex.startsWith("#")) hex.substring(1) else hex
        val colorInt = clean.toLong(16)
        if (clean.length == 6) {
            Color(colorInt or 0x00000000FF000000)
        } else {
            Color(colorInt)
        }
    } catch (e: Exception) {
        TwitchTeal
    }
}
