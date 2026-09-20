package com.eetu.twitchapp.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import coil.compose.AsyncImage
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmoteSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { TwitchSettingsManager(context) }

    var enable7tv by remember { mutableStateOf(settingsManager.is7tvEnabled()) }
    var enableBttv by remember { mutableStateOf(settingsManager.isBttvEnabled()) }
    var enableFfz by remember { mutableStateOf(settingsManager.isFfzEnabled()) }

    val sampleEmotes = listOf(
        "KEKW" to "https://cdn.7tv.app/emote/60afb5d8e09f5db760920ef0/2x.webp",
        "catJAM" to "https://cdn.7tv.app/emote/60ae3fd40583b28b704cc03b/2x.webp",
        "Pog" to "https://cdn.7tv.app/emote/60ae3e620583b28b704cbf9b/2x.webp",
        "monkaW" to "https://cdn.7tv.app/emote/60ae3eb60583b28b704cbfbf/2x.webp",
        "Pepega" to "https://cdn.7tv.app/emote/60ae3f3a0583b28b704cbff9/2x.webp",
        "widepeppoHappy" to "https://cdn.7tv.app/emote/60ae3f860583b28b704cc01b/2x.webp",
        "COPIUM" to "https://cdn.7tv.app/emote/60ae40a40583b28b704cc090/2x.webp",
        "Sadge" to "https://cdn.7tv.app/emote/60ae3f700583b28b704cc012/2x.webp"
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Emote Settings", fontWeight = FontWeight.Bold) },
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
            // Emote Preview Showcase
            Card(
                colors = CardDefaults.cardColors(containerColor = TwitchDarkCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Preview of Third-Party Emotes",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "When chat messages mention these keywords, they render as rich animated images instead of plain text.",
                        color = TwitchTextDim,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(sampleEmotes) { (name, url) ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                AsyncImage(
                                    model = url,
                                    contentDescription = name,
                                    modifier = Modifier.size(44.dp)
                                )
                                Text(
                                    text = name,
                                    fontSize = 11.sp,
                                    color = Color.White,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Text("Emote Providers", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)) {
                Column {
                    EmoteToggleRow(
                        title = "7TV Emotes",
                        description = "Enable 7TV global and channel emotes (KEKW, catJAM, etc.)",
                        checked = enable7tv,
                        onCheckedChange = {
                            enable7tv = it
                            settingsManager.set7tvEnabled(it)
                        }
                    )
                    HorizontalDivider(color = TwitchDarkSurface)
                    EmoteToggleRow(
                        title = "BetterTTV (BTTV) Emotes",
                        description = "Enable BetterTTV global and channel emotes",
                        checked = enableBttv,
                        onCheckedChange = {
                            enableBttv = it
                            settingsManager.setBttvEnabled(it)
                        }
                    )
                    HorizontalDivider(color = TwitchDarkSurface)
                    EmoteToggleRow(
                        title = "FrankerFaceZ (FFZ) Emotes",
                        description = "Enable FrankerFaceZ room and global emotes",
                        checked = enableFfz,
                        onCheckedChange = {
                            enableFfz = it
                            settingsManager.setFfzEnabled(it)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EmoteToggleRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f).padding(end = 16.dp)) {
            Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(text = description, color = TwitchTextDim, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = TwitchPurple
            )
        )
    }
}
