@file:OptIn(
    androidx.compose.material3.ExperimentalMaterial3Api::class
)

package com.eetu.twitchapp.ui.player

import android.view.ViewGroup
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.window.Dialog
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.MainActivity
import com.eetu.twitchapp.data.model.LiveStreamItem
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun NativeTwitchPlayer(
    exoPlayer: ExoPlayer,
    streamInfo: LiveStreamItem?,
    channelName: String,
    isLoading: Boolean,
    errorMessage: String?,
    availableQualities: List<VideoTrackOption> = emptyList(),
    selectedQuality: String = "auto",
    isAudioOnly: Boolean = false,
    isLowLatency: Boolean = true,
    lowLatencyBufferMs: Int = 4500,
    isPipEnabled: Boolean = true,
    backgroundAudioEnabled: Boolean = true,
    onSelectQuality: (String) -> Unit = {},
    onToggleAudioOnly: () -> Unit = {},
    onToggleLowLatency: () -> Unit = {},
    onSelectLowLatencyBuffer: (Int) -> Unit = {},
    onTogglePipEnabled: (Boolean) -> Unit = {},
    onToggleBackgroundAudio: (Boolean) -> Unit = {},
    isOledMode: Boolean = false,
    onToggleOledMode: (Boolean) -> Unit = {},
    onMinimize: () -> Unit,
    onToggleFullscreen: () -> Unit = {},
    isFullscreen: Boolean = false,
    showChatToggle: Boolean = false,
    isChatVisible: Boolean = true,
    onToggleChat: () -> Unit = {},
    adBreakActive: Boolean = false,
    adBreakRemainingSeconds: Int = 0,
    isAutoMuteAds: Boolean = true,
    isShowAdOverlay: Boolean = true,
    isAuto360pAds: Boolean = true,
    onToggleAutoMuteAds: (Boolean) -> Unit = {},
    onToggleShowAdOverlay: (Boolean) -> Unit = {},
    onToggleAuto360pAds: (Boolean) -> Unit = {},
    onDismissAdBreak: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val activity = context as? MainActivity
    val configuration = androidx.compose.ui.platform.LocalConfiguration.current
    val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
    val twitchColors = LocalTwitchColors.current

    var showControls by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var showStreamDetailsDialog by remember { mutableStateOf(false) }
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }

    var chatFeedbackText by remember { mutableStateOf<String?>(null) }
    var chatFeedbackJob by remember { mutableStateOf<Job?>(null) }
    val coroutineScope = rememberCoroutineScope()

    val scaleAnim = remember { Animatable(1f) }
    val offsetXAnim = remember { Animatable(0f) }
    val offsetYAnim = remember { Animatable(0f) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    var isZooming by remember { mutableStateOf(false) }
    var lastZoomTime by remember { mutableLongStateOf(0L) }
    var showPillByTimer by remember { mutableStateOf(false) }

    val isZoomed = scaleAnim.value > 1.05f

    LaunchedEffect(isZooming, isZoomed, lastZoomTime) {
        if (isZooming || isZoomed) {
            showPillByTimer = true
            delay(2500)
            showPillByTimer = false
        } else {
            showPillByTimer = false
        }
    }

    val showZoomPill = isZoomed && (isZooming || showPillByTimer || showControls)

    // Reset zoom when stream changes or orientation switches
    LaunchedEffect(channelName) {
        scaleAnim.snapTo(1f)
        offsetXAnim.snapTo(0f)
        offsetYAnim.snapTo(0f)
    }

    LaunchedEffect(isLandscape) {
        scaleAnim.snapTo(1f)
        offsetXAnim.snapTo(0f)
        offsetYAnim.snapTo(0f)
    }

    fun triggerChatToggle() {
        val nextVisible = !isChatVisible
        onToggleChat()
        chatFeedbackJob?.cancel()
        chatFeedbackText = if (nextVisible) "Chat visible" else "Chat hidden"
        chatFeedbackJob = coroutineScope.launch {
            delay(1100)
            chatFeedbackText = null
        }
    }

    // Auto-hide controls after 3.5 seconds
    LaunchedEffect(showControls) {
        if (showControls) {
            delay(3500)
            showControls = false
        }
    }

    // Keep screen on while video/audio is playing to prevent display sleep
    DisposableEffect(exoPlayer.isPlaying) {
        val window = activity?.window
        if (exoPlayer.isPlaying) {
            window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Fullscreen / Status bar & Navigation bar handling:
    // In portrait: show system bars
    // In landscape: hide system bars (status bar + gesture pill) to avoid video overlap and letterboxing
    DisposableEffect(isLandscape) {
        activity?.setLandscapeSystemBars(isLandscape)
        onDispose {
            activity?.setLandscapeSystemBars(false)
        }
    }

    Box(
        modifier = modifier
            .background(Color.Black)
            .clipToBounds()
            .onSizeChanged { containerSize = it }
            .pointerInput(Unit) {
                detectTwoFingerZoomAndPan(
                    onGesture = { centroid, pan, zoom ->
                        if (containerSize.width > 0 && containerSize.height > 0) {
                            val oldScale = scaleAnim.value
                            val newScale = (oldScale * zoom).coerceIn(0.85f, 5.5f)
                            val effectiveZoom = newScale / oldScale

                            val center = Offset(containerSize.width / 2f, containerSize.height / 2f)
                            val curX = offsetXAnim.value
                            val curY = offsetYAnim.value

                            val newX = curX - (centroid.x - center.x - curX) * (effectiveZoom - 1f) + pan.x
                            val newY = curY - (centroid.y - center.y - curY) * (effectiveZoom - 1f) + pan.y

                            val maxPanX = (containerSize.width * (newScale - 1f) / 2f).coerceAtLeast(0f)
                            val maxPanY = (containerSize.height * (newScale - 1f) / 2f).coerceAtLeast(0f)

                            val overscrollLimitX = maxPanX + containerSize.width * 0.15f
                            val overscrollLimitY = maxPanY + containerSize.height * 0.15f

                            coroutineScope.launch {
                                scaleAnim.snapTo(newScale)
                                offsetXAnim.snapTo(newX.coerceIn(-overscrollLimitX, overscrollLimitX))
                                offsetYAnim.snapTo(newY.coerceIn(-overscrollLimitY, overscrollLimitY))
                            }
                            isZooming = true
                            lastZoomTime = System.currentTimeMillis()
                        }
                    },
                    onGestureEnd = {
                        isZooming = false
                        lastZoomTime = System.currentTimeMillis()
                        if (containerSize.width > 0 && containerSize.height > 0) {
                            val curScale = scaleAnim.value
                            val curX = offsetXAnim.value
                            val curY = offsetYAnim.value

                            if (curScale < 1.05f) {
                                coroutineScope.launch {
                                    launch { scaleAnim.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { offsetXAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { offsetYAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                                }
                            } else {
                                val targetScale = curScale.coerceIn(1.0f, 5.0f)
                                val maxPanX = (containerSize.width * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val maxPanY = (containerSize.height * (targetScale - 1f) / 2f).coerceAtLeast(0f)
                                val targetX = curX.coerceIn(-maxPanX, maxPanX)
                                val targetY = curY.coerceIn(-maxPanY, maxPanY)

                                coroutineScope.launch {
                                    launch { scaleAnim.animateTo(targetScale, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { offsetXAnim.animateTo(targetX, tween(250, easing = FastOutSlowInEasing)) }
                                    launch { offsetYAnim.animateTo(targetY, tween(250, easing = FastOutSlowInEasing)) }
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(isLandscape) {
                var totalDragY = 0f
                var totalDragX = 0f
                var isDragging = false
                detectDragGestures(
                    onDragStart = {
                        totalDragY = 0f
                        totalDragX = 0f
                        isDragging = false
                    },
                    onDrag = { change, dragAmount ->
                        totalDragY += dragAmount.y
                        totalDragX += dragAmount.x
                        if (kotlin.math.abs(totalDragY) > 20f || kotlin.math.abs(totalDragX) > 20f) {
                            isDragging = true
                            change.consume()
                        }
                    },
                    onDragEnd = {
                        if (isDragging) {
                            val threshold = 40.dp.toPx()
                            if (kotlin.math.abs(totalDragY) > kotlin.math.abs(totalDragX)) {
                                if (!isLandscape && totalDragY < -threshold) {
                                    onToggleFullscreen()
                                } else if (!isLandscape && totalDragY > threshold) {
                                    onMinimize()
                                } else if (isLandscape && totalDragY > threshold) {
                                    onToggleFullscreen()
                                }
                            }
                        }
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { showControls = !showControls },
                    onDoubleTap = {
                        if (scaleAnim.value > 1.05f) {
                            coroutineScope.launch {
                                launch { scaleAnim.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
                                launch { offsetXAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                                launch { offsetYAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                            }
                            chatFeedbackJob?.cancel()
                            chatFeedbackText = "Zoom reset (1.0x)"
                            chatFeedbackJob = coroutineScope.launch {
                                delay(1000)
                                chatFeedbackText = null
                            }
                        } else {
                            triggerChatToggle()
                        }
                    }
                )
            }
    ) {
        // ExoPlayer Surface View (only if not audio only)
        if (!isAudioOnly) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = false
                        this.keepScreenOn = true
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        this.resizeMode = resizeMode
                        setBackgroundColor(android.graphics.Color.BLACK)
                    }
                },
                update = { playerView ->
                    playerView.player = exoPlayer
                    playerView.resizeMode = resizeMode
                    playerView.keepScreenOn = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        scaleX = scaleAnim.value
                        scaleY = scaleAnim.value
                        translationX = offsetXAnim.value
                        translationY = offsetYAnim.value
                    }
            )
        } else {
            // Audio Only Mode UI
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(twitchColors.background),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Filled.Headphones,
                        contentDescription = "Audio Only",
                        tint = TwitchPurple,
                        modifier = Modifier.size(52.dp)
                    )
                    Text(
                        text = "Audio Only Mode Active",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Text(
                        text = streamInfo?.displayName ?: channelName,
                        color = TwitchTextDim,
                        fontSize = 13.sp
                    )
                    OutlinedButton(
                        onClick = onToggleAudioOnly,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TwitchPurple),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Videocam, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Enable Video", fontSize = 12.sp)
                    }
                }
            }
        }

        // Loading Indicator
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    color = TwitchPurple,
                    modifier = Modifier.size(48.dp),
                    strokeWidth = 3.5.dp
                )
            }
        }

        // Error / Offline Message
        if (errorMessage != null && !isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f)),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(24.dp)
                ) {
                    Icon(
                        Icons.Filled.TvOff,
                        contentDescription = null,
                        tint = TwitchRed,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        text = errorMessage,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Commercial Break Placeholder & Countdown Overlay
        if (adBreakActive && isShowAdOverlay) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(TwitchDark),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    if (streamInfo?.profileImageUrl?.isNotEmpty() == true) {
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
                        Spacer(modifier = Modifier.height(4.dp))
                    }

                    Text(
                        text = "Commercial Break in Progress",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Surface(
                        color = TwitchDarkCard,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (isAutoMuteAds) {
                                Icon(
                                    Icons.Filled.VolumeOff,
                                    contentDescription = null,
                                    tint = TwitchPurple,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Text(
                                text = if (adBreakRemainingSeconds > 0) "${adBreakRemainingSeconds}s remaining" else "Ending soon...",
                                color = TwitchTeal,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            if (isAutoMuteAds) {
                                Text(
                                    text = "• Muted",
                                    color = TwitchTextDim,
                                    fontSize = 11.sp
                                )
                            }
                            if (isAuto360pAds) {
                                Text(
                                    text = "• 360p",
                                    color = TwitchTeal,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Stream with ${streamInfo?.displayName ?: channelName} will resume shortly",
                        color = TwitchTextDim,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onToggleShowAdOverlay(false) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TwitchPurple.copy(alpha = 0.7f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = TwitchTeal
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Watch Video",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        OutlinedButton(
                            onClick = { onDismissAdBreak() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, TwitchTextDim.copy(alpha = 0.5f)),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp),
                                tint = TwitchTextDim
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Dismiss",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        } else if (adBreakActive) {
            // Subtle top banner badge with countdown when video is showing
            Surface(
                color = Color.Black.copy(alpha = 0.85f),
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TwitchPurple),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 16.dp)
                    .clickable {
                        onDismissAdBreak()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (isAutoMuteAds) {
                        Icon(
                            Icons.Filled.VolumeOff,
                            contentDescription = null,
                            tint = TwitchPurple,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Text(
                        text = if (adBreakRemainingSeconds > 0) "Commercial • ${adBreakRemainingSeconds}s remaining" else "Commercial break",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    if (isAutoMuteAds) {
                        Text(
                            text = "(Muted)",
                            color = TwitchTextDim,
                            fontSize = 11.sp
                        )
                    }
                    if (isAuto360pAds) {
                        Text(
                            text = "(360p)",
                            color = TwitchTeal,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        // Brief visual indicator pill on double tap
        AnimatedVisibility(
            visible = chatFeedbackText != null,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.Center)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(16.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, TwitchPurple.copy(alpha = 0.6f)),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (chatFeedbackText?.contains("visible") == true) Icons.AutoMirrored.Filled.Chat else Icons.Filled.ChatBubbleOutline,
                        contentDescription = null,
                        tint = TwitchPurple,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = chatFeedbackText ?: "",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Floating Zoom Level Indicator Pill
        AnimatedVisibility(
            visible = showZoomPill,
            enter = fadeIn(tween(150)),
            exit = fadeOut(tween(300)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = if (adBreakActive && isShowAdOverlay) 16.dp else if (adBreakActive) 54.dp else 16.dp)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.82f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, TwitchPurple.copy(alpha = 0.7f)),
                shadowElevation = 6.dp,
                modifier = Modifier.clickable {
                    coroutineScope.launch {
                        launch { scaleAnim.animateTo(1f, tween(250, easing = FastOutSlowInEasing)) }
                        launch { offsetXAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                        launch { offsetYAnim.animateTo(0f, tween(250, easing = FastOutSlowInEasing)) }
                    }
                    chatFeedbackJob?.cancel()
                    chatFeedbackText = "Zoom reset (1.0x)"
                    chatFeedbackJob = coroutineScope.launch {
                        delay(1000)
                        chatFeedbackText = null
                    }
                }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.ZoomIn,
                        contentDescription = "Zoomed",
                        tint = TwitchPurple,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = String.format(java.util.Locale.US, "%.1fx", scaleAnim.value),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "• Reset",
                        color = TwitchTextDim,
                        fontSize = 11.sp
                    )
                }
            }
        }

        // Overlay Controls (Fade In / Out)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.45f))
            ) {
                // Top Bar: Minimize (Slide down) button + Streamer info + Settings
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.TopCenter),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.weight(1f, fill = false)
                    ) {
                        IconButton(
                            onClick = onMinimize,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Minimize video",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        Column(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .clickable {
                                    if (streamInfo != null) {
                                        showStreamDetailsDialog = true
                                    }
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = streamInfo?.displayName ?: channelName,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (streamInfo != null && streamInfo.gameName.isNotEmpty()) {
                                    Text(
                                        text = "• ${streamInfo.gameName}",
                                        color = TwitchTextDim,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (streamInfo != null && streamInfo.title.isNotEmpty()) {
                                Text(
                                    text = streamInfo.title,
                                    color = Color.White.copy(alpha = 0.9f),
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    modifier = Modifier.basicMarquee(iterations = Int.MAX_VALUE)
                                )
                            }
                        }
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Live badge & Viewers
                        if (streamInfo != null && streamInfo.viewersCount > 0) {
                            Surface(
                                color = TwitchRed,
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(5.dp)
                                            .clip(CircleShape)
                                            .background(Color.White)
                                    )
                                    Text(
                                        text = formatViewers(streamInfo.viewersCount),
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }



                        // Chat Toggle Button (in landscape / horizontal mode)
                        if (showChatToggle) {
                            IconButton(
                                onClick = onToggleChat,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    if (isChatVisible) Icons.AutoMirrored.Filled.Chat else Icons.Filled.ChatBubbleOutline,
                                    contentDescription = if (isChatVisible) "Hide Chat" else "Show Chat",
                                    tint = if (isChatVisible) TwitchPurple else Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Settings Button (Quality, Low Latency, Background Audio, PiP)
                        IconButton(
                            onClick = { showSettingsSheet = true },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.Settings,
                                contentDescription = "Playback settings",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Center Play / Pause
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center
                ) {
                    IconButton(
                        onClick = {
                            if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                        },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                    ) {
                        Icon(
                            if (exoPlayer.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (exoPlayer.isPlaying) "Pause" else "Play",
                            tint = Color.White,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                // Bottom Bar: Quality badge, PiP, Fullscreen
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .align(Alignment.BottomCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Quick Quality Badge / Selector
                    Surface(
                        color = Color.Black.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.clickable { showSettingsSheet = true }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                Icons.Filled.Tune,
                                contentDescription = null,
                                tint = TwitchPurple,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (isAudioOnly) "Audio Only" else selectedQuality.replace("auto", "Auto"),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    // Right: PiP & Fullscreen Buttons
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Direct Picture-in-Picture Button
                        IconButton(
                            onClick = {
                                (context as? MainActivity)?.enterPipMode()
                            },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                Icons.Filled.PictureInPictureAlt,
                                contentDescription = "Picture in Picture",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        // Fullscreen / Orientation Toggle Button
                        IconButton(
                            onClick = onToggleFullscreen,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                if (isFullscreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                                contentDescription = if (isFullscreen) "Exit Fullscreen" else "Enter Fullscreen",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // Modal Bottom Sheet for Playback Settings
    if (showSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showSettingsSheet = false },
            sheetState = sheetState,
            containerColor = TwitchDarkCard,
            dragHandle = { BottomSheetDefaults.DragHandle(color = TwitchTextDim) }
        ) {
            val scrollState = rememberScrollState()
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState)
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .navigationBarsPadding()
                    .padding(bottom = 36.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Player Controls & Settings",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )

                // Quality Selector Section
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Stream Quality",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = if (isAudioOnly) {
                                "Audio Only"
                            } else {
                                val currentOpt = availableQualities.find {
                                    it.id == selectedQuality || (it.id == "source" && selectedQuality.startsWith("${it.height}p"))
                                }
                                currentOpt?.label ?: selectedQuality.replace("auto", "Auto")
                            },
                            color = TwitchPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(availableQualities) { qualityOpt ->
                            val isSelected = if (qualityOpt.id == "audio_only") {
                                isAudioOnly
                            } else if (qualityOpt.id == "source") {
                                !isAudioOnly && (selectedQuality == "source" || selectedQuality.startsWith("${qualityOpt.height}p"))
                            } else {
                                !isAudioOnly && selectedQuality == qualityOpt.id
                            }

                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    onSelectQuality(qualityOpt.id)
                                },
                                label = {
                                    Text(
                                        text = qualityOpt.label,
                                        fontSize = 12.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = TwitchPurple,
                                    selectedLabelColor = Color.White,
                                    containerColor = TwitchDark,
                                    labelColor = TwitchTextDim
                                )
                            )
                        }
                    }
                }

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                // Low Latency Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Low Latency Mode",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Reduces delay to see chat reactions in real time",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isLowLatency,
                        onCheckedChange = { onToggleLowLatency() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TwitchPurple
                        )
                    )
                }

                if (isLowLatency) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.05f)),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            var sliderBufferSeconds by remember(lowLatencyBufferMs) {
                                mutableFloatStateOf(lowLatencyBufferMs / 1000f)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Target Buffer",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold
                                )

                                val bufferLabel = when {
                                    sliderBufferSeconds < 3.0f -> "Ultra Low Delay"
                                    sliderBufferSeconds < 4.5f -> "Balanced"
                                    sliderBufferSeconds < 6.0f -> "Safe (Anti-Freeze)"
                                    else -> "Extra Safe"
                                }
                                val badgeColor = when {
                                    sliderBufferSeconds < 3.0f -> Color(0xFFFFB703)
                                    sliderBufferSeconds < 4.5f -> TwitchPurple
                                    sliderBufferSeconds < 6.0f -> TwitchTeal
                                    else -> Color(0xFF48CAE4)
                                }

                                Surface(
                                    color = badgeColor.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(6.dp),
                                    border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "${String.format(java.util.Locale.US, "%.1f", sliderBufferSeconds)}s • $bufferLabel",
                                        color = badgeColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Preset Chips Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                val presets = listOf(
                                    "Ultra (2.0s)" to 2000,
                                    "Balanced (3.5s)" to 3500,
                                    "Safe (5.0s)" to 5000,
                                    "Extra (6.5s)" to 6500
                                )
                                presets.forEach { (label, ms) ->
                                    val isSelected = Math.abs(sliderBufferSeconds * 1000 - ms) < 250
                                    Surface(
                                        onClick = {
                                            sliderBufferSeconds = ms / 1000f
                                            onSelectLowLatencyBuffer(ms)
                                        },
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) TwitchPurple else Color.White.copy(alpha = 0.08f),
                                        border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text(
                                            text = label,
                                            color = if (isSelected) Color.White else TwitchTextDim,
                                            fontSize = 10.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            textAlign = TextAlign.Center,
                                            modifier = Modifier.padding(vertical = 6.dp)
                                        )
                                    }
                                }
                            }

                            // Fine-tuning slider
                            Slider(
                                value = sliderBufferSeconds,
                                onValueChange = { sliderBufferSeconds = it },
                                onValueChangeFinished = {
                                    onSelectLowLatencyBuffer((sliderBufferSeconds * 1000).toInt())
                                },
                                valueRange = 1.5f..8.0f,
                                steps = 12,
                                colors = SliderDefaults.colors(
                                    thumbColor = TwitchPurple,
                                    activeTrackColor = TwitchPurple,
                                    inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text(
                                text = if (sliderBufferSeconds < 3.5f) {
                                    "Minimal delay (~1-2s). If stream freezes on network jitter, choose Safe or 4.5s+."
                                } else {
                                    "Safe buffer (~2+ chunks ahead) protects against freezes while keeping latency low."
                                },
                                color = TwitchTextDim,
                                fontSize = 11.sp,
                                lineHeight = 14.sp
                            )
                        }
                    }
                }

                // Audio Only Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Audio-Only Mode",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Disables video decoding to save battery and data",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isAudioOnly,
                        onCheckedChange = { onToggleAudioOnly() },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TwitchPurple
                        )
                    )
                }

                // Picture in Picture Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Picture-in-Picture (PiP)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Auto-enter floating player when swiping to home",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isPipEnabled,
                        onCheckedChange = onTogglePipEnabled,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TwitchPurple
                        )
                    )
                }

                // Background Audio Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background Audio",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Keep playing audio when screen is locked or app closed",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = backgroundAudioEnabled,
                        onCheckedChange = onToggleBackgroundAudio,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TwitchPurple
                        )
                    )
                }

                // Full OLED Black Mode Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Full OLED Black Mode",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Pure #000000 black background for maximum OLED battery savings",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isOledMode,
                        onCheckedChange = onToggleOledMode,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TwitchPurple
                        )
                    )
                }

                // Auto-Mute Commercials Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Auto-Mute Commercials",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Automatically mute stream audio during ad breaks",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isAutoMuteAds,
                        onCheckedChange = onToggleAutoMuteAds,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TwitchPurple
                        )
                    )
                }

                // Commercial Break Overlay Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Hide Video (Placeholder Screen)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Covers video with a 'Commercial break' card. Turn OFF to watch muted ads directly on screen.",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isShowAdOverlay,
                        onCheckedChange = onToggleShowAdOverlay,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TwitchPurple
                        )
                    )
                }

                // Desktop 360p Video Swap Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "360p Video Swap (Experimental)",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "Downscales stream to 360p during ads. May cause buffering on live streams.",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }
                    Switch(
                        checked = isAuto360pAds,
                        onCheckedChange = onToggleAuto360pAds,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = TwitchPurple
                        )
                    )
                }
            }
        }
    }

    if (showStreamDetailsDialog && streamInfo != null) {
        StreamDetailsDialog(
            streamInfo = streamInfo,
            onDismiss = { showStreamDetailsDialog = false }
        )
    }
}

