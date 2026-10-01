package com.eetu.twitchapp.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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

    var oledMode by remember { mutableStateOf(settingsManager.isOledMode()) }
    var compactFeed by remember { mutableStateOf(settingsManager.isCompactFeed()) }
    var thumbnailSizeDp by remember { mutableIntStateOf(settingsManager.getThumbnailSizeDp()) }
    var desktopMode by remember { mutableStateOf(settingsManager.isDesktopMode()) }
    var chatOpacity by remember { mutableFloatStateOf(settingsManager.getChatOpacity()) }
    var customUserAgent by remember { mutableStateOf(settingsManager.getUserAgent()) }
    var isLowLatency by remember { mutableStateOf(settingsManager.isLowLatency()) }
    var lowLatencyBufferMs by remember { mutableIntStateOf(settingsManager.getLowLatencyBufferMs()) }
    var chatFontSizeSp by remember { mutableFloatStateOf(settingsManager.getChatFontSizeSp()) }

    val twitchColors = LocalTwitchColors.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Appearance & Playback", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = twitchColors.background)
            )
        },
        containerColor = twitchColors.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Feed & Thumbnail Appearance
            Text("Feed & Thumbnail Appearance", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = twitchColors.card)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Feed Card Layout", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)

                    // Layout Mode Selector: Horizontal [Image] Details vs Stacked [Image] / Details
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            onClick = {
                                compactFeed = true
                                settingsManager.setCompactFeed(true)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (compactFeed) TwitchPurple else Color.White.copy(alpha = 0.08f),
                            border = if (compactFeed) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Horizontal",
                                    color = if (compactFeed) Color.White else TwitchTextDim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "[ Image ] Details",
                                    color = if (compactFeed) Color.White.copy(alpha = 0.8f) else TwitchTextDim.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        Surface(
                            onClick = {
                                compactFeed = false
                                settingsManager.setCompactFeed(false)
                            },
                            shape = RoundedCornerShape(8.dp),
                            color = if (!compactFeed) TwitchPurple else Color.White.copy(alpha = 0.08f),
                            border = if (!compactFeed) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Column(
                                modifier = Modifier.padding(vertical = 10.dp, horizontal = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Stacked",
                                    color = if (!compactFeed) Color.White else TwitchTextDim,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = "[ Image ] / Details",
                                    color = if (!compactFeed) Color.White.copy(alpha = 0.8f) else TwitchTextDim.copy(alpha = 0.7f),
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // Thumbnail Size Slider
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Thumbnail Width",
                            color = Color.White,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = "${thumbnailSizeDp} dp",
                            color = TwitchPurple,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }

                    Slider(
                        value = thumbnailSizeDp.toFloat(),
                        onValueChange = {
                            thumbnailSizeDp = it.toInt()
                            settingsManager.setThumbnailSizeDp(it.toInt())
                        },
                        valueRange = 90f..180f,
                        steps = 17,
                        colors = SliderDefaults.colors(
                            thumbColor = TwitchPurple,
                            activeTrackColor = TwitchPurple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Interactive Live Preview Card
                    Text(
                        text = "Card Preview",
                        color = TwitchTextDim,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (compactFeed) {
                        // Horizontal Preview [ Image ] Details
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TwitchDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .width(thumbnailSizeDp.dp)
                                        .aspectRatio(16f / 9f)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E1E24)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = TwitchPurple,
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Surface(
                                        color = TwitchRed,
                                        shape = RoundedCornerShape(3.dp),
                                        modifier = Modifier
                                            .padding(3.dp)
                                            .align(Alignment.TopStart)
                                    ) {
                                        Text(
                                            text = "LIVE",
                                            color = Color.White,
                                            fontSize = 7.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                        )
                                    }
                                }

                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "StreamerName",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "Playing an awesome game! Ranked matches",
                                        color = Color.White.copy(alpha = 0.85f),
                                        fontSize = 11.sp,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Just Chatting • 14.5K viewers",
                                        color = TwitchTeal,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    } else {
                        // Stacked Preview [ Image ] / Details
                        Card(
                            colors = CardDefaults.cardColors(containerColor = TwitchDark),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(110.dp)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF1E1E24)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.PlayArrow,
                                        contentDescription = null,
                                        tint = TwitchPurple,
                                        modifier = Modifier.size(28.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "StreamerName • Playing an awesome game!",
                                    color = Color.White,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    maxLines = 1
                                )
                                Text(
                                    text = "Just Chatting • 14.5K viewers",
                                    color = TwitchTeal,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }

            // Player & Low Latency Buffer
            Text("Player & Low Latency Buffer", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = twitchColors.card)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Low Latency Mode", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Reduces delay to see chat reactions in real time", color = TwitchTextDim, fontSize = 12.sp)
                        }
                        Switch(
                            checked = isLowLatency,
                            onCheckedChange = {
                                isLowLatency = it
                                settingsManager.setLowLatency(it)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TwitchPurple
                            )
                        )
                    }

                    if (isLowLatency) {
                        HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                        val bufferSec = lowLatencyBufferMs / 1000f
                        val label = when {
                            bufferSec < 3.0f -> "Ultra Low Delay"
                            bufferSec < 4.5f -> "Balanced"
                            bufferSec < 6.0f -> "Safe (Anti-Freeze)"
                            else -> "Extra Safe"
                        }
                        val badgeColor = when {
                            bufferSec < 3.0f -> Color(0xFFFFB703)
                            bufferSec < 4.5f -> TwitchPurple
                            bufferSec < 6.0f -> TwitchTeal
                            else -> Color(0xFF48CAE4)
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Target Delay / Buffer", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)

                            Surface(
                                color = badgeColor.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, badgeColor.copy(alpha = 0.5f))
                            ) {
                                Text(
                                    text = "${String.format(java.util.Locale.US, "%.1f", bufferSec)}s • $label",
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
                            presets.forEach { (chipLabel, ms) ->
                                val isSelected = Math.abs(lowLatencyBufferMs - ms) < 250
                                Surface(
                                    onClick = {
                                        lowLatencyBufferMs = ms
                                        settingsManager.setLowLatencyBufferMs(ms)
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    color = if (isSelected) TwitchPurple else Color.White.copy(alpha = 0.08f),
                                    border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = chipLabel,
                                        color = if (isSelected) Color.White else TwitchTextDim,
                                        fontSize = 10.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        // Buffer Slider
                        var sliderVal by remember(lowLatencyBufferMs) { mutableFloatStateOf(lowLatencyBufferMs / 1000f) }
                        Slider(
                            value = sliderVal,
                            onValueChange = { sliderVal = it },
                            onValueChangeFinished = {
                                val ms = (sliderVal * 1000).toInt()
                                lowLatencyBufferMs = ms
                                settingsManager.setLowLatencyBufferMs(ms)
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
                            text = if (lowLatencyBufferMs < 3500) {
                                "Minimal delay (~1-2s). If you experience stream freezing on network fluctuations, choose Safe (5.0s)."
                            } else {
                                "Recommended Safe buffer (~2+ chunks ahead) protects against playback freezing while keeping delay low."
                            },
                            color = TwitchTextDim,
                            fontSize = 11.sp,
                            lineHeight = 14.sp
                        )
                    }
                }
            }

            // Display & Layout
            Text("Display & Layout", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = twitchColors.card)) {
                Column {
                    EmoteToggleRow(
                        title = "Full OLED Black Mode",
                        description = "Pure #000000 black background across the app for true blacks and maximum battery savings on OLED displays",
                        checked = oledMode,
                        onCheckedChange = {
                            oledMode = it
                            settingsManager.setOledMode(it)
                        }
                    )
                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
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

            // Chat Appearance & Floating Mode
            Text("Chat Font Size & Floating Mode", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)

            Card(colors = CardDefaults.cardColors(containerColor = twitchColors.card)) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Chat Font Size Header
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Chat Font Size", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("Applied to docked chat and floating overlay", color = TwitchTextDim, fontSize = 12.sp)
                        }
                        Surface(
                            color = TwitchPurple.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TwitchPurple.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${chatFontSizeSp.toInt()} sp",
                                color = TwitchPurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Font Size Preset Chips
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val fontPresets = listOf(
                            "Small (11)" to 11f,
                            "Normal (13)" to 13f,
                            "Large (15)" to 15f,
                            "Huge (18)" to 18f
                        )
                        fontPresets.forEach { (label, size) ->
                            val isSelected = Math.abs(chatFontSizeSp - size) < 0.5f
                            Surface(
                                onClick = {
                                    chatFontSizeSp = size
                                    settingsManager.setChatFontSizeSp(size)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) TwitchPurple else Color.White.copy(alpha = 0.08f),
                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else TwitchTextDim,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Font Size Slider
                    Slider(
                        value = chatFontSizeSp,
                        onValueChange = {
                            chatFontSizeSp = it
                            settingsManager.setChatFontSizeSp(it)
                        },
                        valueRange = 10f..22f,
                        steps = 11,
                        colors = SliderDefaults.colors(
                            thumbColor = TwitchPurple,
                            activeTrackColor = TwitchPurple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Live Chat Preview Box
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = TwitchDark,
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Preview",
                                color = TwitchTextDim,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Surface(
                                    color = TwitchPurple,
                                    shape = RoundedCornerShape(3.dp),
                                    modifier = Modifier.size((chatFontSizeSp * 1.2f).dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text("★", color = Color.White, fontSize = (chatFontSizeSp * 0.75f).sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                                Text(
                                    text = "Viewer123:",
                                    color = TwitchTeal,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = chatFontSizeSp.sp
                                )
                                Text(
                                    text = "PogChamp Loving this stream!",
                                    color = Color.White,
                                    fontSize = chatFontSizeSp.sp
                                )
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f))

                    // Floating Chat Transparency
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Floating Chat Transparency", color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            Text("0% is fully transparent text directly over video", color = TwitchTextDim, fontSize = 12.sp)
                        }
                        Surface(
                            color = TwitchPurple.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, TwitchPurple.copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "${(chatOpacity * 100).toInt()}%",
                                color = TwitchPurple,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Transparency Preset Chips (100%, 75%, 50%, 25%, 0%)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val transparencyPresets = listOf(
                            "100%" to 1.0f,
                            "75%" to 0.75f,
                            "50%" to 0.50f,
                            "25%" to 0.25f,
                            "0%" to 0.0f
                        )
                        transparencyPresets.forEach { (label, valOpacity) ->
                            val isSelected = Math.abs(chatOpacity - valOpacity) < 0.10f
                            Surface(
                                onClick = {
                                    chatOpacity = valOpacity
                                    settingsManager.setChatOpacity(valOpacity)
                                },
                                shape = RoundedCornerShape(8.dp),
                                color = if (isSelected) TwitchPurple else Color.White.copy(alpha = 0.08f),
                                border = if (isSelected) null else BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text(
                                    text = label,
                                    color = if (isSelected) Color.White else TwitchTextDim,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            }
                        }
                    }

                    // Transparency Slider (0% to 100%)
                    Slider(
                        value = chatOpacity,
                        onValueChange = {
                            chatOpacity = it
                            settingsManager.setChatOpacity(it)
                        },
                        valueRange = 0.0f..1.0f,
                        steps = 3,
                        colors = SliderDefaults.colors(
                            thumbColor = TwitchPurple,
                            activeTrackColor = TwitchPurple,
                            inactiveTrackColor = Color.White.copy(alpha = 0.15f)
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text(
                        text = if (chatOpacity <= 0.05f) {
                            "0% Transparent: pure chat text floating cleanly over live video with subtle drop shadows."
                        } else {
                            "Backdrop opacity at ${(chatOpacity * 100).toInt()}% for readability against bright streams."
                        },
                        color = TwitchTextDim,
                        fontSize = 11.sp,
                        lineHeight = 14.sp
                    )
                }
            }

            // Browser Customization
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
