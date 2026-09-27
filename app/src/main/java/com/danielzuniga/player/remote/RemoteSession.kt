package com.danielzuniga.player.remote

import android.content.Context
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Base64
import androidx.core.graphics.scale
import androidx.media3.common.Player
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.widget.NowPlayingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import org.json.JSONObject
import java.io.ByteArrayOutputStream

/**
 * The web monitor while the playback service runs: connected to the phone's room whenever the
 * monitor is on and a browser was ever paired, sending the state and cover only while some
 * browser is watching, and applying the commands that come back.
 */
class RemoteSession(
    private val context: Context,
    private val player: Player,
    private val store: RemoteStore,
    private val client: OkHttpClient,
    private val relayUrl: String,
    library: () -> LibraryIndex,
    private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private val audio = context.getSystemService(AudioManager::class.java)
    private val control = RemoteControl(player, library, object : RemoteControl.Volume {
        override fun get(): Float {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            return audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
        }

        override fun set(value: Float) {
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(value * max), 0)
        }
    })

    private var link: RemoteLink? = null
    private var watchers = 0
    private var sentArtFor: String? = null
    private var artJob: Job? = null
    private var job: Job? = null

    private val pushState = Runnable {
        link?.takeIf { watchers > 0 }?.send(control.state())
        sendArtIfChanged()
    }
    private val heartbeat = object : Runnable {
        override fun run() {
            if (player.isPlaying) changed()
            main.postDelayed(this, HEARTBEAT_MS)
        }
    }

    fun start() {
        job = scope.launch {
            combine(store.enabled, store.pairing) { enabled, pairing -> pairing.takeIf { enabled } }
                .collect { pairing -> connect(pairing) }
        }
        main.postDelayed(heartbeat, HEARTBEAT_MS)
    }

    /** The player changed: browsers get the new state, coalesced so a burst sends one. */
    fun changed() {
        if (link == null || watchers == 0) return
        main.removeCallbacks(pushState)
        main.postDelayed(pushState, COALESCE_MS)
    }

    fun stop() {
        job?.cancel()
        main.removeCallbacks(pushState)
        main.removeCallbacks(heartbeat)
        connect(null)
    }

    private fun connect(pairing: Pairing?) {
        link?.stop()
        link = null
        watchers = 0
        if (pairing == null) return
        link = RemoteLink(client, relayUrl, pairing, object : RemoteLink.Listener {
            override fun onMessage(message: JSONObject) {
                control.handle(message)?.let { link?.send(it) }
                if (message.optString("op") == "hello") sentArtFor = null
                changed()
            }

            override fun onWebs(count: Int) {
                val joined = count > watchers
                watchers = count
                if (joined) {
                    // A browser that just arrived gets everything now.
                    sentArtFor = null
                    changed()
                }
            }
        }).also { it.start() }
    }

    private fun sendArtIfChanged() {
        val item = player.currentMediaItem ?: return
        if (item.mediaId == sentArtFor || watchers == 0) return
        sentArtFor = item.mediaId
        val songId = item.mediaId
        val uri = item.mediaMetadata.artworkUri
        artJob?.cancel()
        artJob = scope.launch {
            val jpeg = withContext(Dispatchers.IO) { artJpeg(uri) }
            link?.send(
                JSONObject()
                    .put("type", "art")
                    .put("songId", songId)
                    .put("jpeg", jpeg ?: JSONObject.NULL)
            )
        }
    }

    /** The cover as a small base64 JPEG, well under the relay's 64 KB per message. */
    private fun artJpeg(uri: Uri?): String? {
        val bitmap = NowPlayingWidget.loadArtwork(context, uri) ?: return null
        // Center square, like the app's covers, then down to ART_PX.
        val crop = minOf(bitmap.width, bitmap.height)
        val square = Bitmap.createBitmap(bitmap, (bitmap.width - crop) / 2, (bitmap.height - crop) / 2, crop, crop)
        val scaled = if (crop > ART_PX) square.scale(ART_PX, ART_PX) else square
        val bytes = ByteArrayOutputStream().use {
            scaled.compress(Bitmap.CompressFormat.JPEG, 80, it)
            it.toByteArray()
        }
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private companion object {
        const val HEARTBEAT_MS = 30_000L
        const val COALESCE_MS = 150L
        const val ART_PX = 300
    }
}
