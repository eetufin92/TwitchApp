package com.eetu.twitchapp.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.data.network.TwitchGqlClient
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onChannelSelected: (String) -> Unit,
    onOpenMultiStream: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenEmoteSettings: () -> Unit,
    onOpenAdSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gqlClient = remember { TwitchGqlClient() }
    val coroutineScope = rememberCoroutineScope()

    var streams by remember { mutableStateOf<List<LiveStreamItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var showMenu by remember { mutableStateOf(false) }

    fun loadStreams(query: String = "") {
        isLoading = true
        coroutineScope.launch {
            try {
                val results = if (query.isEmpty()) {
                    gqlClient.getTopStreams(24)
                } else {
                    gqlClient.searchChannels(query, 20)
                }
                streams = results
            } catch (e: Exception) {
                // Keep existing
            } finally {
                isLoading = false
            }
        }
    }

    LaunchedEffect(Unit) {
        loadStreams()
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(TwitchDark)
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Search Bar
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { newQuery ->
                            searchQuery = newQuery
                            searchJob?.cancel()
                            searchJob = coroutineScope.launch {
                                delay(350)
                                loadStreams(newQuery)
                            }
                        },
                        placeholder = { Text("Search streamers or games...", fontSize = 13.sp, color = TwitchTextDim) },
                        leadingIcon = {
                            Icon(Icons.Filled.Search, contentDescription = "Search", tint = TwitchPurple, modifier = Modifier.size(20.dp))
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = {
                                    searchQuery = ""
                                    loadStreams()
                                }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Clear", tint = TwitchTextDim, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TwitchPurple,
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = TwitchDarkCard,
                            unfocusedContainerColor = TwitchDarkCard
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp)
                    )

                    // Multistream Quick Icon
                    IconButton(
                        onClick = onOpenMultiStream,
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(Icons.Filled.GridView, contentDescription = "Multistream", tint = TwitchTeal, modifier = Modifier.size(24.dp))
                    }

                    // Settings Menu
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(Icons.Filled.Tune, contentDescription = "Menu", tint = Color.White, modifier = Modifier.size(24.dp))
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Multistream") },
                                leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, tint = TwitchTeal) },
                                onClick = {
                                    showMenu = false
                                    onOpenMultiStream()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Emote Settings (7TV, BTTV, FFZ)") },
                                leadingIcon = { Icon(Icons.Filled.Mood, contentDescription = null, tint = TwitchPurple) },
                                onClick = {
                                    showMenu = false
                                    onOpenEmoteSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Ad Muter Settings") },
                                leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null, tint = TwitchTeal) },
                                onClick = {
                                    showMenu = false
                                    onOpenAdSettings()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Settings") },
                                leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onOpenSettings()
                                }
                            )
                        }
                    }
                }
            }
        },
        containerColor = TwitchDark,
        modifier = modifier
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            if (isLoading && streams.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = TwitchPurple)
                }
            } else if (streams.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotEmpty()) "No channels found for \"$searchQuery\"" else "No active streams found",
                        color = TwitchTextDim,
                        fontSize = 14.sp
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 300.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(streams, key = { it.id + it.login }) { item ->
                        LiveStreamCard(
                            stream = item,
                            onClick = { onChannelSelected(item.login) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LiveStreamCard(
    stream: LiveStreamItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = TwitchDarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // Stream Preview Image with Live Badge & Viewers
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .background(Color.Black)
            ) {
                if (stream.previewImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(stream.previewImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = "Stream preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Live Tag (Top Left)
                Surface(
                    color = TwitchRed,
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                ) {
                    Text(
                        text = "LIVE",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                // Viewers Tag (Bottom Left)
                if (stream.viewersCount > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.75f),
                        shape = RoundedCornerShape(4.dp),
                        modifier = Modifier
                            .padding(8.dp)
                            .align(Alignment.BottomStart)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(TwitchRed)
                            )
                            Text(
                                text = "${formatViewersCount(stream.viewersCount)} viewers",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Streamer Info Bar below preview
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(10.dp),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Avatar
                if (stream.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(stream.profileImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = stream.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.title.ifEmpty { stream.displayName },
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = stream.displayName,
                        color = TwitchTextDim,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (stream.gameName.isNotEmpty()) {
                        Text(
                            text = stream.gameName,
                            color = TwitchTeal,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

private fun formatViewersCount(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}
