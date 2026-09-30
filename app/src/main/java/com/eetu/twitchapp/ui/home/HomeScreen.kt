package com.eetu.twitchapp.ui.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.data.auth.TwitchAuthManager
import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.data.network.TwitchGqlClient
import com.eetu.twitchapp.ui.auth.TwitchLoginDialog
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
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
    val context = LocalContext.current
    val authManager = remember { TwitchAuthManager.getInstance(context) }
    val currentUser by authManager.currentUser.collectAsState()

    val gqlClient = remember { TwitchGqlClient() }
    val coroutineScope = rememberCoroutineScope()

    var streams by remember { mutableStateOf<List<LiveStreamItem>>(emptyList()) }
    var followedStreams by remember { mutableStateOf<List<LiveStreamItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var isSearchActive by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var showMenu by remember { mutableStateOf(false) }
    var showUserMenu by remember { mutableStateOf(false) }
    var showLoginDialog by remember { mutableStateOf(false) }
    val settingsManager = remember { com.eetu.twitchapp.data.TwitchSettingsManager(context) }
    var isCompactFeed by remember { mutableStateOf(settingsManager.isCompactFeed()) }
    var thumbnailSizeDp by remember { mutableIntStateOf(settingsManager.getThumbnailSizeDp()) }
    var selectedTab by remember { mutableIntStateOf(if (currentUser != null) 0 else 1) }

    DisposableEffect(Unit) {
        isCompactFeed = settingsManager.isCompactFeed()
        thumbnailSizeDp = settingsManager.getThumbnailSizeDp()
        onDispose { }
    }

    fun loadStreams(query: String = "") {
        isLoading = true
        coroutineScope.launch {
            try {
                if (query.isEmpty()) {
                    val topDeferred = async { gqlClient.getTopStreams(24) }
                    val token = authManager.getAuthToken()
                    val followedDeferred = if (!token.isNullOrEmpty()) {
                        async { gqlClient.getFollowedLiveStreams(token) }
                    } else null

                    streams = topDeferred.await()
                    if (followedDeferred != null) {
                        followedStreams = followedDeferred.await()
                    } else {
                        followedStreams = emptyList()
                    }
                } else {
                    streams = gqlClient.searchChannels(query, 20)
                }
            } catch (e: Exception) {
                // Keep existing
            } finally {
                isLoading = false
            }
        }
    }

    // Intercept back gesture when search is active to clear search rather than exiting
    BackHandler(enabled = isSearchActive || searchQuery.isNotEmpty()) {
        isSearchActive = false
        val hadQuery = searchQuery.isNotEmpty()
        searchQuery = ""
        keyboardController?.hide()
        focusManager.clearFocus()
        if (hadQuery) {
            loadStreams("")
        }
    }

    LaunchedEffect(currentUser) {
        if (currentUser != null) {
            selectedTab = 0
        }
        loadStreams(searchQuery)
    }

    if (showLoginDialog) {
        TwitchLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = {
                showLoginDialog = false
                loadStreams(searchQuery)
            }
        )
    }

    Scaffold(
        topBar = {
            Column(
                modifier = Modifier
                    .background(TwitchDark)
                    .statusBarsPadding()
            ) {
                val inSearchMode = isSearchActive || searchQuery.isNotEmpty()

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Back arrow when in search mode to quickly exit search
                    if (inSearchMode) {
                        IconButton(
                            onClick = {
                                isSearchActive = false
                                val hadQuery = searchQuery.isNotEmpty()
                                searchQuery = ""
                                searchJob?.cancel()
                                keyboardController?.hide()
                                focusManager.clearFocus()
                                if (hadQuery) {
                                    loadStreams("")
                                }
                            },
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Exit search",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    // Search Bar - Takes full remaining width with pixel-perfect alignment
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { newQuery ->
                            searchQuery = newQuery
                            if (newQuery.isNotEmpty()) {
                                isSearchActive = true
                            }
                            searchJob?.cancel()
                            searchJob = coroutineScope.launch {
                                delay(350)
                                loadStreams(newQuery)
                            }
                        },
                        textStyle = TextStyle(
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(TwitchPurple),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboardController?.hide()
                            focusManager.clearFocus()
                        }),
                        modifier = Modifier
                            .weight(1f)
                            .height(42.dp)
                            .onFocusChanged { focusState ->
                                if (focusState.isFocused) {
                                    isSearchActive = true
                                } else if (searchQuery.isEmpty()) {
                                    isSearchActive = false
                                }
                            },
                        decorationBox = { innerTextField ->
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(TwitchDarkCard, RoundedCornerShape(12.dp))
                                    .border(
                                        width = 1.dp,
                                        color = if (isSearchActive) TwitchPurple else Color.White.copy(alpha = 0.15f),
                                        shape = RoundedCornerShape(12.dp)
                                    )
                                    .padding(horizontal = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                if (!inSearchMode) {
                                    Icon(
                                        imageVector = Icons.Filled.Search,
                                        contentDescription = "Search",
                                        tint = TwitchPurple,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Box(
                                    modifier = Modifier.weight(1f),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (searchQuery.isEmpty()) {
                                        Text(
                                            text = if (inSearchMode) "Search streamers or games..." else "Search streamers...",
                                            fontSize = 13.sp,
                                            color = TwitchTextDim
                                        )
                                    }
                                    innerTextField()
                                }
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(
                                        onClick = {
                                            searchQuery = ""
                                            loadStreams("")
                                        },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Clear",
                                            tint = TwitchTextDim,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    )

                    // Secondary actions: Only visible when NOT in search mode
                    if (!inSearchMode) {
                        // Multistream Quick Icon
                        IconButton(
                            onClick = onOpenMultiStream,
                            modifier = Modifier.size(38.dp)
                        ) {
                            Icon(
                                Icons.Filled.GridView,
                                contentDescription = "Multistream",
                                tint = TwitchTeal,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        // Account / Login Avatar
                        Box {
                            IconButton(
                                onClick = {
                                    if (currentUser != null) {
                                        showUserMenu = true
                                    } else {
                                        showLoginDialog = true
                                    }
                                },
                                modifier = Modifier.size(38.dp)
                            ) {
                                val user = currentUser
                                if (user != null && user.profileImageUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = ImageRequest.Builder(LocalContext.current)
                                            .data(user.profileImageUrl)
                                            .crossfade(true)
                                            .build(),
                                        contentDescription = user.displayName,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(CircleShape)
                                            .border(1.5.dp, TwitchPurple, CircleShape)
                                    )
                                } else {
                                    Icon(
                                        imageVector = if (currentUser != null) Icons.Filled.AccountCircle else Icons.AutoMirrored.Filled.Login,
                                        contentDescription = "Account",
                                        tint = if (currentUser != null) TwitchPurple else Color.White,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }

                            if (currentUser != null) {
                                DropdownMenu(
                                    expanded = showUserMenu,
                                    onDismissRequest = { showUserMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Column {
                                                Text(
                                                    text = currentUser?.displayName ?: "",
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White
                                                )
                                                Text(
                                                    text = "@${currentUser?.login ?: ""}",
                                                    fontSize = 12.sp,
                                                    color = TwitchTextDim
                                                )
                                            }
                                        },
                                        onClick = { },
                                        enabled = false
                                    )
                                    HorizontalDivider()
                                    DropdownMenuItem(
                                        text = { Text("Log Out", color = MaterialTheme.colorScheme.error) },
                                        leadingIcon = {
                                            Icon(
                                                Icons.AutoMirrored.Filled.Logout,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.error
                                            )
                                        },
                                        onClick = {
                                            showUserMenu = false
                                            authManager.logout()
                                            followedStreams = emptyList()
                                            selectedTab = 1
                                        }
                                    )
                                }
                            }
                        }

                        // Settings & Actions Menu
                        Box {
                            IconButton(
                                onClick = { showMenu = true },
                                modifier = Modifier.size(38.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Tune,
                                    contentDescription = "Menu",
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Refresh Feed") },
                                    leadingIcon = { Icon(Icons.Filled.Refresh, contentDescription = null, tint = Color.White) },
                                    onClick = {
                                        showMenu = false
                                        loadStreams(searchQuery)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (isCompactFeed) "Switch to Large Cards" else "Switch to Compact View") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (isCompactFeed) Icons.Filled.ViewAgenda else Icons.Filled.ViewCompact,
                                            contentDescription = null,
                                            tint = TwitchPurple
                                        )
                                    },
                                    onClick = {
                                        showMenu = false
                                        val next = !isCompactFeed
                                        isCompactFeed = next
                                        settingsManager.setCompactFeed(next)
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

                // Tabs: Following vs Top Live (shown when not searching)
                if (searchQuery.isEmpty()) {
                    TabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = TwitchDark,
                        contentColor = TwitchPurple,
                        divider = {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                        }
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Favorite,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selectedTab == 0) TwitchPurple else TwitchTextDim
                                    )
                                    Text(
                                        text = "Following",
                                        fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = if (selectedTab == 0) Color.White else TwitchTextDim
                                    )
                                    if (followedStreams.isNotEmpty()) {
                                        Surface(
                                            color = if (selectedTab == 0) TwitchPurple else TwitchDarkCard,
                                            shape = CircleShape
                                        ) {
                                            Text(
                                                text = "${followedStreams.size}",
                                                color = Color.White,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Tv,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp),
                                        tint = if (selectedTab == 1) TwitchPurple else TwitchTextDim
                                    )
                                    Text(
                                        text = "Top Live",
                                        fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 14.sp,
                                        color = if (selectedTab == 1) Color.White else TwitchTextDim
                                    )
                                }
                            }
                        )
                    }
                }
            }
        },
        containerColor = TwitchDark,
        modifier = modifier
    ) { paddingValues ->
        val pullRefreshState = rememberPullToRefreshState()
        PullToRefreshBox(
            isRefreshing = isLoading && (streams.isNotEmpty() || followedStreams.isNotEmpty()),
            onRefresh = { loadStreams(searchQuery) },
            state = pullRefreshState,
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            indicator = {
                PullToRefreshDefaults.Indicator(
                    state = pullRefreshState,
                    isRefreshing = isLoading && (streams.isNotEmpty() || followedStreams.isNotEmpty()),
                    containerColor = TwitchDarkCard,
                    color = TwitchPurple,
                    modifier = Modifier.align(Alignment.TopCenter)
                )
            }
        ) {
            if (searchQuery.isNotEmpty()) {
                // Search Results Mode
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
                            text = "No channels found for \"$searchQuery\"",
                            color = TwitchTextDim,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = if (isCompactFeed) GridCells.Adaptive(minSize = 340.dp) else GridCells.Adaptive(minSize = 300.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompactFeed) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "SEARCH RESULTS FOR \"${searchQuery}\"",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                        items(streams, key = { it.id + it.login }) { item ->
                            if (isCompactFeed) {
                                CompactStreamCard(
                                    stream = item,
                                    thumbnailWidthDp = thumbnailSizeDp,
                                    onClick = { onChannelSelected(item.login) }
                                )
                            } else {
                                LiveStreamCard(
                                    stream = item,
                                    onClick = { onChannelSelected(item.login) }
                                )
                            }
                        }
                    }
                }
            } else if (selectedTab == 0) {
                // Following Tab
                if (currentUser == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                            colors = CardDefaults.cardColors(containerColor = TwitchDarkCard),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    Icons.Filled.AccountCircle,
                                    contentDescription = null,
                                    tint = TwitchPurple,
                                    modifier = Modifier.size(56.dp)
                                )
                                Text(
                                    text = "Sign in to Twitch",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Text(
                                    text = "Log in to see your followed live channels, chat with your account, and sync emotes.",
                                    color = TwitchTextDim,
                                    fontSize = 13.sp,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = { showLoginDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple),
                                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 10.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("Log In", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                } else if (isLoading && followedStreams.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = TwitchPurple)
                    }
                } else if (followedStreams.isEmpty()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Filled.TvOff,
                            contentDescription = null,
                            tint = TwitchTextDim,
                            modifier = Modifier.size(56.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "No followed channels live",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "None of the streamers you follow are live right now.",
                            color = TwitchTextDim,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { selectedTab = 1 },
                            colors = ButtonDefaults.buttonColors(containerColor = TwitchDarkCard),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Browse Top Live", color = TwitchPurple)
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = if (isCompactFeed) GridCells.Adaptive(minSize = 340.dp) else GridCells.Adaptive(minSize = 300.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompactFeed) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(TwitchRed)
                                )
                                Text(
                                    text = "LIVE FOLLOWED CHANNELS",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.5.sp
                                )
                                Text(
                                    text = "(${followedStreams.size})",
                                    color = TwitchPurple,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        items(followedStreams, key = { "followed_${it.id}_${it.login}" }) { item ->
                            if (isCompactFeed) {
                                CompactStreamCard(
                                    stream = item,
                                    thumbnailWidthDp = thumbnailSizeDp,
                                    onClick = { onChannelSelected(item.login) }
                                )
                            } else {
                                LiveStreamCard(
                                    stream = item,
                                    onClick = { onChannelSelected(item.login) }
                                )
                            }
                        }
                    }
                }
            } else {
                // Top Live Tab
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
                            text = "No active streams found",
                            color = TwitchTextDim,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = if (isCompactFeed) GridCells.Adaptive(minSize = 340.dp) else GridCells.Adaptive(minSize = 300.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(if (isCompactFeed) 8.dp else 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Quick horizontal followed channels preview row if any followed channels are live
                        if (currentUser != null && followedStreams.isNotEmpty()) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Column(modifier = Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(TwitchRed)
                                            )
                                            Text(
                                                text = "FOLLOWED CHANNELS",
                                                color = Color.White,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold,
                                                letterSpacing = 0.5.sp
                                            )
                                            Text(
                                                text = "(${followedStreams.size})",
                                                color = TwitchPurple,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                        Text(
                                            text = "View all",
                                            color = TwitchPurple,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.clickable { selectedTab = 0 }
                                        )
                                    }

                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                        contentPadding = PaddingValues(vertical = 4.dp)
                                    ) {
                                        items(followedStreams, key = { "top_followed_${it.id}_${it.login}" }) { item ->
                                            FollowedChannelCard(
                                                stream = item,
                                                onClick = { onChannelSelected(item.login) }
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Text(
                                text = "TOP LIVE CHANNELS",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }

                        items(streams, key = { it.id + it.login }) { item ->
                            if (isCompactFeed) {
                                CompactStreamCard(
                                    stream = item,
                                    thumbnailWidthDp = thumbnailSizeDp,
                                    onClick = { onChannelSelected(item.login) }
                                )
                            } else {
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
    }
}

@Composable
fun FollowedChannelCard(
    stream: LiveStreamItem,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = TwitchDarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.width(170.dp)
    ) {
        Column {
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
                        contentDescription = stream.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                Surface(
                    color = Color.Black.copy(alpha = 0.75f),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .padding(6.dp)
                        .align(Alignment.BottomStart)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(TwitchRed)
                        )
                        Text(
                            text = formatViewersCount(stream.viewersCount),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (stream.profileImageUrl.isNotEmpty()) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(stream.profileImageUrl)
                            .crossfade(true)
                            .build(),
                        contentDescription = stream.displayName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .border(1.5.dp, TwitchPurple, CircleShape)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stream.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (stream.gameName.isNotEmpty()) {
                        Text(
                            text = stream.gameName,
                            color = TwitchTeal,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
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
                if (stream.title != "Offline") {
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

@Composable
fun CompactStreamCard(
    stream: LiveStreamItem,
    thumbnailWidthDp: Int = 125,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = TwitchDarkCard),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Compact Thumbnail 16:9
            Box(
                modifier = Modifier
                    .width(thumbnailWidthDp.dp)
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(6.dp))
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

                // Live Tag
                if (stream.title != "Offline") {
                    Surface(
                        color = TwitchRed,
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "LIVE",
                            color = Color.White,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                        )
                    }
                }

                // Viewers Tag
                if (stream.viewersCount > 0) {
                    Surface(
                        color = Color.Black.copy(alpha = 0.8f),
                        shape = RoundedCornerShape(3.dp),
                        modifier = Modifier
                            .padding(4.dp)
                            .align(Alignment.BottomStart)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Box(modifier = Modifier.size(4.dp).clip(CircleShape).background(TwitchRed))
                            Text(
                                text = formatViewersCount(stream.viewersCount),
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            // Right: Stream Info
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (stream.profileImageUrl.isNotEmpty()) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(stream.profileImageUrl)
                                .crossfade(true)
                                .build(),
                            contentDescription = stream.displayName,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                        )
                    }
                    Text(
                        text = stream.displayName,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (stream.title.isNotEmpty()) {
                    Text(
                        text = stream.title,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                if (stream.gameName.isNotEmpty()) {
                    Text(
                        text = stream.gameName,
                        color = TwitchTeal,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
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
