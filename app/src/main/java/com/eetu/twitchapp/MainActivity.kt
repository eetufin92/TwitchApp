package com.eetu.twitchapp

import android.Manifest
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.navigation3.runtime.NavEntry
import androidx.navigation3.runtime.rememberNavBackStack
import androidx.navigation3.ui.NavDisplay
import com.eetu.twitchapp.navigation.Destination
import com.eetu.twitchapp.service.PlaybackService
import com.eetu.twitchapp.ui.components.TwitchWebView
import com.eetu.twitchapp.ui.home.HomeScreen
import com.eetu.twitchapp.ui.multistream.MultiStreamScreen
import com.eetu.twitchapp.ui.player.CollapsiblePlayerScaffold
import com.eetu.twitchapp.ui.player.PlayerViewModel
import com.eetu.twitchapp.ui.settings.*
import com.eetu.twitchapp.ui.theme.TwitchAppTheme

class MainActivity : ComponentActivity() {
    private var isPlayerVisible = true
    private var isFullscreen = false
    private var isInPip = mutableStateOf(false)
    private var intentUrl = mutableStateOf<String?>(null)
    private var videoDimensions = Pair(0, 0)

    companion object {
        var isAppVisible = false
        var currentIsPlaying = false
        var currentTitle: String? = null
        var currentStreamer: String? = null
    }

    override fun onStart() {
        super.onStart()
        isAppVisible = true
        PlaybackService.stop(this)
    }

    override fun onStop() {
        super.onStop()
        isAppVisible = false
        if (currentIsPlaying) {
            PlaybackService.start(this, currentTitle, currentStreamer)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent) {
        if (intent.action == Intent.ACTION_VIEW) {
            intent.data?.toString()?.let { url ->
                intentUrl.value = url
            }
        }
    }

    override fun onUserLeaveHint() {
        if (isPlayerVisible && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            enterPipMode()
        }
        super.onUserLeaveHint()
    }

    private fun enterPipMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val (w, h) = videoDimensions
            val ratio = if (w > 0 && h > 0) {
                val r = w.toFloat() / h.toFloat()
                if (r < 1 / 2.39f) Rational(100, 239)
                else if (r > 2.39f) Rational(239, 100)
                else Rational(w, h)
            } else {
                Rational(16, 9)
            }

            val builder = PictureInPictureParams.Builder()
                .setAspectRatio(ratio)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(true)
                builder.setSeamlessResizeEnabled(true)
            }

            enterPictureInPictureMode(builder.build())
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: android.content.res.Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPip.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            intentUrl.value = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()

        setContent {
            TwitchAppTheme {
                val context = LocalContext.current

                // Request POST_NOTIFICATIONS permission on Android 13+
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    val launcher = rememberLauncherForActivityResult(
                        ActivityResultContracts.RequestPermission()
                    ) { }

                    LaunchedEffect(Unit) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val adBlockManager = remember { com.eetu.twitchapp.data.AdBlockManager.getInstance(context) }
                LaunchedEffect(Unit) {
                    adBlockManager.autoUpdateIfDue()
                }

                val playerViewModel = remember {
                    PlayerViewModel(application)
                }

                LaunchedEffect(intentUrl.value) {
                    val raw = intentUrl.value
                    if (!raw.isNullOrEmpty()) {
                        val clean = raw.trim()
                            .removePrefix("https://")
                            .removePrefix("http://")
                            .removePrefix("www.")
                            .removePrefix("m.")
                            .removePrefix("twitch.tv/")
                            .trim('/')
                        if (clean.isNotEmpty() && !clean.contains('/')) {
                            playerViewModel.playChannel(clean)
                        }
                    }
                }

                val backStack = rememberNavBackStack(Destination.Player())

                NavDisplay(
                    backStack = backStack,
                    onBack = { if (backStack.size > 1) backStack.removeAt(backStack.size - 1) }
                ) { destination ->
                    when (destination) {
                        is Destination.Player -> {
                            isPlayerVisible = true
                            NavEntry(key = destination) {
                                CollapsiblePlayerScaffold(
                                    playerViewModel = playerViewModel,
                                    onOpenMultiStream = { channel ->
                                        if (!backStack.any { it is Destination.MultiStream }) {
                                            val initial = if (channel.isNotEmpty()) listOf(channel) else emptyList()
                                            backStack.add(Destination.MultiStream(initial))
                                        }
                                    },
                                    onOpenSettings = {
                                        if (!backStack.contains(Destination.Settings)) {
                                            backStack.add(Destination.Settings)
                                        }
                                    }
                                ) {
                                    HomeScreen(
                                        onChannelSelected = { channel ->
                                            playerViewModel.playChannel(channel)
                                        },
                                        onOpenMultiStream = {
                                            if (!backStack.any { it is Destination.MultiStream }) {
                                                val activeCh = playerViewModel.currentChannel.value
                                                val initial = if (activeCh.isNotEmpty()) listOf(activeCh) else emptyList()
                                                backStack.add(Destination.MultiStream(initial))
                                            }
                                        },
                                        onOpenSettings = {
                                            if (!backStack.contains(Destination.Settings)) {
                                                backStack.add(Destination.Settings)
                                            }
                                        },
                                        onOpenEmoteSettings = {
                                            if (!backStack.contains(Destination.EmoteSettings)) {
                                                backStack.add(Destination.EmoteSettings)
                                            }
                                        },
                                        onOpenAdSettings = {
                                            if (!backStack.contains(Destination.AdSettings)) {
                                                backStack.add(Destination.AdSettings)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                        is Destination.MultiStream -> {
                            isPlayerVisible = false
                            NavEntry(key = destination) {
                                MultiStreamScreen(
                                    initialChannels = destination.initialChannels,
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }
                        }
                        is Destination.Settings -> {
                            isPlayerVisible = false
                            NavEntry(key = destination) {
                                SettingsScreen(
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    },
                                    onNavigateToEmotes = {
                                        if (!backStack.contains(Destination.EmoteSettings)) {
                                            backStack.add(Destination.EmoteSettings)
                                        }
                                    },
                                    onNavigateToAds = {
                                        if (!backStack.contains(Destination.AdSettings)) {
                                            backStack.add(Destination.AdSettings)
                                        }
                                    },
                                    onNavigateToAdBlock = {
                                        if (!backStack.contains(Destination.AdBlockSettings)) {
                                            backStack.add(Destination.AdBlockSettings)
                                        }
                                    },
                                    onNavigateToAppearance = {
                                        if (!backStack.contains(Destination.ChatAppearanceSettings)) {
                                            backStack.add(Destination.ChatAppearanceSettings)
                                        }
                                    }
                                )
                            }
                        }
                        is Destination.EmoteSettings -> {
                            isPlayerVisible = false
                            NavEntry(key = destination) {
                                EmoteSettingsScreen(
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }
                        }
                        is Destination.AdSettings -> {
                            isPlayerVisible = false
                            NavEntry(key = destination) {
                                AdSettingsScreen(
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }
                        }
                        is Destination.AdBlockSettings -> {
                            isPlayerVisible = false
                            NavEntry(key = destination) {
                                AdBlockSettingsScreen(
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }
                        }
                        is Destination.ChatAppearanceSettings -> {
                            isPlayerVisible = false
                            NavEntry(key = destination) {
                                ChatAppearanceSettingsScreen(
                                    onNavigateBack = {
                                        if (backStack.size > 1) backStack.removeAt(backStack.size - 1)
                                    }
                                )
                            }
                        }
                        else -> NavEntry(destination) {}
                    }
                }
            }
        }
    }
}
