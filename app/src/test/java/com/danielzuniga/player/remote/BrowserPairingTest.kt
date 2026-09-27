package com.danielzuniga.player.remote

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.danielzuniga.player.MainActivity
import com.danielzuniga.player.appContainer
import com.danielzuniga.player.playback.PlaybackService
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
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowToast
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Pairing from the activity: a pairing link makes the phone show a code, and the browser gets the
 * phone's room only once it sends that code. A stand-in relay (MockWebServer) with a fake browser
 * replaces the real one for the pairing and the playback service.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class BrowserPairingTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val temporary = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
    private val server = MockWebServer()
    private val received = CopyOnWriteArrayList<JSONObject>()
    private val closed = CountDownLatch(1)
    private lateinit var service: ServiceController<PlaybackService>
    private var scenario: ActivityScenario<MainActivity>? = null

    private val pairing get() = app.appContainer.browserPairing
    private val store get() = app.appContainer.remote

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        app.appContainer.settings.setOnlineLyrics(false)
        app.appContainer.settings.setOnlineTags(false)
        app.appContainer.settings.setAutoUpdates(false)
        server.start()
        // Before the service starts: its RemoteSession reads the address once.
        app.appContainer.relayUrl = server.url("/").toString().removeSuffix("/").replace("http", "ws")
        // Robolectric doesn't bind services on its own; hand it the real session binder.
        service = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, PlaybackService::class.java),
            service.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE)),
        )
    }

    @After
    fun tearDown() {
        pairing.cancel()
        scenario?.close()
        shadowOf(Looper.getMainLooper()).idle()
        service.destroy()
        server.shutdown()
    }

    /** The browser waiting in the temporary room: answers what the phone sends with [replies]. */
    private fun browser(replies: (JSONObject) -> List<JSONObject> = { emptyList() }) {
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            val outbox = Outbox("web-1")

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"relay":"peers","phone":true,"webs":1}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(RemoteCrypto.open(temporary.key, text) ?: return)
                received += message
                for (reply in replies(message)) {
                    webSocket.send(RemoteCrypto.seal(temporary.key, outbox.stamp(reply).toString()))
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                super.onClosing(webSocket, code, reason)
                closed.countDown()
            }
        }))
        // Then the phone's own room, if the monitor gets turned on.
        server.enqueue(MockResponse().withWebSocketUpgrade(RelaySocket()))
    }

    private fun typed(code: String) = JSONObject().put("type", "code").put("code", code)

    /** The code on the phone's screen, as the person would read it. */
    private fun shownCode() = pairing.pending.value!!.code

    private fun linkTo(pairing: Pairing) = Intent(app, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse("https://player.danzuniga.xyz/pair#r=${pairing.room}&k=${pairing.key}"))

    private fun open(intent: Intent = linkTo(temporary)) {
        scenario = ActivityScenario.launch(intent)
    }

    /** The pairing's WebSocket callbacks come back through the main looper: turn it by hand. */
    private fun turnUntil(done: () -> Boolean) {
        val deadline = System.currentTimeMillis() + 10_000
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }
    }

    private fun exists(text: String) = compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    private fun types() = received.map { it.getString("type") }

    @Test
    fun theQrLinkShowsACodeAndCancelLeavesWithoutAnotherWord() {
        browser()
        open()

        compose.onNodeWithText("Pair a browser").assertIsDisplayed()
        compose.onNodeWithText("Type this code in the monitor on your computer").assertIsDisplayed()
        compose.onNodeWithText(shownCode()).assertIsDisplayed()
        turnUntil { received.isNotEmpty() }
        compose.onNodeWithText("Cancel").performClick()
        turnUntil { closed.count == 0L }

        assertTrue(closed.await(5, TimeUnit.SECONDS))
        assertEquals(listOf("askCode"), types())
        assertFalse(exists("Pair a browser"))
        assertNull(pairing.pending.value)
        assertNull(store.pairing.value)
        assertFalse(store.enabled.value)
    }

    @Test
    fun theSitesOpenInPlayerButtonShowsTheSameDialog() {
        browser()
        open(
            Intent(app, MainActivity::class.java)
                .setAction(Intent.ACTION_VIEW)
                .setData(Uri.parse("https://player.danzuniga.xyz/pair"))
                .putExtra("r", temporary.room)
                .putExtra("k", temporary.key),
        )

        compose.onNodeWithText("Pair a browser").assertIsDisplayed()
        assertEquals(temporary, pairing.pending.value!!.temporary)
        compose.onNodeWithText(shownCode()).assertIsDisplayed()
    }

    @Test
    fun anotherLinkWhilePairingIsIgnored() {
        browser()
        val other = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
        open()
        val first = pairing.pending.value!!

        scenario!!.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, linkTo(other)) }
        compose.waitForIdle()

        assertEquals(first, pairing.pending.value)
        compose.onNodeWithText(first.code).assertIsDisplayed()
    }

    @Test
    fun theCodeSurvivesARotation() {
        browser()
        open()
        val first = pairing.pending.value!!

        scenario!!.recreate()
        compose.waitForIdle()

        assertEquals(first, pairing.pending.value)
        compose.onNodeWithText(first.code).assertIsDisplayed()
    }

    @Test
    fun typingTheCodeInTheBrowserPairsIt() {
        browser { message ->
            when (message.getString("type")) {
                "askCode" -> listOf(typed(shownCode().lowercase()))
                "welcome" -> listOf(JSONObject().put("type", "paired"))
                else -> emptyList()
            }
        }
        open()

        turnUntil { store.enabled.value }

        val phone = store.pairing.value!!
        assertEquals(listOf("askCode", "welcome"), types())
        assertEquals(phone.room, received[1].getString("room"))
        assertEquals(phone.key, received[1].getString("key"))
        assertEquals("/room/${temporary.room}?role=phone", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
        assertNull(pairing.pending.value)
        assertFalse(exists("Pair a browser"))
        assertEquals("Browser paired", ShadowToast.getTextOfLatestToast())
    }

    @Test
    fun threeWrongCodesCloseTheDialogAndPairNothing() {
        browser { message ->
            if (message.getString("type") == "askCode") List(3) { typed("ZZZZZZ") } else emptyList()
        }
        open()

        turnUntil { pairing.pending.value == null && received.size >= 4 }

        assertEquals(listOf("askCode", "wrongCode", "wrongCode", "pairFailed"), types())
        assertFalse(exists("Pair a browser"))
        assertNull(store.pairing.value)
        assertFalse(store.enabled.value)
        assertTrue(ShadowToast.getTextOfLatestToast().startsWith("Too many wrong codes"))
    }
}
