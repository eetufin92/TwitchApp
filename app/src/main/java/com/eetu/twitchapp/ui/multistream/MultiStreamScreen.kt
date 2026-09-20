@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.eetu.twitchapp.ui.multistream

import android.net.Uri
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.eetu.twitchapp.data.network.TwitchGqlClient
import com.eetu.twitchapp.ui.components.FloatingResizableChat
import com.eetu.twitchapp.ui.theme.*

@Composable
fun MultiStreamScreen(
    initialChannels: List<String> = emptyList(),
    onNavigateBack: () -> Unit
) {
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp

    val streams = remember { mutableStateListOf<String>().apply { addAll(initialChannels) } }
    var activeAudioChannel by remember { mutableStateOf(streams.firstOrNull() ?: "") }
    var showAddDialog by remember { mutableStateOf(false) }
    var showFloatingChat by remember { mutableStateOf(false) }
    var newChannelInput by remember { mutableStateOf("") }

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
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    // Chat toggle
                    IconButton(onClick = { showFloatingChat = !showFloatingChat }) {
                        Icon(
                            Icons.Filled.Chat,
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
                // Dynamic Multi-view layout
                when (streams.size) {
                    1 -> {
                        StreamTile(
                            channel = streams[0],
                            isActiveAudio = activeAudioChannel == streams[0],
                            onSelectAudio = { activeAudioChannel = streams[0] },
                            onClose = {
                                val removed = streams.removeAt(0)
                                if (activeAudioChannel == removed) activeAudioChannel = streams.firstOrNull() ?: ""
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    2 -> {
                        if (isLandscape) {
                            Row(modifier = Modifier.fillMaxSize()) {
                                streams.forEachIndexed { index, ch ->
                                    StreamTile(
                                        channel = ch,
                                        isActiveAudio = activeAudioChannel == ch,
                                        onSelectAudio = { activeAudioChannel = ch },
                                        onClose = {
                                            streams.removeAt(index)
                                            if (activeAudioChannel == ch) activeAudioChannel = streams.firstOrNull() ?: ""
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                        } else {
                            Column(modifier = Modifier.fillMaxSize()) {
                                streams.forEachIndexed { index, ch ->
                                    StreamTile(
                                        channel = ch,
                                        isActiveAudio = activeAudioChannel == ch,
                                        onSelectAudio = { activeAudioChannel = ch },
                                        onClose = {
                                            streams.removeAt(index)
                                            if (activeAudioChannel == ch) activeAudioChannel = streams.firstOrNull() ?: ""
                                        },
                                        modifier = Modifier.weight(1f).fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    3 -> {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Top 1 stream
                            StreamTile(
                                channel = streams[0],
                                isActiveAudio = activeAudioChannel == streams[0],
                                onSelectAudio = { activeAudioChannel = streams[0] },
                                onClose = {
                                    val removed = streams.removeAt(0)
                                    if (activeAudioChannel == removed) activeAudioChannel = streams.firstOrNull() ?: ""
                                },
                                modifier = Modifier.weight(1.1f).fillMaxWidth()
                            )
                            // Bottom 2 streams
                            Row(modifier = Modifier.weight(0.9f).fillMaxWidth()) {
                                for (i in 1..2) {
                                    val ch = streams[i]
                                    StreamTile(
                                        channel = ch,
                                        isActiveAudio = activeAudioChannel == ch,
                                        onSelectAudio = { activeAudioChannel = ch },
                                        onClose = {
                                            streams.removeAt(i)
                                            if (activeAudioChannel == ch) activeAudioChannel = streams.firstOrNull() ?: ""
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
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
                                    val ch = streams[i]
                                    StreamTile(
                                        channel = ch,
                                        isActiveAudio = activeAudioChannel == ch,
                                        onSelectAudio = { activeAudioChannel = ch },
                                        onClose = {
                                            streams.removeAt(i)
                                            if (activeAudioChannel == ch) activeAudioChannel = streams.firstOrNull() ?: ""
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
                                    )
                                }
                            }
                            Row(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                for (i in 2..3) {
                                    val ch = streams[i]
                                    StreamTile(
                                        channel = ch,
                                        isActiveAudio = activeAudioChannel == ch,
                                        onSelectAudio = { activeAudioChannel = ch },
                                        onClose = {
                                            streams.removeAt(i)
                                            if (activeAudioChannel == ch) activeAudioChannel = streams.firstOrNull() ?: ""
                                        },
                                        modifier = Modifier.weight(1f).fillMaxHeight()
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
                    channelName = activeAudioChannel.ifEmpty { streams.first() },
                    availableChannels = streams.toList(),
                    onChannelSelected = { activeAudioChannel = it },
                    onClose = { showFloatingChat = false },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    // Add Stream Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add Streamer", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = newChannelInput,
                        onValueChange = { newChannelInput = it },
                        label = { Text("Channel / Username") },
                        placeholder = { Text("e.g. tarik, shroud") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TwitchPurple,
                            focusedLabelColor = TwitchPurple
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Popular Suggestions:", fontSize = 12.sp, color = TwitchTextDim)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("tarik", "shroud", "xqc", "eslcs").forEach { suggestion ->
                            FilterChip(
                                selected = newChannelInput == suggestion,
                                onClick = { newChannelInput = suggestion },
                                label = { Text(suggestion, fontSize = 11.sp) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val clean = newChannelInput.trim().lowercase()
                        if (clean.isNotEmpty() && !streams.contains(clean) && streams.size < 4) {
                            streams.add(clean)
                            if (activeAudioChannel.isEmpty()) activeAudioChannel = clean
                        }
                        newChannelInput = ""
                        showAddDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple)
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = TwitchTextDim)
                }
            },
            containerColor = TwitchDarkCard
        )
    }
}

@Composable
fun StreamTile(
    channel: String,
    isActiveAudio: Boolean,
    onSelectAudio: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val gqlClient = remember { TwitchGqlClient() }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val exoPlayer = remember(channel) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
        }
    }

    DisposableEffect(channel) {
        onDispose {
            exoPlayer.release()
        }
    }

    LaunchedEffect(isActiveAudio) {
        exoPlayer.volume = if (isActiveAudio) 1f else 0f
    }

    LaunchedEffect(channel) {
        isLoading = true
        errorMessage = null
        val tokenResult = gqlClient.getStreamPlaybackAccessToken(channel)
        if (tokenResult != null) {
            val mediaItem = MediaItem.fromUri(Uri.parse(tokenResult.masterPlaylistUrl))
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
            .border(
                width = if (isActiveAudio) 2.dp else 0.5.dp,
                color = if (isActiveAudio) TwitchPurple else TwitchDarkCard,
                shape = RoundedCornerShape(8.dp)
            ),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Black)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(TwitchDarkCard)
                    .clickable { onSelectAudio() }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
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
                    // Audio toggle button
                    IconButton(
                        onClick = onSelectAudio,
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(
                            if (isActiveAudio) Icons.Filled.VolumeUp else Icons.Filled.VolumeOff,
                            contentDescription = if (isActiveAudio) "Audio Active" else "Audio Muted",
                            tint = if (isActiveAudio) TwitchGreen else TwitchTextDim,
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

            // Video Player
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .clickable { onSelectAudio() },
                contentAlignment = Alignment.Center
            ) {
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = false
                            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                            setBackgroundColor(android.graphics.Color.BLACK)
                        }
                    },
                    update = { pv ->
                        pv.player = exoPlayer
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
            }
        }
    }
}
