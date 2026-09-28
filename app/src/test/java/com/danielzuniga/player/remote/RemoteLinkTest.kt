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
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

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

    /** A browser's command, addressed to [to] (the link under test by default). */
    private fun cmd(op: String, to: String? = link.id) =
        JSONObject().put("type", "cmd").put("op", op).apply { if (to != null) put("to", to) }

    @Test
    fun passesOnBrowserCommandsOnceAndIgnoresStrangers() {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val web = Outbox("web-1")
                val next = sealed(web, cmd("next"))
                webSocket.send("""{"relay":"peers","phone":true,"webs":2}""")
                webSocket.send(next)
                webSocket.send(next) // replayed
                webSocket.send(RemoteCrypto.seal(RemoteCrypto.newKey(), Outbox("x").stamp(JSONObject()).toString()))
                webSocket.send("garbage")
                webSocket.send(sealed(web, cmd("pause")))
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

    @Test
    fun ignoresCommandsAddressedToAnEarlierLinkOfThePhone() {
        // The phone before a restart: same room and key, another link.
        val earlier = RemoteLink(OkHttpClient(), "ws://unused", pairing, object : RemoteLink.Listener {
            override fun onMessage(message: JSONObject) = Unit
        })
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                val web = Outbox("web-1")
                webSocket.send(sealed(web, cmd("next", to = earlier.id))) // replayed from before
                webSocket.send(sealed(web, cmd("pause", to = null)))
                webSocket.send(sealed(web, cmd("hello", to = null)))
                webSocket.send(sealed(web, cmd("previous")))
            }
        }))

        link.start()
        awaitUntil { messages.size >= 2 }

        assertEquals(listOf("hello", "previous"), messages.map { it.getString("op") })
    }

    @Test
    fun sendsUnderAnIdOfItsOwnSoARestartedPhoneIsHeard() {
        val received = LinkedBlockingQueue<JSONObject>()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                received += JSONObject(RemoteCrypto.open(pairing.key, text)!!)
            }
        }))
        val another = RemoteLink(OkHttpClient(), "ws://unused", pairing, object : RemoteLink.Listener {
            override fun onMessage(message: JSONObject) = Unit
        })

        link.start()
        awaitUntil { link.send(JSONObject().put("type", "state")) }

        val sent = received.poll(5, TimeUnit.SECONDS)!!
        assertEquals(link.id, sent.getString("from"))
        assertEquals(1L, sent.getLong("seq"))
        assertTrue(link.id.matches(Regex("phone-[A-Za-z0-9_-]{8}")))
        assertNotEquals(link.id, another.id)
    }
}
