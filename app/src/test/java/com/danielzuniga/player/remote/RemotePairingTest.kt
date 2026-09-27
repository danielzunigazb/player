package com.danielzuniga.player.remote

import android.net.Uri
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/** Pairs against a stand-in for the relay with a browser in the room, over real WebSockets. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemotePairingTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val temporary = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
    private val phone = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
    private val received = LinkedBlockingQueue<String>()

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        // Runs the link's stop that a timed-out pairing posted to the main thread.
        shadowOf(Looper.getMainLooper()).idle()
        server.shutdown()
    }

    private val relayUrl get() = server.url("/").toString().removeSuffix("/").replace("http", "ws")

    /** A browser in the room: says it's there, and answers a welcome with [reply] sealed. */
    private fun browser(key: String, reply: ((JSONObject) -> JSONObject?)?) {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            val outbox = Outbox("web-1")

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"relay":"peers","phone":true,"webs":1}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val plain = RemoteCrypto.open(key, text) ?: return
                received += plain
                val answer = reply?.invoke(JSONObject(plain)) ?: return
                webSocket.send(RemoteCrypto.seal(key, outbox.stamp(answer).toString()))
            }
        }))
    }

    private fun <T> onMainLooper(block: suspend () -> T): T {
        val result = CoroutineScope(Dispatchers.IO).async { block() }
        while (!result.isCompleted) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
        return runBlocking { result.await() }
    }

    @Test
    fun handsThePhoneRoomToTheBrowserSealedWithTheTemporaryKey() {
        browser(temporary.key) { JSONObject().put("type", "paired") }

        val paired = onMainLooper { RemotePairing.pair(client, relayUrl, temporary, phone, "Pixel") }

        assertTrue(paired)
        assertEquals("/room/${temporary.room}?role=phone", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
        val welcome = JSONObject(received.poll(1, TimeUnit.SECONDS)!!)
        assertEquals("welcome", welcome.getString("type"))
        assertEquals(phone.room, welcome.getString("room"))
        assertEquals(phone.key, welcome.getString("key"))
        assertEquals("Pixel", welcome.getString("name"))
    }

    @Test
    fun failsWhenTheBrowserNeverConfirms() {
        browser(temporary.key, reply = null)

        val paired = onMainLooper { RemotePairing.pair(client, relayUrl, temporary, phone, "Pixel", timeoutMs = 1_500) }

        assertFalse(paired)
    }

    @Test
    fun aConfirmationSealedWithAnotherKeyIsNotTrusted() {
        val stranger = RemoteCrypto.newKey()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"relay":"peers","phone":true,"webs":1}""")
                val forged = Outbox("web-x").stamp(JSONObject().put("type", "paired")).toString()
                webSocket.send(RemoteCrypto.seal(stranger, forged))
            }
        }))

        val paired = onMainLooper { RemotePairing.pair(client, relayUrl, temporary, phone, "Pixel", timeoutMs = 1_500) }

        assertFalse(paired)
    }

    @Test
    fun readsOnlyPairingLinksOfTheSite() {
        val link = "https://player.danzuniga.xyz/pair#r=${temporary.room}&k=${temporary.key}"
        assertEquals(temporary, RemotePairing.parse(Uri.parse(link)))
        assertNull(RemotePairing.parse(Uri.parse(link.replace("player.danzuniga.xyz", "evil.example"))))
        assertNull(RemotePairing.parse(Uri.parse(link.replace("https", "http"))))
        assertNull(RemotePairing.parse(Uri.parse(link.replace("/pair", "/monitor"))))
        assertNull(RemotePairing.parse(Uri.parse("https://player.danzuniga.xyz/pair#r=short&k=${temporary.key}")))
        assertNull(RemotePairing.parse(Uri.parse("https://player.danzuniga.xyz/pair")))
        assertNull(RemotePairing.parse(null))
    }
}
