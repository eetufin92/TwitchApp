package com.eetu.twitchapp.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eetu.twitchapp.ui.theme.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: () -> Unit,
    onNavigateToEmotes: () -> Unit,
    onNavigateToAds: () -> Unit,
    onNavigateToAdBlock: () -> Unit,
    onNavigateToAppearance: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
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
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SettingsCategoryItem(
                title = "Emote Settings",
                subtitle = "Configure 7TV, BetterTTV, and FrankerFaceZ emotes",
                icon = Icons.Filled.Mood,
                iconTint = TwitchPurple,
                onClick = onNavigateToEmotes
            )

            SettingsCategoryItem(
                title = "SSAI Ad Protection",
                subtitle = "Automatic ad muting and 'Ad in progress' overlay",
                icon = Icons.Filled.Shield,
                iconTint = TwitchTeal,
                onClick = onNavigateToAds
            )

            SettingsCategoryItem(
                title = "uBlock Origin AdBlocker",
                subtitle = "Network filtering, tracker blocking, and cosmetic rules",
                icon = Icons.Filled.Security,
                iconTint = Color(0xFF00B4D8),
                onClick = onNavigateToAdBlock
            )

            SettingsCategoryItem(
                title = "Chat & Appearance",
                subtitle = "Floating chat defaults, desktop/tablet mode, user agent",
                icon = Icons.Filled.Palette,
                iconTint = Color(0xFFFFB703),
                onClick = onNavigateToAppearance
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "TwitchApp v1.0 • Built with Jetpack Compose",
                color = TwitchTextDim,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )
        }
    }
}

@Composable
fun SettingsCategoryItem(
    title: String,
    subtitle: String,
    icon: ImageVector,
    iconTint: Color,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(28.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(2.dp))
                Text(text = subtitle, color = TwitchTextDim, fontSize = 13.sp)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = TwitchTextDim,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
