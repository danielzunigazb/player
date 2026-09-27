package com.danielzuniga.player.remote

import okhttp3.WebSocket
import okhttp3.WebSocketListener

/** The relay's end of a test WebSocket: answers a close like the real one, so shutdown is quick. */
open class RelaySocket : WebSocketListener() {
    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        webSocket.close(1000, null)
    }
}
