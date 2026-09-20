package com.eetu.twitchapp.ui.components

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.view.View
import android.view.ViewGroup
import android.webkit.*
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.eetu.twitchapp.MainActivity
import com.eetu.twitchapp.bridge.TwitchAndroidBridge
import com.eetu.twitchapp.data.AdBlockManager
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.service.PlaybackService
import com.eetu.twitchapp.ui.theme.*
import java.io.ByteArrayInputStream
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TwitchWebView(
    modifier: Modifier = Modifier,
    initialUrl: String = "https://m.twitch.tv",
    onFullscreenStateChanged: (Boolean) -> Unit = {},
    onVideoDimensionsChanged: (Int, Int) -> Unit = { _, _ -> },
    onOpenSettings: () -> Unit = {},
    onOpenEmoteSettings: () -> Unit = {},
    onOpenAdSettings: () -> Unit = {},
    onOpenAdBlockSettings: () -> Unit = {},
    onOpenMultiStream: () -> Unit = {},
    isInPip: Boolean = false
) {
    val context = LocalContext.current
    val settingsManager = remember { TwitchSettingsManager(context) }
    val adBlockManager = remember { AdBlockManager.getInstance(context) }
    val configuration = LocalConfiguration.current
    val isTablet = configuration.screenWidthDp >= 600

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var currentUrl by remember { mutableStateOf(initialUrl) }
    var isFullscreen by remember { mutableStateOf(false) }
    var customViewRef by remember { mutableStateOf<View?>(null) }
    var customViewCallbackRef by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    // Ad state from bridge
    var isAdPlaying by remember { mutableStateOf(false) }
    var adCountdownText by remember { mutableStateOf("") }

    // Floating chat state
    var showFloatingChat by remember { mutableStateOf(false) }
    var currentChannel by remember { mutableStateOf("") }
    var currentTitle by remember { mutableStateOf("Twitch") }

    // Draggable Floating Menu Button position (in dp)
    var fabOffsetX by remember { mutableFloatStateOf(0f) }
    var fabOffsetY by remember { mutableFloatStateOf(0f) }
    var showFabMenu by remember { mutableStateOf(false) }

    // MediaSession
    val mediaSession = remember {
        MediaSession(context, "TwitchApp").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() {
                    webViewInstance?.evaluateJavascript("document.querySelector('video')?.play();", null)
                }
                override fun onPause() {
                    webViewInstance?.evaluateJavascript("document.querySelector('video')?.pause();", null)
                }
            })
            isActive = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            mediaSession.isActive = false
            mediaSession.release()
            PlaybackService.stop(context)
        }
    }

    // Audio becoming noisy receiver
    DisposableEffect(Unit) {
        val noisyReceiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(c: Context, intent: android.content.Intent) {
                if (intent.action == android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                    webViewInstance?.evaluateJavascript("document.querySelector('video')?.pause();", null)
                }
            }
        }
        val filter = android.content.IntentFilter(android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)
        context.registerReceiver(noisyReceiver, filter)
        onDispose {
            context.unregisterReceiver(noisyReceiver)
        }
    }

    // Back handling
    if (isFullscreen) {
        BackHandler {
            customViewCallbackRef?.onCustomViewHidden()
        }
    } else if (showFloatingChat) {
        BackHandler {
            showFloatingChat = false
        }
    } else if (canGoBack) {
        BackHandler {
            webViewInstance?.let {
                if (it.canGoBack()) {
                    it.goBack()
                    it.postDelayed({
                        canGoBack = it.canGoBack()
                        currentUrl = it.url ?: ""
                    }, 100)
                }
            }
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // Sleek Minimal TopBar in non-fullscreen mode
        if (!isFullscreen) {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(TwitchPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("T", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        Text(
                            text = if (currentChannel.isNotEmpty()) currentChannel else "TwitchApp",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White
                        )
                    }
                },
                actions = {
                    // Floating Chat toggle
                    IconButton(onClick = { showFloatingChat = !showFloatingChat }) {
                        Icon(
                            Icons.Filled.Chat,
                            contentDescription = "Floating Chat",
                            tint = if (showFloatingChat) TwitchPurple else Color.White
                        )
                    }
                    // Multistream button
                    IconButton(onClick = onOpenMultiStream) {
                        Icon(
                            Icons.Filled.GridView,
                            contentDescription = "Multistream",
                            tint = TwitchTeal
                        )
                    }
                    // Settings button (Direct and easy to find!)
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            Icons.Filled.Settings,
                            contentDescription = "Settings",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TwitchDark)
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Main WebView
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val swipeLayout = androidx.swiperefreshlayout.widget.SwipeRefreshLayout(ctx)
                    val webView = PersistentWebView(ctx).apply {
                        webViewInstance = this
                        setBackgroundColor(android.graphics.Color.BLACK)
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )

                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = true
                            setSupportZoom(true)
                            builtInZoomControls = false
                            displayZoomControls = false
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE

                            val forceDesktop = settingsManager.isDesktopMode() || isTablet
                            if (forceDesktop) {
                                userAgentString = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                            } else {
                                val customUA = settingsManager.getUserAgent()
                                if (customUA.isNotEmpty()) {
                                    userAgentString = customUA
                                }
                            }
                        }

                        val bridge = TwitchAndroidBridge(
                            context = ctx,
                            webView = this,
                            onAdStatusChanged = { isAd, countdown ->
                                isAdPlaying = isAd
                                adCountdownText = countdown
                            },
                            onVideoDimensionsChanged = { w, h ->
                                onVideoDimensionsChanged(w, h)
                            },
                            onMetadataChanged = { title, streamer ->
                                currentTitle = title
                                mediaSession.setMetadata(
                                    MediaMetadata.Builder()
                                        .putString(MediaMetadata.METADATA_KEY_TITLE, title)
                                        .putString(MediaMetadata.METADATA_KEY_ARTIST, streamer)
                                        .build()
                                )
                            },
                            onPlaybackStateChanged = { isPlaying ->
                                val activity = ctx.findActivity()
                                if (isPlaying) {
                                    activity?.window?.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                } else {
                                    activity?.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                                }
                                val state = if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED
                                mediaSession.setPlaybackState(
                                    PlaybackState.Builder()
                                        .setState(state, PlaybackState.PLAYBACK_POSITION_UNKNOWN, 1.0f)
                                        .setActions(PlaybackState.ACTION_PLAY or PlaybackState.ACTION_PAUSE or PlaybackState.ACTION_PLAY_PAUSE)
                                        .build()
                                )
                            },
                            onChannelChanged = { ch ->
                                currentChannel = ch
                            }
                        )
                        addJavascriptInterface(bridge, "TwitchAndroidBridge")

                        webViewClient = object : WebViewClient() {
                            // uBlock Origin Network Request Interception
                            override fun shouldInterceptRequest(
                                view: WebView?,
                                request: WebResourceRequest?
                            ): WebResourceResponse? {
                                val requestUrl = request?.url
                                if (requestUrl != null) {
                                    val host = requestUrl.host
                                    val urlString = requestUrl.toString()
                                    if (adBlockManager.isUrlBlocked(host, urlString)) {
                                        return WebResourceResponse(
                                            "text/plain",
                                            "UTF-8",
                                            204,
                                            "No Content",
                                            emptyMap(),
                                            ByteArrayInputStream(ByteArray(0))
                                        )
                                    }
                                }
                                return super.shouldInterceptRequest(view, request)
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                super.onPageStarted(view, url, favicon)
                                canGoBack = view?.canGoBack() ?: false
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                swipeLayout.isRefreshing = false
                                canGoBack = view?.canGoBack() ?: false
                                currentUrl = url ?: ""

                                // Inject core twitch scriptlet
                                try {
                                    val js = ctx.assets.open("twitch_injections.js").bufferedReader().use { it.readText() }
                                    view?.evaluateJavascript(js, null)
                                } catch (e: Exception) {
                                    android.util.Log.e("TwitchWebView", "Failed to inject scripts", e)
                                }

                                // Inject uBlock Origin cosmetic hiding CSS
                                val cosmeticCss = adBlockManager.getCosmeticCss()
                                if (cosmeticCss.isNotEmpty()) {
                                    val cssScript = """
                                        (function() {
                                            var style = document.getElementById('twitch-adblock-cosmetic');
                                            if (!style) {
                                                style = document.createElement('style');
                                                style.id = 'twitch-adblock-cosmetic';
                                                document.head.appendChild(style);
                                            }
                                            style.textContent = `$cosmeticCss`;
                                        })();
                                    """.trimIndent()
                                    view?.evaluateJavascript(cssScript, null)
                                }
                            }
                        }

                        webChromeClient = object : WebChromeClient() {
                            override fun onShowCustomView(view: View?, callback: CustomViewCallback?) {
                                if (customViewRef != null) {
                                    callback?.onCustomViewHidden()
                                    return
                                }

                                val activity = ctx.findActivity()
                                val contentFrame = activity?.findViewById<FrameLayout>(android.R.id.content)

                                view?.apply {
                                    setBackgroundColor(android.graphics.Color.BLACK)
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }

                                contentFrame?.addView(view)
                                view?.requestFocus()

                                customViewRef = view
                                customViewCallbackRef = callback
                                isFullscreen = true
                                onFullscreenStateChanged(true)
                            }

                            override fun onHideCustomView() {
                                val activity = ctx.findActivity()
                                val contentFrame = activity?.findViewById<FrameLayout>(android.R.id.content)

                                customViewRef?.let { contentFrame?.removeView(it) }
                                customViewRef = null
                                customViewCallbackRef?.onCustomViewHidden()
                                customViewCallbackRef = null
                                isFullscreen = false
                                onFullscreenStateChanged(false)
                            }
                        }

                        val targetUrl = if (isTablet && initialUrl == "https://m.twitch.tv") "https://www.twitch.tv" else initialUrl
                        loadUrl(targetUrl)
                    }

                    swipeLayout.addView(webView)
                    swipeLayout.setOnRefreshListener {
                        webView.reload()
                    }
                    swipeLayout
                },
                update = { swipeView ->
                    val swipeLayout = swipeView as androidx.swiperefreshlayout.widget.SwipeRefreshLayout
                    swipeLayout.isEnabled = !isFullscreen
                }
            )

            // Native Compose Ad Overlay (when ad is detected and enabled in settings)
            AnimatedVisibility(
                visible = isAdPlaying && settingsManager.isShowAdOverlay(),
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter)
            ) {
                Surface(
                    color = Color.Black.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.padding(top = if (isFullscreen) 24.dp else 16.dp, start = 16.dp, end = 16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(TwitchPurple),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Filled.VolumeOff,
                                contentDescription = "Muted",
                                tint = Color.White,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "Commercial Break In Progress",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp
                            )
                            Text(
                                text = if (adCountdownText.isNotEmpty()) "$adCountdownText • Audio Muted 🎧" else "Audio Muted to save your ears 🎧",
                                color = TwitchTextDim,
                                fontSize = 11.sp
                            )
                        }

                        TextButton(
                            onClick = {
                                webViewInstance?.evaluateJavascript("const v = document.querySelector('video'); if (v) { v.muted = false; v.volume = 1.0; }", null)
                            },
                            colors = ButtonDefaults.textButtonColors(contentColor = TwitchTeal)
                        ) {
                            Text("Unmute", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            // Floating Resizable Chat Overlay
            if (showFloatingChat && currentChannel.isNotEmpty()) {
                FloatingResizableChat(
                    channelName = currentChannel,
                    availableChannels = listOf(currentChannel),
                    onClose = { showFloatingChat = false },
                    modifier = Modifier.fillMaxSize()
                )
            }

            // FULLY DRAGGABLE Floating Action Menu Button (Movable anywhere on screen!)
            Box(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .offset { IntOffset(fabOffsetX.roundToInt(), fabOffsetY.roundToInt()) }
                        .padding(end = 16.dp, bottom = 16.dp)
                        .pointerInput(Unit) {
                            detectDragGestures { change, dragAmount ->
                                change.consume()
                                fabOffsetX += dragAmount.x
                                fabOffsetY += dragAmount.y
                            }
                        }
                ) {
                    FloatingActionButton(
                        onClick = { showFabMenu = true },
                        containerColor = TwitchPurple,
                        contentColor = Color.White,
                        shape = CircleShape,
                        modifier = Modifier
                            .size(52.dp)
                            .alpha(if (showFabMenu) 1.0f else 0.75f)
                            .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = "Twitch Controls", modifier = Modifier.size(24.dp))
                    }

                    DropdownMenu(
                        expanded = showFabMenu,
                        onDismissRequest = { showFabMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (showFloatingChat) "Close Floating Chat" else "Open Floating Chat") },
                            leadingIcon = { Icon(Icons.Filled.Chat, contentDescription = null, tint = TwitchPurple) },
                            onClick = {
                                showFabMenu = false
                                showFloatingChat = !showFloatingChat
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Multistream (Multi-View)") },
                            leadingIcon = { Icon(Icons.Filled.GridView, contentDescription = null, tint = TwitchTeal) },
                            onClick = {
                                showFabMenu = false
                                onOpenMultiStream()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Emote Settings (7TV, BTTV, FFZ)") },
                            leadingIcon = { Icon(Icons.Filled.Mood, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onOpenEmoteSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("SSAI Ad Muter Settings") },
                            leadingIcon = { Icon(Icons.Filled.Shield, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onOpenAdSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("uBlock Origin Filters") },
                            leadingIcon = { Icon(Icons.Filled.Security, contentDescription = null, tint = Color(0xFF00B4D8)) },
                            onClick = {
                                showFabMenu = false
                                onOpenAdBlockSettings()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Settings") },
                            leadingIcon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                            onClick = {
                                showFabMenu = false
                                onOpenSettings()
                            }
                        )
                    }
                }
            }
        }
    }
}

fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

@SuppressLint("ViewConstructor")
private class PersistentWebView(context: Context) : WebView(context) {
    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(View.VISIBLE)
    }

    override fun onVisibilityChanged(changedView: View, visibility: Int) {
        super.onVisibilityChanged(changedView, View.VISIBLE)
    }
}
