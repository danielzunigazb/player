package com.danielzuniga.player.remote

import android.os.Handler
import android.os.Looper
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONException
import org.json.JSONObject

/**
 * The phone's connection to its room on the relay: seals what it sends, opens and checks what
 * arrives, and reconnects by itself (1 s, 2 s, 4 s… up to 30 s) until [stop]. Callbacks run on
 * the main thread, where the player lives.
 */
class RemoteLink(
    private val client: OkHttpClient,
    private val relayUrl: String,
    private val pairing: Pairing,
    private val listener: Listener,
) {
    interface Listener {
        /** A message from a browser that opened with the room's key and isn't a replay. */
        fun onMessage(message: JSONObject)

        /** How many browsers are in the room, whenever someone comes or goes. */
        fun onWebs(count: Int) {}
    }

    private val main = Handler(Looper.getMainLooper())
    private val outbox = Outbox(PHONE)
    private val inbox = Inbox()
    private var socket: WebSocket? = null
    private var stopped = false
    private var attempt = 0
    private val reconnect = Runnable { connect() }

    fun start() {
        stopped = false
        connect()
    }

    /** Sends [message] sealed; dropped when not connected (the next state catches up). */
    fun send(message: JSONObject): Boolean {
        val socket = socket ?: return false
        val sealed = RemoteCrypto.seal(pairing.key, outbox.stamp(message).toString())
        return socket.send(sealed)
    }

    fun stop() {
        stopped = true
        main.removeCallbacks(reconnect)
        socket?.close(1000, null)
        socket = null
    }

    private fun connect() {
        if (stopped) return
        val request = Request.Builder().url("$relayUrl/room/${pairing.room}?role=phone").build()
        client.newWebSocket(request, Callbacks())
    }

    private fun retryLater() {
        if (stopped) return
        val delayMs = (1000L shl attempt.coerceAtMost(5)).coerceAtMost(MAX_BACKOFF_MS)
        attempt++
        main.removeCallbacks(reconnect)
        main.postDelayed(reconnect, delayMs)
    }

    private fun handle(text: String) {
        if (text.startsWith("{")) {
            // The relay's own presence note, in the clear.
            val note = try {
                JSONObject(text)
            } catch (e: JSONException) {
                return
            }
            if (note.optString("relay") == "peers") listener.onWebs(note.optInt("webs"))
            return
        }
        val plain = RemoteCrypto.open(pairing.key, text) ?: return
        val message = try {
            JSONObject(plain)
        } catch (e: JSONException) {
            return
        }
        if (inbox.accept(message)) listener.onMessage(message)
    }

    private inner class Callbacks : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            main.post {
                if (stopped) {
                    webSocket.close(1000, null)
                } else {
                    socket = webSocket
                    attempt = 0
                }
            }
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            main.post { if (socket === webSocket) handle(text) }
        }

        override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
            webSocket.close(1000, null)
            main.post { lost(webSocket) }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            main.post { lost(webSocket) }
        }

        private fun lost(webSocket: WebSocket) {
            if (socket === webSocket) {
                socket = null
                listener.onWebs(0)
            }
            // A socket that never opened also lands here: try again later either way.
            if (socket == null) retryLater()
        }
    }

    companion object {
        const val PHONE = "phone"
        private const val MAX_BACKOFF_MS = 30_000L
    }
}
