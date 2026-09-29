package com.eetu.twitchapp.ui.auth

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.eetu.twitchapp.data.auth.TwitchAuthManager
import com.eetu.twitchapp.data.model.TwitchUser
import com.eetu.twitchapp.data.network.TwitchGqlClient
import com.eetu.twitchapp.ui.theme.TwitchDark
import com.eetu.twitchapp.ui.theme.TwitchDarkCard
import com.eetu.twitchapp.ui.theme.TwitchPurple
import com.eetu.twitchapp.ui.theme.TwitchTextDim
import kotlinx.coroutines.launch

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun TwitchLoginDialog(
    onDismiss: () -> Unit,
    onLoginSuccess: (TwitchUser) -> Unit
) {
    val context = LocalContext.current
    val authManager = remember { TwitchAuthManager.getInstance(context) }
    val gqlClient = remember { TwitchGqlClient() }
    val coroutineScope = rememberCoroutineScope()

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageProgress by remember { mutableIntStateOf(0) }
    var isAuthenticating by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var capturedToken by remember { mutableStateOf<String?>(null) }

    fun checkAndAuthenticate(url: String?) {
        if (capturedToken != null || isAuthenticating) return

        val cookieManager = CookieManager.getInstance()
        val token = findAuthToken(cookieManager, url) ?: return

        capturedToken = token
        isAuthenticating = true
        errorMessage = null

        coroutineScope.launch {
            try {
                val user = gqlClient.validateAuthToken(token)
                if (user != null) {
                    authManager.saveSession(token, user)
                    onLoginSuccess(user)
                    onDismiss()
                } else {
                    errorMessage = "Could not verify session. Please try again."
                    isAuthenticating = false
                    capturedToken = null
                }
            } catch (e: Exception) {
                errorMessage = "Authentication error: ${e.localizedMessage ?: "Unknown error"}"
                isAuthenticating = false
                capturedToken = null
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 24.dp)
                .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
            color = TwitchDark
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(TwitchDarkCard)
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "Sign in with Twitch",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Log in securely to view followed channels",
                            color = TwitchTextDim,
                            fontSize = 12.sp
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { webViewInstance?.reload() }
                        ) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = "Reload",
                                tint = Color.White
                            )
                        }
                        IconButton(
                            onClick = onDismiss
                        ) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Close",
                                tint = Color.White
                            )
                        }
                    }
                }

                // Progress indicator
                if (pageProgress in 1..99) {
                    LinearProgressIndicator(
                        progress = { pageProgress / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = TwitchPurple,
                        trackColor = Color.Transparent
                    )
                }

                // Error / Info Banner
                if (errorMessage != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.errorContainer,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }

                // Authenticating overlay or Webview
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                webViewInstance = this

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                settings.apply {
                                    javaScriptEnabled = true
                                    domStorageEnabled = true
                                    useWideViewPort = true
                                    loadWithOverviewMode = true
                                    setSupportZoom(true)
                                    builtInZoomControls = true
                                    displayZoomControls = false
                                }

                                webChromeClient = object : WebChromeClient() {
                                    override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                        pageProgress = newProgress
                                    }
                                }

                                webViewClient = object : WebViewClient() {
                                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                        checkAndAuthenticate(url)
                                    }

                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        checkAndAuthenticate(url)
                                    }

                                    override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                                        checkAndAuthenticate(url)
                                    }

                                    override fun onLoadResource(view: WebView?, url: String?) {
                                        checkAndAuthenticate(url)
                                    }

                                    override fun shouldOverrideUrlLoading(
                                        view: WebView?,
                                        request: WebResourceRequest?
                                    ): Boolean {
                                        checkAndAuthenticate(request?.url?.toString())
                                        return false
                                    }
                                }

                                loadUrl("https://www.twitch.tv/login")
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    if (isAuthenticating) {
                        Surface(
                            modifier = Modifier.fillMaxSize(),
                            color = TwitchDark.copy(alpha = 0.92f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize(),
                                verticalArrangement = Arrangement.Center,
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator(color = TwitchPurple)
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "Signing you in...",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Verifying Twitch credentials",
                                    color = TwitchTextDim,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun findAuthToken(cookieManager: CookieManager, currentUrl: String?): String? {
    val urlsToProbe = listOfNotNull(
        "https://www.twitch.tv",
        "https://twitch.tv",
        "https://passport.twitch.tv",
        "https://id.twitch.tv",
        currentUrl
    )
    for (url in urlsToProbe) {
        try {
            val cookieStr = cookieManager.getCookie(url)
            val token = extractTokenFromCookieString(cookieStr)
            if (token != null) return token
        } catch (_: Exception) {
        }
    }
    return null
}

private fun extractTokenFromCookieString(cookieStr: String?): String? {
    if (cookieStr.isNullOrBlank()) return null
    val items = cookieStr.split(";")
    for (item in items) {
        val parts = item.trim().split("=", limit = 2)
        if (parts.size == 2 && parts[0] == "auth-token") {
            val value = parts[1].trim()
            if (value.isNotEmpty() && value != "\"\"" && value != "null") {
                return value.removeSurrounding("\"")
            }
        }
    }
    return null
}
