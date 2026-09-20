package com.eetu.twitchapp.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.eetu.twitchapp.data.AdBlockManager
import com.eetu.twitchapp.ui.theme.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdBlockSettingsScreen(
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val adBlockManager = remember { AdBlockManager.getInstance(context) }
    val scope = rememberCoroutineScope()

    var isEnabled by remember { mutableStateOf(adBlockManager.isAdBlockEnabled()) }
    var isAutoUpdate by remember { mutableStateOf(adBlockManager.isAutoUpdateOnLaunch()) }
    var subscriptions by remember { mutableStateOf(adBlockManager.getSubscriptions()) }
    var isUpdating by remember { mutableStateOf(false) }
    var updateStatusMessage by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("uBlock Origin AdBlock", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            scope.launch {
                                isUpdating = true
                                updateStatusMessage = "Updating uBlock lists..."
                                val res = adBlockManager.updateAllFilters()
                                isUpdating = false
                                updateStatusMessage = if (res.isSuccess) "Filters updated successfully!" else "Failed to update filters"
                                subscriptions = adBlockManager.getSubscriptions()
                            }
                        },
                        enabled = !isUpdating
                    ) {
                        if (isUpdating) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = TwitchTeal, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, contentDescription = "Update Now", tint = TwitchTeal)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = TwitchDark)
            )
        },
        containerColor = TwitchDark
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                // Info Banner
                Card(colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Network & Cosmetic Filtering",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Blocks network trackers, telemetry, and banner ads using official uBlock Origin and EasyList subscriptions.",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                        updateStatusMessage?.let { msg ->
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(text = msg, color = TwitchTeal, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }

            item {
                Card(colors = CardDefaults.cardColors(containerColor = TwitchDarkCard)) {
                    Column {
                        EmoteToggleRow(
                            title = "Enable AdBlocker",
                            description = "Block banner ads, trackers, and malicious scripts",
                            checked = isEnabled,
                            onCheckedChange = {
                                isEnabled = it
                                adBlockManager.setAdBlockEnabled(it)
                            }
                        )
                        HorizontalDivider(color = TwitchDarkSurface)
                        EmoteToggleRow(
                            title = "Auto-Update Lists",
                            description = "Periodically fetch updated rules in the background",
                            checked = isAutoUpdate,
                            onCheckedChange = {
                                isAutoUpdate = it
                                adBlockManager.setAutoUpdateOnLaunch(it)
                            }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("uBlock Filter Subscriptions", color = TwitchPurple, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Last update: ${adBlockManager.getLastUpdateTime()}", color = TwitchTextDim, fontSize = 11.sp)
                }
            }

            items(subscriptions, key = { it.id }) { sub ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = TwitchDarkCard),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                            Text(text = sub.name, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = if (sub.ruleCount > 0) "${sub.ruleCount} active rules" else "Official uBlock list",
                                color = TwitchTextDim,
                                fontSize = 12.sp
                            )
                        }
                        Switch(
                            checked = sub.isEnabled,
                            onCheckedChange = { checked ->
                                adBlockManager.toggleSubscription(sub.id, checked)
                                subscriptions = adBlockManager.getSubscriptions()
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.White,
                                checkedTrackColor = TwitchPurple
                            )
                        )
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
