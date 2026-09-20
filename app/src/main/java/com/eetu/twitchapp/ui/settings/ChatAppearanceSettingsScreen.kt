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
fun ChatAppearanceSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { TwitchSettingsManager(context) }

    var desktopMode by remember { mutableStateOf(settingsManager.isDesktopMode()) }
    var chatOpacity by remember { mutableFloatStateOf(settingsManager.getChatOpacity()) }
    var customUserAgent by remember { mutableStateOf(settingsManager.getUserAgent()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Chat & Appearance", fontWeight = FontWeight.Bold) },
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
            Text("Display & Layout", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)) {
                Column {
                    EmoteToggleRow(
                        title = "Force Desktop Layout",
                        description = "Always request full desktop Twitch layout with side-by-side stream & chat (Default on tablets)",
                        checked = desktopMode,
                        onCheckedChange = {
                            desktopMode = it
                            settingsManager.setDesktopMode(it)
                        }
                    )
                }
            }

            Text("Floating Chat Defaults", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Chat Background Opacity", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Spacer(modifier = Modifier.height(2.dp))
                    Text("Controls transparency when floating over the video player", color = TwitchTextDim, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Slider(
                            value = chatOpacity,
                            onValueChange = {
                                chatOpacity = it
                                settingsManager.setChatOpacity(it)
                            },
                            valueRange = 0.3f..1.0f,
                            modifier = Modifier.weight(1f),
                            colors = SliderDefaults.colors(
                                thumbColor = TwitchPurple,
                                activeTrackColor = TwitchPurple
                            )
                        )
                        Text(
                            text = "${(chatOpacity * 100).toInt()}%",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }

            Text("Browser Customization", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Custom User Agent (Optional)", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    OutlinedTextField(
                        value = customUserAgent,
                        onValueChange = {
                            customUserAgent = it
                            settingsManager.setUserAgent(it)
                        },
                        placeholder = { Text("Leave blank to use default browser agent") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = TwitchPurple,
                            focusedLabelColor = TwitchPurple
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}
