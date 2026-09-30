package com.eetu.twitchapp.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { TwitchSettingsManager(context) }

    var autoMute by remember { mutableStateOf(settingsManager.isAutoMuteAds()) }
    var showOverlay by remember { mutableStateOf(settingsManager.isShowAdOverlay()) }
    var auto360p by remember { mutableStateOf(settingsManager.isAuto360pAds()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ad Protection Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TwitchDark)
            )
        },
        containerColor = TwitchDark
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Informational Card
            Card(
                colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "About Twitch Ads (SSAI)",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Twitch embeds advertisements directly into the video stream via Server-Side Ad Insertion (SSAI). When an ad break occurs, Twitch replaces the broadcaster's video feed with either a commercial or its purple 'Commercial break in progress' screen. Because this replacement happens on Twitch's servers, no app toggle can skip the break — but TwitchApp automatically detects the break and mutes the audio so you don't have to listen to loud commercials.",
                        color = TwitchTextDim,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }

            Text("Options", color = TwitchTeal, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)) {
                Column {
                    EmoteToggleRow(
                        title = "Automatic SSAI Muting",
                        description = "Instantly mutes the player during commercial breaks to protect your ears",
                        checked = autoMute,
                        onCheckedChange = {
                            autoMute = it
                            settingsManager.setAutoMuteAds(it)
                        }
                    )
                    HorizontalDivider(color = TwitchDarkSurface)
                    EmoteToggleRow(
                        title = "Hide Video (Placeholder Screen)",
                        description = "Covers the video with a 'Commercial break' card. Turn off to watch muted ads directly on screen with a top countdown pill.",
                        checked = showOverlay,
                        onCheckedChange = {
                            showOverlay = it
                            settingsManager.setShowAdOverlay(it)
                        }
                    )
                    HorizontalDivider(color = TwitchDarkSurface)
                    EmoteToggleRow(
                        title = "Desktop 360p Video Swap (Experimental)",
                        description = "Downscale stream to 360p during ads. Note: Twitch embeds ads into all qualities, so quality swap does not skip ads and may cause live buffering.",
                        checked = auto360p,
                        onCheckedChange = {
                            auto360p = it
                            settingsManager.setAuto360pAds(it)
                        }
                    )
                }
            }
        }
    }
}
