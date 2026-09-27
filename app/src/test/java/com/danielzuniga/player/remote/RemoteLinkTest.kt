package com.danielzuniga.player.remote

import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteLinkTest {

    private val server = MockWebServer()
    private val pairing = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
    private val messages = mutableListOf<JSONObject>()
    private val webs = mutableListOf<Int>()
    private lateinit var link: RemoteLink

    @Before
    fun setUp() {
        server.start()
        link = RemoteLink(
            OkHttpClient(),
            server.url("/").toString().removeSuffix("/").replace("http", "ws"),
            pairing,
            object : RemoteLink.Listener {
                override fun onMessage(message: JSONObject) {
                    messages += message
                }

                override fun onWebs(count: Int) {
                    webs += count
                }
            },
        )
    }

    @After
    fun tearDown() {
        link.stop()
        server.shutdown()
    }

    /** Turns the main looper, advancing its clock, until [done] or 5 s of real time. */
    private fun awaitUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 5_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(5)
        }
    }

    private fun sealed(outbox: Outbox, message: JSONObject) =
        RemoteCrypto.seal(pairing.key, outbox.stamp(message).toString())

    @Test
    fun passesOnBrowserCommandsOnceAndIgnoresStrangers() {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val web = Outbox("web-1")
                val next = sealed(web, JSONObject().put("type", "cmd").put("op", "next"))
                webSocket.send("""{"relay":"peers","phone":true,"webs":2}""")
                webSocket.send(next)
                webSocket.send(next) // replayed
                webSocket.send(RemoteCrypto.seal(RemoteCrypto.newKey(), Outbox("x").stamp(JSONObject()).toString()))
                webSocket.send("garbage")
                webSocket.send(sealed(web, JSONObject().put("type", "cmd").put("op", "pause")))
            }
        }))

        link.start()
        awaitUntil { messages.size >= 2 }

        assertEquals(listOf("next", "pause"), messages.map { it.getString("op") })
        assertEquals(2, webs.first())
    }

    @Test
    fun reconnectsAfterTheRelayDropsIt() {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.close(1001, "going away")
            }
        }))
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(sealed(Outbox("web-1"), JSONObject().put("type", "cmd").put("op", "hello")))
            }
        }))

        link.start()
        awaitUntil { messages.isNotEmpty() }

        assertEquals("hello", messages.single().getString("op"))
        assertEquals(2, server.requestCount)
    }
}
