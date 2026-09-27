package com.danielzuniga.player.remote

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.MainActivity
import com.danielzuniga.player.appContainer
import com.danielzuniga.player.playback.PlaybackService
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/**
 * A pairing link only asks: nothing reaches the browser (no room created, no connection) until
 * the person accepts, and the question shows the code the monitor shows under its QR.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "en-w411dp-h891dp")
class PairingConfirmationTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val temporary = Pairing(RemoteCrypto.newRoom(), RemoteCrypto.newKey())
    private lateinit var service: ServiceController<PlaybackService>
    private var scenario: ActivityScenario<MainActivity>? = null

    @Before
    fun setUp() {
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        app.appContainer.settings.setOnlineLyrics(false)
        app.appContainer.settings.setOnlineTags(false)
        app.appContainer.settings.setAutoUpdates(false)
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
    }

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
    }

    @Test
    fun theSitesOpenInPlayerButtonAsksToo() {
        open("https://player.danzuniga.xyz/pair", temporary.room, temporary.key)

        compose.onNodeWithText("Pair this browser?").assertIsDisplayed()
        compose.onNodeWithText(RemoteCrypto.pairingCode(temporary.key)).assertIsDisplayed()
        assertNull(app.appContainer.remote.pairing.value)
    }
}
