@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.eetu.twitchapp.ui.multistream

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.zIndex
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.data.model.LiveStreamItem
import kotlinx.coroutines.delay
import com.eetu.twitchapp.data.auth.TwitchAuthManager
import com.eetu.twitchapp.data.network.TwitchGqlClient
import com.eetu.twitchapp.ui.components.FloatingResizableChat
import androidx.activity.compose.BackHandler
import androidx.compose.runtime.saveable.rememberSaveable
import com.eetu.twitchapp.ui.theme.*

@Composable
fun MultiStreamScreen(
    initialChannels: List<String> = emptyList(),
    existingPlayer: ExoPlayer? = null,
    existingChannel: String? = null,
    onReturnToSingleStream: (String) -> Unit = {},
    onCloseAll: () -> Unit = {},
    onNavigateBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val streams = remember { mutableStateListOf<String>().apply { addAll(initialChannels) } }
    var hasEverHadMultipleStreams by rememberSaveable { mutableStateOf(streams.size >= 2) }

    LaunchedEffect(streams.size) {
        if (streams.size >= 2) {
            hasEverHadMultipleStreams = true
        }
    }

    // Per-stream independent audio volume and mute tracking
    val streamVolumes = remember { mutableStateMapOf<String, Float>() }
    val streamMuted = remember { mutableStateMapOf<String, Boolean>() }
    var focusedChannel by remember { mutableStateOf(streams.firstOrNull() ?: "") }

    // Top-level disposal: ensure existingPlayer volume is always restored to 1.0f on exit
    DisposableEffect(Unit) {
        onDispose {
            existingPlayer?.volume = 1f
        }
    }

    var showAddDialog by remember { mutableStateOf(false) }
    var showFloatingChat by remember { mutableStateOf(false) }
    var newChannelInput by remember { mutableStateOf("") }

    // Multi-stream tile dragging state
    var draggedChannel by remember { mutableStateOf<String?>(null) }
    var hoveredTargetChannel by remember { mutableStateOf<String?>(null) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var touchInRoot by remember { mutableStateOf(Offset.Zero) }

    val tileBounds = remember { mutableStateMapOf<String, Rect>() }
    val tileHeaderRoots = remember { mutableStateMapOf<String, Offset>() }
    val playlistTokenCache = remember { mutableMapOf<String, String>() }

    fun handleDragStart(channel: String, localDown: Offset) {
        draggedChannel = channel
        dragOffset = Offset.Zero
        val headerRoot = tileHeaderRoots[channel] ?: Offset.Zero
        touchInRoot = headerRoot + localDown
        hoveredTargetChannel = null
    }

    fun handleDrag(delta: Offset) {
        dragOffset += delta
        touchInRoot += delta
        val dragged = draggedChannel ?: return
        hoveredTargetChannel = tileBounds.entries.firstOrNull { (ch, rect) ->
            ch != dragged && rect.contains(touchInRoot)
        }?.key
    }

    fun handleDragEnd() {
        val dragged = draggedChannel
        val target = hoveredTargetChannel
        if (dragged != null && target != null && dragged != target) {
            val idx1 = streams.indexOf(dragged)
            val idx2 = streams.indexOf(target)
            if (idx1 != -1 && idx2 != -1) {
                val temp = streams[idx1]
                streams[idx1] = streams[idx2]
                streams[idx2] = temp
            }
        }
        draggedChannel = null
        hoveredTargetChannel = null
        dragOffset = Offset.Zero
    }

    fun handleDragCancel() {
        draggedChannel = null
        hoveredTargetChannel = null
        dragOffset = Offset.Zero
    }

    fun removeStream(channel: String) {
        val idx = streams.indexOf(channel)
        if (idx != -1) {
            streams.removeAt(idx)
            tileBounds.remove(channel)
            tileHeaderRoots.remove(channel)
            streamVolumes.remove(channel)
            streamMuted.remove(channel)
            if (focusedChannel == channel) {
                focusedChannel = streams.firstOrNull() ?: ""
            }
            if (hasEverHadMultipleStreams && streams.size == 1) {
                val remainingChannel = streams[0]
                streamMuted[remainingChannel] = false
                streamVolumes[remainingChannel] = 1.0f
                existingPlayer?.volume = 1f
                onReturnToSingleStream(remainingChannel)
            } else if (streams.isEmpty()) {
                onCloseAll()
            }
        }
    }

    fun handleBackNavigation() {
        existingPlayer?.volume = 1f
        if (streams.isNotEmpty() && hasEverHadMultipleStreams) {
            onReturnToSingleStream(focusedChannel.ifEmpty { streams.first() })
        } else if (streams.isEmpty()) {
            onCloseAll()
        } else {
            onNavigateBack()
        }
    }

    BackHandler(enabled = true) {
        handleBackNavigation()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Multistream", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                        Badge(containerColor = TwitchPurple) {
                            Text("${streams.size}/4", color = Color.White)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { handleBackNavigation() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Chat toggle
                    IconButton(onClick = { showFloatingChat = !showFloatingChat }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = "Toggle Chat",
                            tint = if (showFloatingChat) TwitchPurple else Color.White
                        )
                    }
                    // Add stream button
                    if (streams.size < 4) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Filled.Add, contentDescription = "Add Stream", tint = TwitchTeal)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TwitchDark)
            )
        },
        containerColor = TwitchDark
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (streams.isEmpty()) {
                // Empty state
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Tv,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = TwitchTextDim
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No active streams", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showAddDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add a Streamer")
                    }
                }
            } else {
                @Composable
                fun RenderStreamTile(
                    channel: String,
                    canDrag: Boolean,
                    modifier: Modifier
                ) {
                    key(channel) {
                        StreamTile(
                            channel = channel,
                            existingPlayer = existingPlayer,
                            existingChannel = existingChannel,
                            volume = streamVolumes[channel] ?: 1.0f,
                            onVolumeChange = { streamVolumes[channel] = it },
                            isMuted = streamMuted[channel] ?: false,
                            onToggleMute = {
                                val currentlyMuted = streamMuted[channel] ?: false
                                streamMuted[channel] = !currentlyMuted
                            },
                            isFocused = focusedChannel == channel,
                            onFocus = { focusedChannel = channel },
                            onClose = { removeStream(channel) },
                            isDragging = draggedChannel == channel,
                            isHoveredTarget = hoveredTargetChannel == channel,
                            hoveredSwapWith = draggedChannel,
                            dragOffset = if (draggedChannel == channel) dragOffset else Offset.Zero,
                            onDragStart = { localDown -> handleDragStart(channel, localDown) },
                            onDrag = { delta -> handleDrag(delta) },
                            onDragEnd = { handleDragEnd() },
                            onDragCancel = { handleDragCancel() },
                            onTilePositioned = { rect -> tileBounds[channel] = rect },
                            onHeaderPositioned = { offset -> tileHeaderRoots[channel] = offset },
                            canDrag = canDrag,
                            tokenCache = playlistTokenCache,
                            modifier = modifier
                        )
                    }
                }

                // Dynamic Multi-view layout
                when (streams.size) {
                    1 -> {
                        RenderStreamTile(
                            channel = streams[0],
                            canDrag = false,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    2 -> {
                        if (isLandscape) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                streams.forEach { ch ->
                                    RenderStreamTile(
                                        channel = ch,
                                        canDrag = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                streams.forEach { ch ->
                                    RenderStreamTile(
                                        channel = ch,
                                        canDrag = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    3 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top 1 stream
                            RenderStreamTile(
                                channel = streams[0],
                                canDrag = true,
                                modifier = Modifier
                                    .weight(1.1f)
                                    .fillMaxWidth()
                            )
                            // Bottom 2 streams
                            Row(modifier = Modifier.weight(0.9f).fillMaxWidth()) {
                                for (i in 1..2) {
                                    RenderStreamTile(
                                        channel = streams[i],
                                        canDrag = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                    4 -> {
                        // 2x2 Grid
                        Column(modifier = Modifier.fillMaxSize()) {
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                for (i in 0..1) {
                                    RenderStreamTile(
                                        channel = streams[i],
                                        canDrag = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                for (i in 2..3) {
                                    RenderStreamTile(
                                        channel = streams[i],
                                        canDrag = true,
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Floating Resizable Chat overlay with multi-stream channel switcher
            if (showFloatingChat && streams.isNotEmpty()) {
                FloatingResizableChat(
                    channelName = focusedChannel.ifEmpty { streams.first() },
                    availableChannels = streams.toList(),
                    onChannelSelected = { focusedChannel = it },
                    onClose = { showFloatingChat = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Add Stream Dialog with Autocomplete (Followed live channels first, then search)
    if (showAddDialog) {
        AddStreamerDialog(
            currentStreams = streams.toList(),
            onAddChannel = { channel ->
                val clean = channel.trim().lowercase()
                if (clean.isNotEmpty() && !streams.contains(clean) && streams.size < 4) {
                    streams.add(clean)
                    streamVolumes[clean] = 1.0f
                    streamMuted[clean] = false
                    if (focusedChannel.isEmpty()) focusedChannel = clean
                }
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false }
        )
    }
}

@Composable
fun AddStreamerDialog(
    currentStreams: List<String> = emptyList(),
    title: String? = null,
    onAddChannel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { TwitchAuthManager.getInstance(context) }
    val gqlClient = remember { TwitchGqlClient() }

    var searchQuery by remember { mutableStateOf("") }
    var followedStreams by remember { mutableStateOf<List<LiveStreamItem>>(emptyList()) }
    var topStreams by remember { mutableStateOf<List<LiveStreamItem>>(emptyList()) }
    var searchResults by remember { mutableStateOf<List<LiveStreamItem>>(emptyList()) }
    var isSearching by remember { mutableStateOf(false) }
    var isLoadingInitial by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        isLoadingInitial = true
        try {
            val token = authManager.getAuthToken()
            if (!token.isNullOrEmpty()) {
                followedStreams = gqlClient.getFollowedLiveStreams(token)
            }
            topStreams = gqlClient.getTopStreams(12)
        } catch (_: Exception) {
        } finally {
            isLoadingInitial = false
        }
    }

    LaunchedEffect(searchQuery) {
        val query = searchQuery.trim()
        if (query.isNotEmpty()) {
            delay(250)
            isSearching = true
            try {
                searchResults = gqlClient.searchChannels(query, 16)
            } catch (_: Exception) {
                searchResults = emptyList()
            } finally {
                isSearching = false
            }
        } else {
            searchResults = emptyList()
            isSearching = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TwitchDarkCard,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.85f)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title ?: "Add Streamer (${currentStreams.size}/4)",
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = Color.White
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TwitchTextDim, modifier = Modifier.size(18.dp))
                    }
                }

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search streamer or game...", fontSize = 13.sp, color = TwitchTextDim) },
                    singleLine = true,
                    leadingIcon = {
                        Icon(Icons.Filled.Search, contentDescription = null, tint = TwitchTextDim, modifier = Modifier.size(18.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TwitchTextDim, modifier = Modifier.size(16.dp))
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = TwitchPurple,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (isSearching || (isLoadingInitial && searchQuery.isEmpty())) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = TwitchPurple, modifier = Modifier.size(32.dp))
                        }
                    } else if (searchQuery.isNotEmpty()) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            val cleanQuery = searchQuery.trim().lowercase()
                            val isAlreadyAdded = currentStreams.any { it.equals(cleanQuery, ignoreCase = true) }
                            item {
                                Surface(
                                    color = TwitchDark,
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAlreadyAdded) {
                                            onAddChannel(cleanQuery)
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Icon(Icons.Filled.AddCircleOutline, contentDescription = null, tint = TwitchPurple)
                                        Text(
                                            text = if (isAlreadyAdded) "Channel \"$cleanQuery\" already added" else "Add \"$cleanQuery\" directly",
                                            color = if (isAlreadyAdded) TwitchTextDim else Color.White,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                    }
                                }
                            }

                            if (searchResults.isEmpty()) {
                                item {
                                    Text("No matching streamers found", color = TwitchTextDim, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp))
                                }
                            } else {
                                items(searchResults, key = { it.id.ifEmpty { it.login } }) { item ->
                                    val added = currentStreams.any { it.equals(item.login, ignoreCase = true) }
                                    StreamerSearchRow(
                                        item = item,
                                        isAdded = added,
                                        onSelect = { onAddChannel(item.login) }
                                    )
                                }
                            }
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            if (followedStreams.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "FOLLOWED LIVE CHANNELS",
                                        color = TwitchPurple,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                    )
                                }
                                items(followedStreams, key = { "f_${it.id.ifEmpty { it.login }}" }) { item ->
                                    val added = currentStreams.any { it.equals(item.login, ignoreCase = true) }
                                    StreamerSearchRow(
                                        item = item,
                                        isAdded = added,
                                        onSelect = { onAddChannel(item.login) }
                                    )
                                }
                            }

                            if (topStreams.isNotEmpty()) {
                                item {
                                    Text(
                                        text = "TOP LIVE CHANNELS",
                                        color = TwitchTextDim,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
                                    )
                                }
                                items(topStreams, key = { "t_${it.id.ifEmpty { it.login }}" }) { item ->
                                    val added = currentStreams.any { it.equals(item.login, ignoreCase = true) }
                                    StreamerSearchRow(
                                        item = item,
                                        isAdded = added,
                                        onSelect = { onAddChannel(item.login) }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamerSearchRow(
    item: LiveStreamItem,
    isAdded: Boolean,
    onSelect: () -> Unit
) {
    Surface(
        color = TwitchDark,
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAdded, onClick = onSelect)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                if (item.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(item.profileImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = item.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(TwitchPurple),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = item.displayName.take(1).uppercase(),
                            color = Color.White,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.displayName,
                        color = if (isAdded) TwitchTextDim else Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (item.gameName.isNotEmpty()) {
                        Text(
                            text = item.gameName,
                            color = TwitchTextDim,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (isAdded) {
                Surface(
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "Added",
                        color = TwitchTextDim,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            } else if (item.viewersCount > 0) {
                Surface(
                    color = TwitchRed.copy(alpha = 0.2f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = "${item.viewersCount} viewers",
                        color = TwitchRed,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StreamTile(
    channel: String,
    volume: Float = 1.0f,
    onVolumeChange: (Float) -> Unit = {},
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    isFocused: Boolean = false,
    onFocus: () -> Unit = {},
    onClose: () -> Unit,
    existingPlayer: ExoPlayer? = null,
    existingChannel: String? = null,
    isDragging: Boolean = false,
    isHoveredTarget: Boolean = false,
    hoveredSwapWith: String? = null,
    dragOffset: Offset = Offset.Zero,
    onDragStart: (Offset) -> Unit = {},
    onDrag: (Offset) -> Unit = {},
    onDragEnd: () -> Unit = {},
    onDragCancel: () -> Unit = {},
    onTilePositioned: (Rect) -> Unit = {},
    onHeaderPositioned: (Offset) -> Unit = {},
    canDrag: Boolean = true,
    tokenCache: MutableMap<String, String>? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gqlClient = remember { TwitchGqlClient() }
    val authManager = remember { TwitchAuthManager.getInstance(context) }
    val isExistingStream = existingPlayer != null && channel.equals(existingChannel, ignoreCase = true)
    var isLoading by remember { mutableStateOf(!isExistingStream) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var playerViewRef by remember { mutableStateOf<PlayerView?>(null) }
    var showVolumeSlider by remember { mutableStateOf(false) }

    val exoPlayer = if (isExistingStream) {
        checkNotNull(existingPlayer)
    } else {
        remember(channel) {
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build()
            ExoPlayer.Builder(context)
                .setAudioAttributes(audioAttrs, false)
                .build().apply {
                    repeatMode = Player.REPEAT_MODE_OFF
                    playWhenReady = true
                }
        }
    }

    val effectiveVolume = if (isMuted) 0f else volume.coerceIn(0f, 1f)

    DisposableEffect(channel) {
        onDispose {
            playerViewRef?.player = null
            if (isExistingStream) {
                existingPlayer.volume = 1f
            } else {
                exoPlayer.stop()
                exoPlayer.release()
            }
        }
    }

    LaunchedEffect(effectiveVolume) {
        exoPlayer.volume = effectiveVolume
    }

    LaunchedEffect(channel) {
        if (isExistingStream) {
            isLoading = false
            errorMessage = null
            if (!exoPlayer.isPlaying) {
                exoPlayer.play()
            }
            return@LaunchedEffect
        }

        isLoading = true
        errorMessage = null
        val cachedUrl = tokenCache?.get(channel)
        val playlistUrl = if (!cachedUrl.isNullOrEmpty()) {
            cachedUrl
        } else {
            val tokenResult = gqlClient.getStreamPlaybackAccessToken(channel, authManager.getAuthToken())
            if (tokenResult != null) {
                tokenCache?.put(channel, tokenResult.masterPlaylistUrl)
                tokenResult.masterPlaylistUrl
            } else null
        }

        if (playlistUrl != null) {
            val mediaItem = MediaItem.fromUri(Uri.parse(playlistUrl))
            exoPlayer.setMediaItem(mediaItem)
            exoPlayer.prepare()
            exoPlayer.play()
            isLoading = false
        } else {
            errorMessage = "Channel offline"
            isLoading = false
        }
    }

    Card(
        modifier = modifier
            .padding(2.dp)
            .zIndex(if (isDragging) 100f else 1f)
            .onGloballyPositioned { coords ->
                onTilePositioned(coords.boundsInRoot())
            }
            .graphicsLayer {
                if (isDragging) {
                    translationX = dragOffset.x
                    translationY = dragOffset.y
                    scaleX = 1.04f
                    scaleY = 1.04f
                    shadowElevation = 24f
                }
            }
            .border(
                width = if (isHoveredTarget) 2.5.dp else if (isDragging) 2.dp else if (isFocused) 2.dp else 0.5.dp,
                color = if (isHoveredTarget) TwitchTeal else if (isDragging) TwitchPurple else if (isFocused) TwitchPurple else TwitchDarkCard,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar (draggable from anywhere except the controls)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(if (isDragging) TwitchPurple.copy(alpha = 0.35f) else TwitchDarkCard)
                    .onGloballyPositioned { coords ->
                        onHeaderPositioned(coords.boundsInRoot().topLeft)
                    }
                    .then(
                        if (canDrag) {
                            Modifier.pointerInput(channel) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var isDraggingStarted = false
                                    var totalDrag = Offset.Zero
                                    val touchSlop = viewConfiguration.touchSlop

                                    try {
                                        while (true) {
                                            val event = awaitPointerEvent()
                                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                            if (!change.pressed) {
                                                if (isDraggingStarted) {
                                                    isDraggingStarted = false
                                                    onDragEnd()
                                                } else {
                                                    onFocus()
                                                }
                                                break
                                            }
                                            val dragAmount = change.position - change.previousPosition
                                            totalDrag += dragAmount
                                            if (!isDraggingStarted) {
                                                if (totalDrag.getDistance() > touchSlop) {
                                                    isDraggingStarted = true
                                                    change.consume()
                                                    onDragStart(down.position)
                                                    onDrag(totalDrag)
                                                }
                                            } else {
                                                change.consume()
                                                onDrag(dragAmount)
                                            }
                                        }
                                    } finally {
                                        if (isDraggingStarted) {
                                            onDragCancel()
                                        }
                                    }
                                }
                            }
                        } else {
                            Modifier.clickable { onFocus() }
                        }
                    )
                    .padding(horizontal = 8.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (canDrag) {
                        Icon(
                            imageVector = Icons.Filled.DragIndicator,
                            contentDescription = "Drag from title bar to reorder",
                            tint = if (isDragging) TwitchPurple else Color.White.copy(alpha = 0.65f),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(TwitchRed)
                    )
                    Text(
                        text = channel,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Volume percentage pill badge (tap to toggle volume slider)
                    Surface(
                        color = if (isMuted || effectiveVolume == 0f) TwitchRed.copy(alpha = 0.22f) else TwitchPurple.copy(alpha = 0.35f),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { showVolumeSlider = !showVolumeSlider }
                    ) {
                        Text(
                            text = if (isMuted || effectiveVolume == 0f) "Muted" else "${(effectiveVolume * 100).roundToInt()}%",
                            color = if (isMuted || effectiveVolume == 0f) TwitchRed else Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                        )
                    }

                    // Quick mute/unmute audio button
                    IconButton(
                        onClick = onToggleMute,
                        modifier = Modifier.size(26.dp)
                    ) {
                        val icon = when {
                            isMuted || effectiveVolume == 0f -> Icons.AutoMirrored.Filled.VolumeOff
                            effectiveVolume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                            else -> Icons.AutoMirrored.Filled.VolumeUp
                        }
                        val tint = if (isMuted || effectiveVolume == 0f) TwitchRed else TwitchGreen
                        Icon(
                            imageVector = icon,
                            contentDescription = if (isMuted || effectiveVolume == 0f) "Unmute" else "Mute",
                            tint = tint,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Close stream tile
                    IconButton(
                        onClick = onClose,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close Stream",
                            tint = TwitchTextDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Expandable Per-Stream Volume Slider
            AnimatedVisibility(
                visible = showVolumeSlider,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Surface(
                    color = TwitchDarkCard.copy(alpha = 0.95f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 2.dp)
                    ) {
                        IconButton(
                            onClick = onToggleMute,
                            modifier = Modifier.size(22.dp)
                        ) {
                            Icon(
                                imageVector = if (isMuted || effectiveVolume == 0f) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeDown,
                                contentDescription = if (isMuted || effectiveVolume == 0f) "Unmute" else "Mute",
                                tint = if (isMuted || effectiveVolume == 0f) TwitchRed else TwitchGreen,
                                modifier = Modifier.size(15.dp)
                            )
                        }
                        Slider(
                            value = effectiveVolume,
                            onValueChange = { newVol ->
                                onVolumeChange(newVol)
                                if (isMuted && newVol > 0f) {
                                    onToggleMute()
                                }
                            },
                            valueRange = 0f..1f,
                            modifier = Modifier
                                .weight(1f)
                                .height(28.dp),
                            colors = SliderDefaults.colors(
                                thumbColor = TwitchPurple,
                                activeTrackColor = TwitchPurple,
                                inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                            )
                        )
                        Text(
                            text = "${(effectiveVolume * 100).roundToInt()}%",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.width(36.dp),
                            textAlign = TextAlign.End
                        )
                    }
                }
            }

            // Video Player
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onFocus() },
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setBackgroundColor(android.graphics.Color.BLACK)
                            playerViewRef = this
                        }
                    },
                    update = { pv ->
                        pv.player = exoPlayer
                        playerViewRef = pv
                    },
                    modifier = Modifier.fillMaxSize()
                )

                if (isLoading) {
                    CircularProgressIndicator(
                        color = TwitchPurple,
                        modifier = Modifier.size(32.dp),
                        strokeWidth = 2.5.dp
                    )
                } else if (errorMessage != null) {
                    Text(errorMessage ?: "", color = TwitchTextDim, fontSize = 12.sp)
                }

                // Visual target feedback during drag & drop
                if (isHoveredTarget && hoveredSwapWith != null) {
                    Surface(
                        color = TwitchTeal.copy(alpha = 0.92f),
                        shape = RoundedCornerShape(8.dp),
                        shadowElevation = 6.dp,
                        modifier = Modifier.align(Alignment.Center)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.SwapHoriz,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                            Text(
                                text = "Swap with $hoveredSwapWith",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
