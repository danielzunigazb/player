package com.danielzuniga.player.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.MainActivity
import com.danielzuniga.player.playback.PlaybackService
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** Boots the real activity and playback service (empty device library) and walks the main flows. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AppSmokeTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var service: ServiceController<PlaybackService>
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)

        // Robolectric doesn't bind services on its own; hand it the real session binder.
        service = Robolectric.buildService(PlaybackService::class.java).create()
        val bindIntent = Intent(MediaSessionService.SERVICE_INTERFACE)
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, PlaybackService::class.java),
            service.get().onBind(bindIntent),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        service.destroy()
    }

    @Test
    fun showsTabsAndEmptyLibrary() {
        compose.onNodeWithText("Canciones").assertIsDisplayed()
        compose.onNodeWithText("Álbumes").assertIsDisplayed()
        compose.waitUntil(5_000) { exists("No se encontró música en el dispositivo.") }
    }

    // The new-playlist dialog is tested in isolation (ScreensTest): in an edge-to-edge activity
    // Robolectric never idles once a dialog text field takes focus.
    @Test
    fun showsSmartPlaylists() {
        compose.onNodeWithText("Playlists").performClick()
        compose.onNodeWithText("Favoritas").assertIsDisplayed()
        compose.onNodeWithText("Más escuchadas").assertIsDisplayed()
        compose.onNodeWithText("Nueva playlist").assertIsDisplayed()
    }

    @Test
    fun opensSettingsAndSwitchesTheme() {
        compose.onNodeWithContentDescription("Ajustes").performClick()
        compose.onNodeWithText("Negro puro (AMOLED)").performClick()
        compose.onNodeWithText("Volver a escanear la música").assertIsDisplayed()
        compose.onNodeWithContentDescription("Volver").performClick()
        compose.onNodeWithText("Canciones").assertIsDisplayed()
    }

    private fun exists(text: String) =
        compose.onAllNodes(hasText(text)).fetchSemanticsNodes().isNotEmpty()
}
