package com.eetu.twitchapp.ui.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.eetu.twitchapp.data.auth.TwitchAuthManager
import com.eetu.twitchapp.ui.auth.TwitchLoginDialog
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
    val context = LocalContext.current
    val authManager = remember { TwitchAuthManager.getInstance(context) }
    val currentUser by authManager.currentUser.collectAsState()
    var showLoginDialog by remember { mutableStateOf(false) }

    if (showLoginDialog) {
        TwitchLoginDialog(
            onDismiss = { showLoginDialog = false },
            onLoginSuccess = { showLoginDialog = false }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
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
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Twitch Account Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = TwitchDarkCard),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val user = currentUser
                    if (user != null) {
                        if (user.profileImageUrl.isNotEmpty()) {
                            AsyncImage(
                                model = ImageRequest.Builder(LocalContext.current)
                                    .data(user.profileImageUrl)
                                    .crossfade(true)
                                    .build(),
                                contentDescription = user.displayName,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .border(1.5.dp, TwitchPurple, CircleShape)
                            )
                        } else {
                            Icon(
                                Icons.Filled.AccountCircle,
                                contentDescription = null,
                                tint = TwitchPurple,
                                modifier = Modifier.size(46.dp)
                            )
                        }

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = user.displayName,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "@${user.login}",
                                color = TwitchTextDim,
                                fontSize = 13.sp
                            )
                        }

                        OutlinedButton(
                            onClick = { authManager.logout() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Log Out", fontSize = 12.sp)
                        }
                    } else {
                        Icon(
                            Icons.Filled.AccountCircle,
                            contentDescription = null,
                            tint = TwitchPurple,
                            modifier = Modifier.size(46.dp)
                        )

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Twitch Account",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Sign in to see followed channels",
                                color = TwitchTextDim,
                                fontSize = 13.sp
                            )
                        }

                        Button(
                            onClick = { showLoginDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = TwitchPurple),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("Log In", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

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
                title = "Appearance & Playback",
                subtitle = "Thumbnail size, feed layout, low latency buffer, chat opacity",
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