@Composable
private fun StreamDetailsDialog(
    streamInfo: LiveStreamItem,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = TwitchDarkCard,
            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Header with streamer avatar & name
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        if (streamInfo.profileImageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(streamInfo.profileImageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = streamInfo.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(38.dp)
                                    .clip(CircleShape)
                            )
                        }
                        Column {
                            Text(
                                text = streamInfo.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            if (streamInfo.gameName.isNotEmpty()) {
                                Text(
                                    text = streamInfo.gameName,
                                    color = TwitchPurple,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = TwitchTextDim,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                // Title
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = "STREAM TITLE",
                        color = TwitchTextDim,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = streamInfo.title,
                        color = Color.White,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }

                // Stats (Viewers, etc)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = TwitchDark,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("VIEWERS", color = TwitchTextDim, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = formatViewers(streamInfo.viewersCount),
                                color = TwitchRed,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Close", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

private fun formatViewers(count: Int): String {
    return when {
        count >= 1_000_000 -> String.format("%.1fM", count / 1_000_000.0)
        count >= 1_000 -> String.format("%.1fK", count / 1_000.0)
        else -> count.toString()
    }
}

private suspend fun PointerInputScope.detectTwoFingerZoomAndPan(
    onGesture: (centroid: Offset, pan: Offset, zoom: Float) -> Unit,
    onGestureEnd: () -> Unit
) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false)
        var isTransforming = false
        var hasMultiplePointers = false

        do {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressedChanges = event.changes.filter { it.pressed }
            val pressedCount = pressedChanges.size

            if (pressedCount >= 2) {
                hasMultiplePointers = true
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                val centroid = event.calculateCentroid(useCurrent = false)

                if (zoomChange != 1f || panChange != Offset.Zero) {
                    isTransforming = true
                    onGesture(centroid, panChange, zoomChange)
                }

                // Consume all pointer changes so single taps, double taps, and parent draggables are suppressed
                event.changes.forEach {
                    it.consume()
                }
            } else {
                if (hasMultiplePointers) {
                    // Finger lifted after multi-touch; continue consuming until all pointers are released
                    event.changes.forEach {
                        it.consume()
                    }
                }
                if (pressedCount < 2 && isTransforming) {
                    isTransforming = false
                    onGestureEnd()
                }
            }
        } while (event.changes.any { it.pressed })

        if (isTransforming) {
            onGestureEnd()
        }
    }
}
