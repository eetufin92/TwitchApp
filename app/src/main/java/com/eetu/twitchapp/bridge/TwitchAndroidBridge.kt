package com.eetu.twitchapp.bridge

import android.content.Context
import android.content.Intent
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import com.eetu.twitchapp.data.EmoteRepository
import com.eetu.twitchapp.data.TwitchSettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.json.JSONObject

class TwitchAndroidBridge(
    private val context: Context,
    private val webView: WebView,
    private val onAdStatusChanged: (Boolean, String) -> Unit = { _, _ -> },
    private val onVideoDimensionsChanged: (Int, Int) -> Unit = { _, _ -> },
    private val onMetadataChanged: (String, String) -> Unit = { _, _ -> },
    private val onPlaybackStateChanged: (Boolean) -> Unit = {},
    private val onChannelChanged: (String) -> Unit = {}
) {
    private val scope = CoroutineScope(Dispatchers.Main + Job())
    private val emoteRepository = EmoteRepository()
    private val settingsManager = TwitchSettingsManager(context)

    private var currentChannel: String = ""
    private var lastWidth = 0
    private var lastHeight = 0

    @JavascriptInterface
    fun log(message: String) {
        Log.d("TwitchAndroidBridge", "JS: $message")
    }

    @JavascriptInterface
    fun onAdStatusChanged(isAd: Boolean, countdownText: String) {
        Log.d("TwitchAndroidBridge", "AdStatus: isAd=$isAd, countdown=$countdownText")
        webView.post {
            onAdStatusChanged(isAd, countdownText)
        }
    }

    @JavascriptInterface
    fun onVideoDimensionsChanged(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        if (width == lastWidth && height == lastHeight) return

        lastWidth = width
        lastHeight = height

        Log.d("TwitchAndroidBridge", "VideoDimensions: ${width}x${height}")
        webView.post {
            onVideoDimensionsChanged(width, height)
        }
    }

    @JavascriptInterface
    fun updateMetadata(title: String, streamer: String) {
        webView.post {
            onMetadataChanged(title, streamer)
        }
    }

    @JavascriptInterface
    fun updatePlaybackState(isPlaying: Boolean) {
        webView.post {
            onPlaybackStateChanged(isPlaying)
        }
    }

    @JavascriptInterface
    fun onChannelDetected(channelName: String) {
        val cleanName = channelName.lowercase().trim()
        if (cleanName.isEmpty() || cleanName == currentChannel) return
        currentChannel = cleanName

        Log.d("TwitchAndroidBridge", "ChannelDetected: $cleanName")
        webView.post {
            onChannelChanged(cleanName)
        }

        // Fetch channel emotes asynchronously and inject back
        scope.launch {
            val emotes = emoteRepository.getChannelEmotes(
                cleanName,
                enable7tv = settingsManager.is7tvEnabled(),
                enableBttv = settingsManager.isBttvEnabled(),
                enableFfz = settingsManager.isFfzEnabled()
            )

            if (emotes.isNotEmpty()) {
                val json = JSONObject(emotes as Map<*, *>).toString()
                webView.post {
                    webView.evaluateJavascript("if(window.addEmotes) { window.addEmotes($json); }", null)
                }
            }
        }
    }

    @JavascriptInterface
    fun share(url: String) {
        val sendIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, url)
            type = "text/plain"
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(sendIntent, "Share Stream")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }
}
