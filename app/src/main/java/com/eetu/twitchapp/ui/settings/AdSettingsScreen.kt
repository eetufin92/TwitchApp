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
                        text = "Twitch embeds advertisements directly into the video stream via Server-Side Ad Insertion (SSAI). When an ad plays, TwitchApp automatically detects the commercial break, mutes the ad audio, and replaces it with a clean placeholder until the live broadcast resumes.",
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
                        title = "Show 'Ad in Progress' Overlay",
                        description = "Display a soothing overlay with remaining countdown instead of the ad video",
                        checked = showOverlay,
                        onCheckedChange = {
                            showOverlay = it
                            settingsManager.setShowAdOverlay(it)
                        }
                    )
                }
            }
        }
    }
}
