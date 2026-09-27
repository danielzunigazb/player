package com.danielzuniga.player.remote

import android.net.Uri
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** Pairs against a stand-in for the relay with a browser in the room, over real WebSockets. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemotePairingTest {

    private val server = MockWebServer()
    private val client = OkHttpClient()
    private val temporary = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
    private val phone = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
    private val code = "AB3K9Z"

    /** What the phone sent the browser, opened, in order. */
    private val received = CopyOnWriteArrayList<JSONObject>()
    private val closed = CountDownLatch(1)

    /** Whether the phone looked up its own room (it must not before the right code). */
    @Volatile
    private var phoneAsked = false

    @Before
    fun setUp() = server.start()

    @After
    fun tearDown() {
        // Runs the link's stop that a timed-out pairing posted to the main thread.
        shadowOf(Looper.getMainLooper()).idle()
        server.shutdown()
    }

    private val relayUrl get() = server.url("/").toString().removeSuffix("/").replace("http", "ws")

    /** A browser in the room: says it's there, and answers each message with [replies], sealed. */
    private fun browser(key: String = temporary.key, replies: (JSONObject) -> List<JSONObject>) {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            val outbox = Outbox("web-1")

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"relay":"peers","phone":true,"webs":1}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(RemoteCrypto.open(temporary.key, text) ?: return)
                received += message
                for (reply in replies(message)) webSocket.send(RemoteCrypto.seal(key, outbox.stamp(reply).toString()))
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                closed.countDown()
            }
        }))
    }

    private fun typed(text: String) = JSONObject().put("type", "code").put("code", text)
    private val paired = JSONObject().put("type", "paired")

    private fun start(timeoutMs: Long = 20_000): Deferred<RemotePairing.Result> =
        CoroutineScope(Dispatchers.IO).async {
            RemotePairing.pair(client, relayUrl, temporary, code, { phoneAsked = true; phone }, "Pixel", timeoutMs)
        }

    /** Turns the main looper, where the link's callbacks run, until [done]. */
    private fun turnUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun <T> await(result: Deferred<T>): T {
        turnUntil { result.isCompleted }
        return runBlocking { result.await() }
    }

    private fun types() = received.map { it.getString("type") }

    @Test
    fun handsThePhoneRoomOnlyOnceTheRightCodeIsTyped() {
        browser { message ->
            when (message.getString("type")) {
                "askCode" -> listOf(typed(" ab3 k9z "))
                "welcome" -> listOf(paired)
                else -> emptyList()
            }
        }

        assertEquals(RemotePairing.Result.PAIRED, await(start()))

        assertEquals("/room/${temporary.room}?role=phone", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
        assertEquals(listOf("askCode", "welcome"), types())
        val welcome = received[1]
        assertEquals(phone.room, welcome.getString("room"))
        assertEquals(phone.key, welcome.getString("key"))
        assertEquals("Pixel", welcome.getString("name"))
    }

    @Test
    fun aWrongCodeGetsTheTriesLeftAndNoRoom() {
        browser { if (it.getString("type") == "askCode") listOf(typed("ZZZZZZ")) else emptyList() }

        assertEquals(RemotePairing.Result.TIMED_OUT, await(start(timeoutMs = 1_500)))

        assertEquals(listOf("askCode", "wrongCode"), types())
        assertEquals(2, received[1].getInt("attemptsLeft"))
        assertFalse(phoneAsked)
    }

    @Test
    fun threeWrongCodesEndItAndLeaveTheRoom() {
        browser {
            if (it.getString("type") == "askCode") {
                // The fourth, right one comes too late.
                listOf(typed("ZZZZZZ"), typed("YYYYYY"), typed("XXXXXX"), typed(code))
            } else {
                emptyList()
            }
        }

        assertEquals(RemotePairing.Result.WRONG_CODE, await(start()))

        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("askCode", "wrongCode", "wrongCode", "pairFailed"), types())
        assertEquals(listOf(2, 1), received.subList(1, 3).map { it.getInt("attemptsLeft") })
        assertFalse(phoneAsked)
    }

    @Test
    fun cancellingLeavesTheRoomWithoutAnotherWord() {
        browser { emptyList() }

        val pairing = start()
        turnUntil { received.isNotEmpty() }
        pairing.cancel()
        turnUntil { closed.count == 0L }

        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("askCode"), types())
        assertFalse(phoneAsked)
    }

    @Test
    fun failsWhenTheBrowserNeverConfirms() {
        browser { if (it.getString("type") == "askCode") listOf(typed(code)) else emptyList() }

        assertEquals(RemotePairing.Result.TIMED_OUT, await(start(timeoutMs = 1_500)))

        assertEquals(listOf("askCode", "welcome"), types())
    }

    @Test
    fun aConfirmationBeforeTheRightCodeIsNotTrusted() {
        browser { if (it.getString("type") == "askCode") listOf(paired) else emptyList() }

        assertEquals(RemotePairing.Result.TIMED_OUT, await(start(timeoutMs = 1_500)))
        assertEquals(listOf("askCode"), types())
    }

    @Test
    fun aCodeSealedWithAnotherKeyIsIgnored() {
        browser(key = RemoteCrypto.newKey()) { if (it.getString("type") == "askCode") listOf(typed(code)) else emptyList() }

        assertEquals(RemotePairing.Result.TIMED_OUT, await(start(timeoutMs = 1_500)))
        assertEquals(listOf("askCode"), types())
        assertFalse(phoneAsked)
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

    @Test
    fun readsTheRoomAndKeyFromExtrasWhenTheSiteHandsTheLinkOver() {
        val link = Uri.parse("https://player.danzuniga.xyz/pair")
        assertEquals(temporary, RemotePairing.parse(link, temporary.room, temporary.key))
        assertNull(RemotePairing.parse(link, "short", temporary.key))
        assertNull(RemotePairing.parse(Uri.parse("https://evil.example/pair"), temporary.room, temporary.key))
    }
}
