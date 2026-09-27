package com.danielzuniga.player.remote

import android.net.Uri
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.OkHttpClient
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Pairs a browser from the link its QR carries (docs/monitor.md): joins the browser's temporary
 * room, hands it this phone's room and key sealed with the temporary key, and waits for the
 * browser to confirm.
 */
object RemotePairing {

    /**
     * The temporary room and key a pairing link carries, or null when it isn't one. The camera
     * opens the QR's link with them in the fragment; the site's "Abrir en Player" button sends
     * the same link with them as the extras [room] and [key].
     */
    fun parse(uri: Uri?, room: String? = null, key: String? = null): Pairing? {
        if (uri == null || uri.scheme != "https" || uri.host != HOST || uri.path?.trimEnd('/') != "/pair") return null
        val params = uri.fragment.orEmpty().split('&').mapNotNull {
            val (name, value) = it.split('=', limit = 2).takeIf { parts -> parts.size == 2 } ?: return@mapNotNull null
            name to value
        }.toMap()
        val r = params["r"] ?: room ?: return null
        val k = params["k"] ?: key ?: return null
        return Pairing(r, k).takeIf { RemoteCrypto.isRoom(r) && RemoteCrypto.isKey(k) }
    }

    /** True once the browser confirms it has this phone's room. */
    suspend fun pair(
        client: OkHttpClient,
        relayUrl: String,
        temporary: Pairing,
        phone: Pairing,
        name: String,
        timeoutMs: Long = 20_000,
    ): Boolean = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            lateinit var link: RemoteLink
            val welcome = JSONObject()
                .put("type", "welcome")
                .put("room", phone.room)
                .put("key", phone.key)
                .put("name", name)
            link = RemoteLink(client, relayUrl, temporary, object : RemoteLink.Listener {
                override fun onMessage(message: JSONObject) {
                    if (message.optString("type") == "paired" && continuation.isActive) {
                        link.stop()
                        continuation.resume(true)
                    }
                }

                // The browser is waiting in the room: hand it the phone's room. Sent again if it
                // reconnects before confirming.
                override fun onWebs(count: Int) {
                    if (count > 0) link.send(JSONObject(welcome.toString()))
                }
            })
            link.start()
            // Cancellation (the timeout) can come from any thread; the link lives on the main one.
            continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { link.stop() } }
        }
    } ?: false

    const val HOST = "player.danzuniga.xyz"
}
