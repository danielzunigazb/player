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
 * room, asks it for the code the phone shows, and only when the person has typed it right hands
 * the browser this phone's room and key, sealed with the temporary key, and waits for it to
 * confirm. Whoever crafted the link never sees the phone's screen, so they can't pass.
 */
object RemotePairing {

    /** How a pairing ended, when nobody cancelled it. */
    enum class Result {
        PAIRED,

        /** [MAX_TRIES] wrong codes: the browser was told and the phone left its room. */
        WRONG_CODE,

        /** Nobody typed the code, or the browser never confirmed, in time. */
        TIMED_OUT,
    }

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

    /**
     * Pairs the browser waiting in [temporary] once it sends [code]. [phone] gives this phone's
     * room, asked for only after the right code. Cancelling leaves the room without another word.
     */
    suspend fun pair(
        client: OkHttpClient,
        relayUrl: String,
        temporary: Pairing,
        code: String,
        phone: () -> Pairing,
        name: String,
        timeoutMs: Long = TIMEOUT_MS,
    ): Result = withTimeoutOrNull(timeoutMs) {
        suspendCancellableCoroutine { continuation ->
            lateinit var link: RemoteLink
            var webs = 0
            var wrong = 0
            // Set once the code is right; nothing about the phone's room goes out before.
            var welcome: JSONObject? = null

            fun finish(result: Result) {
                link.stop()
                if (continuation.isActive) continuation.resume(result)
            }

            link = RemoteLink(client, relayUrl, temporary, object : RemoteLink.Listener {
                override fun onMessage(message: JSONObject) {
                    if (!continuation.isActive) return
                    when (message.optString("type")) {
                        "code" -> {
                            if (welcome != null) return
                            if (RemoteCrypto.codeMatches(code, message.optString("code"))) {
                                val room = phone()
                                welcome = JSONObject()
                                    .put("type", "welcome")
                                    .put("room", room.room)
                                    .put("key", room.key)
                                    .put("name", name)
                                link.send(JSONObject(welcome.toString()))
                            } else if (++wrong >= MAX_TRIES) {
                                link.send(JSONObject().put("type", "pairFailed"))
                                finish(Result.WRONG_CODE)
                            } else {
                                link.send(JSONObject().put("type", "wrongCode").put("attemptsLeft", MAX_TRIES - wrong))
                            }
                        }
                        "paired" -> if (welcome != null) finish(Result.PAIRED)
                    }
                }

                // The browser is in the room: ask for the code, or hand the room again if it
                // reconnected after the right one.
                override fun onWebs(count: Int) {
                    val arrived = count > 0 && webs == 0
                    webs = count
                    if (!arrived) return
                    link.send(welcome?.let { JSONObject(it.toString()) } ?: JSONObject().put("type", "askCode"))
                }
            })
            link.start()
            // Cancellation (Cancel, the timeout) can come from any thread; the link lives on the main one.
            continuation.invokeOnCancellation { Handler(Looper.getMainLooper()).post { link.stop() } }
        }
    } ?: Result.TIMED_OUT

    const val HOST = "player.danzuniga.xyz"
    const val MAX_TRIES = 3
    const val TIMEOUT_MS = 2 * 60 * 1000L
}
