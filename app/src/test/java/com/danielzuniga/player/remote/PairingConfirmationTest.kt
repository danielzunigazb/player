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
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

/**
 * A pairing link only asks: nothing reaches the browser (no room created, no connection) until
 * the person accepts, and the question shows the code the monitor shows under its QR. A stand-in
 * relay (MockWebServer) replaces the real one for the app, the pairing and the playback service.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class PairingConfirmationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val temporary = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
    private val server = MockWebServer()
    private lateinit var service: ServiceController<PlaybackService>
    private var scenario: ActivityScenario<MainActivity>? = null

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
        scenario?.close()
        service.destroy()
        server.shutdown()
    }

    private fun linkTo(pairing: Pairing) = Intent(app, MainActivity::class.java)
        .setAction(Intent.ACTION_VIEW)
        .setData(Uri.parse("https://player.danzuniga.xyz/pair#r=${pairing.room}&k=${pairing.key}"))

    private fun open(uri: String, room: String? = null, key: String? = null) {
        val intent = Intent(app, MainActivity::class.java)
            .setAction(Intent.ACTION_VIEW)
            .setData(Uri.parse(uri))
        room?.let { intent.putExtra("r", it) }
        key?.let { intent.putExtra("k", it) }
        scenario = ActivityScenario.launch(intent)
    }

    private fun exists(text: String) =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()

    @Test
    fun theQrLinkAsksWithThePairingCodeAndCancelPairsNothing() {
        open("https://player.danzuniga.xyz/pair#r=${temporary.room}&k=${temporary.key}")

        compose.onNodeWithText("Pair this browser?").assertIsDisplayed()
        compose.onNodeWithText(RemoteCrypto.pairingCode(temporary.key)).assertIsDisplayed()
        assertNull(app.appContainer.remote.pairing.value)

        compose.onNodeWithText("Cancel").performClick()
        compose.waitForIdle()

        assertFalse(exists("Pair this browser?"))
        assertNull(app.appContainer.remote.pairing.value)
        assertFalse(app.appContainer.remote.enabled.value)
        assertEquals(0, server.requestCount)
    }

    @Test
    fun theSitesOpenInPlayerButtonAsksToo() {
        open("https://player.danzuniga.xyz/pair", temporary.room, temporary.key)

        compose.onNodeWithText("Pair this browser?").assertIsDisplayed()
        compose.onNodeWithText(RemoteCrypto.pairingCode(temporary.key)).assertIsDisplayed()
        assertNull(app.appContainer.remote.pairing.value)
    }

    @Test
    fun anotherLinkWhileAskingDoesNotReplaceTheQuestion() {
        val other = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
        scenario = ActivityScenario.launch(linkTo(temporary))
        compose.onNodeWithText(RemoteCrypto.pairingCode(temporary.key)).assertIsDisplayed()

        scenario!!.onActivity { InstrumentationRegistry.getInstrumentation().callActivityOnNewIntent(it, linkTo(other)) }
        compose.waitForIdle()

        compose.onNodeWithText(RemoteCrypto.pairingCode(temporary.key)).assertIsDisplayed()
        assertFalse(exists(RemoteCrypto.pairingCode(other.key)))
    }

    @Test
    fun acceptHandsTheBrowserThisPhonesRoomAndTurnsTheMonitorOn() {
        val welcomes = LinkedBlockingQueue<JSONObject>()
        // The browser waiting in the temporary room: confirms the welcome sealed with its key.
        server.enqueue(MockResponse().withWebSocketUpgrade(object : RelaySocket() {
            val outbox = Outbox("web-1")

            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"relay":"peers","phone":true,"webs":1}""")
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                val welcome = JSONObject(RemoteCrypto.open(temporary.key, text) ?: return)
                welcomes += welcome
                val paired = outbox.stamp(JSONObject().put("type", "paired")).toString()
                webSocket.send(RemoteCrypto.seal(temporary.key, paired))
            }
        }))
        // Then the phone's own room, once the monitor is on.
        server.enqueue(MockResponse().withWebSocketUpgrade(RelaySocket()))
        open("https://player.danzuniga.xyz/pair#r=${temporary.room}&k=${temporary.key}")

        compose.onNodeWithText("Accept").performClick()
        // The pairing's WebSocket callbacks come back through the main looper: turn it by hand.
        val deadline = System.currentTimeMillis() + 10_000
        while (!app.appContainer.remote.enabled.value && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(5)
        }

        val phone = app.appContainer.remote.pairing.value!!
        val welcome = welcomes.poll(1, TimeUnit.SECONDS)!!
        assertEquals(phone.room, welcome.getString("room"))
        assertEquals(phone.key, welcome.getString("key"))
        assertEquals("/room/${temporary.room}?role=phone", server.takeRequest(1, TimeUnit.SECONDS)!!.path)
        assertFalse(exists("Pair this browser?"))
        assertTrue(app.appContainer.remote.enabled.value)
    }
}
