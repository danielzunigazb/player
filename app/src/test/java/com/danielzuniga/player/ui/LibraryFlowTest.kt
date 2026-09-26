package com.danielzuniga.player.ui

import android.Manifest
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performTextInput
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.FakeMediaProvider
import com.danielzuniga.player.FakeSong
import com.danielzuniga.player.MainActivity
import com.danielzuniga.player.playback.PlaybackService
import com.danielzuniga.player.appContainer
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

/** Full app on a fake device library: playing, favorites and browsing. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp")
class LibraryFlowTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private lateinit var service: ServiceController<PlaybackService>
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before
    fun launch() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.READ_MEDIA_AUDIO)
        // Keep UI tests off the network: no LRCLIB lookups.
        app.appContainer.settings.setOnlineLyrics(false)
        app.appContainer.settings.setOnlineTags(false)
        FakeMediaProvider.install(
            listOf(
                FakeSong(1, "De Música Ligera", "Soda Stereo", "Canción Animal", albumId = 10, track = 1),
                FakeSong(2, "Un Millón de Años Luz", "Soda Stereo", "Canción Animal", albumId = 10, track = 2),
                FakeSong(3, "Eres", "Café Tacvba", "Cuatro Caminos", albumId = 20, track = 1),
            )
        )
        service = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, PlaybackService::class.java),
            service.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE)),
        )
        scenario = ActivityScenario.launch(MainActivity::class.java)
        compose.waitUntil(5_000) { exists("Eres") }
    }

    @After
    fun tearDown() {
        scenario.close()
        service.destroy()
    }

    @Test
    fun playSongOpenNowPlayingAndFavorite() {
        compose.onNodeWithText("Eres").performClick()
        // Mini player appears with the song.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Eres").fetchSemanticsNodes().size >= 2 }

        compose.onAllNodesWithText("Eres")[1].performClick()
        compose.onNodeWithText("Reproduciendo", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithContentDescription("Añadir a favoritas").performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodes(hasContentDescription("Quitar de favoritas"))
                .fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithContentDescription("Cerrar reproductor").performClick()

        compose.onNodeWithText("Playlists", ignoreCase = true).performClick()
        compose.waitUntil(5_000) { exists("1 canción") }
    }

    @Test
    fun browseAlbumThenArtist() {
        compose.onNodeWithText("Álbumes", ignoreCase = true).performClick()
        compose.onNodeWithText("Canción Animal").performClick()

        compose.onNodeWithText("Un Millón de Años Luz").assertIsDisplayed()
        compose.onAllNodesWithText("Soda Stereo").onFirst().performClick()

        compose.waitUntil(5_000) { exists("1 álbum · 2 canciones") }
    }

    @Test
    fun browseFolders() {
        compose.onNodeWithText("Carpetas", ignoreCase = true).performClick()
        compose.onNodeWithText("Cuatro Caminos").performClick()
        compose.waitUntil(5_000) { exists("$ /storage/emulated/0/Music/Cuatro Caminos") }
        compose.onNodeWithText("Eres").assertIsDisplayed()
    }

    @Test
    fun searchIgnoresAccents() {
        compose.onNodeWithContentDescription("Buscar").performClick()
        compose.onNodeWithText("Buscar en tu música").performTextInput("musica")
        compose.waitUntil(5_000) { !exists("Eres") }
        compose.onNodeWithText("De Música Ligera").assertIsDisplayed()
    }

    @Test
    fun terminalPlaysWhatYouType() {
        compose.onNodeWithContentDescription("Terminal").performClick()
        compose.onNodeWithTag("terminal_input").performTextInput("play eres")
        compose.onNodeWithTag("terminal_input").performImeAction()

        compose.waitUntil(5_000) { exists("▶ Eres — Café Tacvba") }
        compose.onNodeWithContentDescription("Cerrar terminal").performClick()
        // The mini player picks the song up.
        compose.waitUntil(5_000) { compose.onAllNodesWithText("Eres").fetchSemanticsNodes().size >= 2 }
    }

    private fun exists(text: String) =
        compose.onAllNodes(hasText(text, ignoreCase = true)).fetchSemanticsNodes().isNotEmpty()
}
