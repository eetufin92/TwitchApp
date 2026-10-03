package com.eetu.twitchapp.ui.player

import android.net.Uri
import android.util.Log
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.ByteArrayOutputStream

/**
 * Factory for creating [TwitchAdDetectingDataSource] instances.
 */
class TwitchAdDetectingDataSourceFactory(
    private val upstreamFactory: DataSource.Factory,
    private val bufferMsProvider: () -> Long = { 4500L },
    private val tag: String = "TwitchAdDetecting",
    private val onAdDetected: (Boolean, String, Int) -> Unit
) : DataSource.Factory {
    override fun createDataSource(): DataSource {
        return TwitchAdDetectingDataSource(upstreamFactory.createDataSource(), bufferMsProvider, tag, onAdDetected)
    }
}

/**
 * DataSource interceptor that captures HLS playlist manifests (.m3u8), parses them
 * with [TwitchAdParser], and notifies [onAdDetected] of active SSAI ad breaks.
 */
class TwitchAdDetectingDataSource(
    private val upstream: DataSource,
    private val bufferMsProvider: () -> Long = { 4500L },
    private val tag: String = "TwitchAdDetecting",
    private val onAdDetected: (Boolean, String, Int) -> Unit
) : DataSource {
    private var isPlaylist = false
    private val playlistBuffer = ByteArrayOutputStream()

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    override fun open(dataSpec: DataSpec): Long {
        val uriStr = dataSpec.uri.toString()
        isPlaylist = uriStr.contains(".m3u8") || uriStr.contains("playlist")
        if (isPlaylist) {
            playlistBuffer.reset()
        }
        return upstream.open(dataSpec)
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(buffer, offset, length)
        if (isPlaylist && bytesRead > 0) {
            playlistBuffer.write(buffer, offset, bytesRead)
        }
        return bytesRead
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    override fun close() {
        if (isPlaylist && playlistBuffer.size() > 0) {
            try {
                val content = playlistBuffer.toString("UTF-8")
                val bufferMs = bufferMsProvider()
                val result = TwitchAdParser.parse(content, bufferMs)
                Log.i(tag, "Ad detection: isAdActive=${result.isAdActive}, adId=${result.adId}, dur=${result.durationSeconds}, hasAdSeg=${result.hasAdSegment}, bufferMs=$bufferMs")
                onAdDetected(result.isAdActive, result.adId, result.durationSeconds)
            } catch (e: Exception) {
                Log.e(tag, "Error parsing playlist for ads", e)
            }
        }
        upstream.close()
    }
}
