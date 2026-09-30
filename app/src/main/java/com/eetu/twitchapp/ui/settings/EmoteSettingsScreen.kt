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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.data.EmoteRepository
import com.eetu.twitchapp.data.TwitchSettingsManager
import com.eetu.twitchapp.data.model.EmoteItem
import com.eetu.twitchapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EmoteSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val settingsManager = remember { TwitchSettingsManager(context) }
    val emoteRepo = remember { EmoteRepository(context) }

    var enable7tv by remember { mutableStateOf(settingsManager.is7tvEnabled()) }
    var enableBttv by remember { mutableStateOf(settingsManager.isBttvEnabled()) }
    var enableFfz by remember { mutableStateOf(settingsManager.isFfzEnabled()) }

    val fallbackEmotes = remember {
        listOf(
            EmoteItem("Kappa", "https://static-cdn.jtvnw.net/emoticons/v2/25/default/dark/2.0", source = "Twitch"),
            EmoteItem("PogChamp", "https://static-cdn.jtvnw.net/emoticons/v2/305954156/default/dark/2.0", source = "Twitch"),
            EmoteItem("LUL", "https://static-cdn.jtvnw.net/emoticons/v2/425618/default/dark/2.0", source = "Twitch"),
            EmoteItem("RainTime", "https://cdn.7tv.app/emote/01FCY771D800007PQ2DF3GDTN6/2x.webp", source = "7TV"),
            EmoteItem("Clap", "https://cdn.7tv.app/emote/01GAM8EFQ00004MXFXAJYKA859/2x.webp", source = "7TV"),
            EmoteItem("peepoHappy", "https://cdn.7tv.app/emote/01GAZ199Z8000FEWHS6AT5QZV0/2x.webp", source = "7TV"),
            EmoteItem(":tf:", "https://cdn.betterttv.net/emote/54fa8f1401e468494b85b537/2x", source = "BTTV"),
            EmoteItem("CiGrip", "https://cdn.betterttv.net/emote/54fa8fce01e468494b85b53c/2x", source = "BTTV"),
            EmoteItem("CatBag", "https://cdn.frankerfacez.com/emote/25927/2", source = "FFZ")
        )
    }

    var dynamicEmotes by remember { mutableStateOf<List<EmoteItem>>(emptyList()) }

    LaunchedEffect(Unit) {
        try {
            val list = emoteRepo.getStructuredChannelEmotes("")
            if (list.isNotEmpty()) {
                dynamicEmotes = list
            }
        } catch (_: Exception) { }
    }

    val displayEmotes = remember(dynamicEmotes, enable7tv, enableBttv, enableFfz) {
        val baseList = dynamicEmotes.ifEmpty { fallbackEmotes }
        baseList.filter { item ->
            when (item.source) {
                "7TV" -> enable7tv
                "BTTV" -> enableBttv
                "FFZ" -> enableFfz
                else -> true
            }
        }
    }

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
                    if (displayEmotes.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "All third-party emote providers are disabled.",
                                color = TwitchTextDim,
                                fontSize = 12.sp
                            )
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            items(displayEmotes, key = { "${it.source}_${it.name}_${it.url}" }) { item ->
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(48.dp)
                                            .background(TwitchDark, RoundedCornerShape(8.dp)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        AsyncImage(
                                            model = ImageRequest.Builder(LocalContext.current)
                                                .data(item.url)
                                                .crossfade(true)
                                                .build(),
                                            contentDescription = item.name,
                                            contentScale = ContentScale.Fit,
                                            modifier = Modifier.size(36.dp)
                                        )
                                    }
                                    Text(
                                        text = item.name,
                                        fontSize = 11.sp,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Surface(
                                        color = when (item.source) {
                                            "7TV" -> TwitchTeal.copy(alpha = 0.2f)
                                            "BTTV" -> Color(0xFFFF5252).copy(alpha = 0.2f)
                                            "FFZ" -> Color(0xFFFFB300).copy(alpha = 0.2f)
                                            else -> TwitchPurple.copy(alpha = 0.2f)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = item.source,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (item.source) {
                                                "7TV" -> TwitchTeal
                                                "BTTV" -> Color(0xFFFF5252)
                                                "FFZ" -> Color(0xFFFFB300)
                                                else -> TwitchPurple
                                            },
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                                        )
                                    }
                                }
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
