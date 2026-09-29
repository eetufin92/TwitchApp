package com.eetu.twitchapp.ui.chat

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Mood
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.data.model.ChatBadge
import com.eetu.twitchapp.data.model.ChatMessage
import com.eetu.twitchapp.data.model.TwitchUser
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NativeChatView(
    messages: List<ChatMessage>,
    emotes: Map<String, String>,
    modifier: Modifier = Modifier,
    fontSizeSp: Float = 13f,
    currentUser: TwitchUser? = null,
    onSendMessage: ((String) -> Unit)? = null,
    onOpenLogin: (() -> Unit)? = null,
    showInput: Boolean = true,
    listState: LazyListState = rememberLazyListState()
) {
    val twitchColors = LocalTwitchColors.current
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    var userScrolledUp by remember { mutableStateOf(false) }
    var wasDragged by remember { mutableStateOf(false) }

    // When orientation swaps or on initial composition, automatically stick to bottom
    LaunchedEffect(isLandscape) {
        userScrolledUp = false
        if (messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    // Input & Emote State
    var inputText by remember { mutableStateOf(TextFieldValue("")) }
    var showEmotePicker by remember { mutableStateOf(false) }
    var emoteSearchQuery by remember { mutableStateOf("") }

    val keyboardController = LocalSoftwareKeyboardController.current
    val focusManager = LocalFocusManager.current
    val isImeVisible = WindowInsets.isImeVisible

    // Back gesture handling:
    // 1. If emote picker tray is open, back closes it
    BackHandler(enabled = showEmotePicker) {
        showEmotePicker = false
    }

    // 2. If keyboard is showing, back hides it and clears input focus
    BackHandler(enabled = isImeVisible) {
        keyboardController?.hide()
        focusManager.clearFocus()
    }

    // Detect user scrolling with touch drag and settling after momentum fling
    val isDragged by listState.interactionSource.collectIsDraggedAsState()

    LaunchedEffect(isDragged, listState.isScrollInProgress) {
        if (isDragged) {
            wasDragged = true
        } else if (wasDragged && !listState.isScrollInProgress) {
            wasDragged = false
            val visibleItems = listState.layoutInfo.visibleItemsInfo
            val totalItems = listState.layoutInfo.totalItemsCount
            val atBottom = !listState.canScrollForward ||
                    visibleItems.isEmpty() ||
                    totalItems == 0 ||
                    (visibleItems.last().index >= totalItems - 2)
            userScrolledUp = !atBottom
            if (atBottom && messages.isNotEmpty()) {
                listState.scrollToItem(messages.lastIndex)
            }
        }
    }

    // Auto-scroll to bottom on new messages whenever user hasn't explicitly scrolled up
    LaunchedEffect(messages.lastOrNull()?.id, userScrolledUp) {
        if (!userScrolledUp && !isDragged && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    LaunchedEffect(isImeVisible) {
        if (isImeVisible && !userScrolledUp && messages.isNotEmpty()) {
            listState.scrollToItem(messages.lastIndex)
        }
    }

    val showScrollButton by remember {
        derivedStateOf {
            userScrolledUp && messages.isNotEmpty()
        }
    }

    // Determine current word at cursor to display live emote suggestions
    val currentWord = remember(inputText.text, inputText.selection) {
        val text = inputText.text
        val cursor = inputText.selection.start
        val textBefore = if (cursor in 0..text.length) text.substring(0, cursor) else text
        textBefore.substringAfterLast(" ", textBefore)
    }

    val matchingSuggestions = remember(currentWord, emotes) {
        val query = currentWord.removePrefix(":").lowercase().trim()
        if (query.length >= 2) {
            emotes.entries
                .filter { (name, _) -> name.lowercase().contains(query) }
                .take(12)
                .toList()
        } else {
            emptyList()
        }
    }

    fun insertEmote(emoteName: String) {
        val text = inputText.text
        val cursor = inputText.selection.start.coerceIn(0, text.length)
        val textBefore = text.substring(0, cursor)
        val textAfter = text.substring(cursor)
        val word = textBefore.substringAfterLast(" ", textBefore)

        val before = if (word.isNotEmpty() && textBefore.endsWith(word)) {
            textBefore.dropLast(word.length)
        } else {
            textBefore
        }

        val newText = "$before$emoteName $textAfter"
        val newCursor = before.length + emoteName.length + 1
        inputText = TextFieldValue(newText, TextRange(newCursor))
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(twitchColors.chatBackground)
    ) {
        // Chat Messages Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
            if (showScrollButton) {
                Surface(
                    color = TwitchPurple,
                    shape = CircleShape,
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .clickable {
                            userScrolledUp = false
                            coroutineScope.launch {
                                if (messages.isNotEmpty()) {
                                    listState.scrollToItem(messages.lastIndex)
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

        // Chat Input / Bottom Actions
        if (showInput) {
            if (currentUser != null) {
                // Live Emote Suggestions Bar while typing
                if (matchingSuggestions.isNotEmpty()) {
                    Surface(
                        color = TwitchDarkCard,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(matchingSuggestions, key = { it.key }) { (name, url) ->
                                Surface(
                                    color = TwitchDark,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.clickable {
                                        insertEmote(name)
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(url)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = name,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Text(
                                            text = name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Chat Input Bar
                Surface(
                    color = twitchColors.card,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Emote Tray Button
                        IconButton(
                            onClick = {
                                if (!showEmotePicker) {
                                    keyboardController?.hide()
                                    showEmotePicker = true
                                } else {
                                    showEmotePicker = false
                                }
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Mood,
                                contentDescription = "Emotes",
                                tint = if (showEmotePicker) TwitchPurple else TwitchTextDim,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Message Text Field with perfectly centered text pill
                        BasicTextField(
                            value = inputText,
                            onValueChange = {
                                inputText = it
                                if (showEmotePicker) showEmotePicker = false
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            cursorBrush = SolidColor(TwitchPurple),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                val text = inputText.text.trim()
                                if (text.isNotEmpty()) {
                                    onSendMessage?.invoke(text)
                                    inputText = TextFieldValue("")
                                    showEmotePicker = false
                                }
                            }),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { if (showEmotePicker) showEmotePicker = false },
                            decorationBox = { innerTextField ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(40.dp)
                                        .background(TwitchDark, RoundedCornerShape(20.dp))
                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(20.dp))
                                        .padding(horizontal = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier.weight(1f),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        if (inputText.text.isEmpty()) {
                                            Text(
                                                text = "Send a message...",
                                                fontSize = 13.sp,
                                                color = TwitchTextDim
                                            )
                                        }
                                        innerTextField()
                                    }
                                }
                            }
                        )

                        // Send Button
                        IconButton(
                            onClick = {
                                val text = inputText.text.trim()
                                if (text.isNotEmpty()) {
                                    onSendMessage?.invoke(text)
                                    inputText = TextFieldValue("")
                                    showEmotePicker = false
                                }
                            },
                            enabled = inputText.text.isNotBlank(),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = "Send",
                                tint = if (inputText.text.isNotBlank()) TwitchPurple else TwitchTextDim.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Emote Picker Tray
                if (showEmotePicker) {
                    val filteredEmotes = remember(emoteSearchQuery, emotes) {
                        val q = emoteSearchQuery.trim().lowercase()
                        if (q.isEmpty()) {
                            emotes.entries.toList()
                        } else {
                            emotes.entries.filter { it.key.lowercase().contains(q) }.toList()
                        }
                    }

                    Surface(
                        color = TwitchDark,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(205.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Search box inside Emote Tray
                            BasicTextField(
                                value = emoteSearchQuery,
                                onValueChange = { emoteSearchQuery = it },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = Color.White,
                                    fontSize = 12.sp
                                ),
                                cursorBrush = SolidColor(TwitchPurple),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                decorationBox = { innerTextField ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(36.dp)
                                            .background(TwitchDarkCard, RoundedCornerShape(8.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(8.dp))
                                            .padding(horizontal = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Search,
                                            contentDescription = null,
                                            tint = TwitchTextDim,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Box(
                                            modifier = Modifier.weight(1f),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (emoteSearchQuery.isEmpty()) {
                                                Text(
                                                    "Search 7TV, BTTV, FFZ emotes...",
                                                    fontSize = 12.sp,
                                                    color = TwitchTextDim
                                                )
                                            }
                                            innerTextField()
                                        }
                                        if (emoteSearchQuery.isNotEmpty()) {
                                            IconButton(
                                                onClick = { emoteSearchQuery = "" },
                                                modifier = Modifier.size(20.dp)
                                            ) {
                                                Icon(
                                                    Icons.Filled.Close,
                                                    contentDescription = "Clear",
                                                    tint = TwitchTextDim,
                                                    modifier = Modifier.size(14.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            )

                            // Emotes Grid
                            if (filteredEmotes.isEmpty()) {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No emotes found", color = TwitchTextDim, fontSize = 12.sp)
                                }
                            } else {
                                LazyVerticalGrid(
                                    columns = GridCells.Adaptive(46.dp),
                                    contentPadding = PaddingValues(6.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    items(filteredEmotes, key = { it.key }) { (name, url) ->
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(RoundedCornerShape(6.dp))
                                                .background(TwitchDarkCard)
                                                .clickable {
                                                    insertEmote(name)
                                                },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            AsyncImage(
                                                model = ImageRequest.Builder(LocalContext.current)
                                                    .data(url)
                                                    .crossfade(true)
                                                    .build(),
                                                contentDescription = name,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Logged-out Banner prompting user to log in
                Surface(
                    color = twitchColors.card,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Log in to join the chat",
                            color = TwitchTextDim,
                            fontSize = 13.sp
                        )
                        Button(
                            onClick = { onOpenLogin?.invoke() },
                            colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
                        ) {
                            Text("Log In", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
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
