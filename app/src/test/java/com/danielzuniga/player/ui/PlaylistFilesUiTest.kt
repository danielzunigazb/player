package com.danielzuniga.player.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.backup.BackupRepository.ImportResult
import com.danielzuniga.player.data.backup.NoPlaylistException
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.components.SongActions
import com.danielzuniga.player.ui.playlists.PlaylistDetailScreen
import com.danielzuniga.player.ui.playlists.PlaylistsTab
import com.danielzuniga.player.ui.settings.BackupDialog
import com.danielzuniga.player.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.IOException

/** Playlists in and out of M3U files: where the person asks for it, and what they're told after. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp")
class PlaylistFilesUiTest {

    @get:Rule
    val compose = createComposeRule()

    private val songs = listOf(Song(1, "De Música Ligera", "Soda Stereo", "Canción Animal", 10, 210_000))
    private val actions = SongActions(
        play = { _, _ -> }, shuffle = {}, playNext = {}, addToQueue = {}, addToPlaylist = {},
        toggleFavorite = {}, openAlbum = {}, openArtist = {},
    )

    private fun showDetail(state: PlaylistDetailUiState, onExport: () -> Unit = {}) = compose.setContent {
        PlayerTheme {
            CompositionLocalProvider(LocalSongActions provides actions) {
                PlaylistDetailScreen(
                    state = state,
                    onBack = {}, onRename = {}, onDelete = {}, onRemoveSong = {}, onReorder = {},
                    bottomPadding = PaddingValues(),
                    onExportM3u = onExport,
                )
            }
        }
    }

    @Test
    fun thePlaylistsTabOffersToImportAnM3uFile() {
        var imports = 0
        compose.setContent {
            PlayerTheme {
                PlaylistsTab(
                    state = PlaylistsUiState(),
                    onOpenPlaylist = {}, onOpenSmartPlaylist = {}, onCreatePlaylist = {},
                    contentPadding = PaddingValues(),
                    onImportPlaylist = { imports++ },
                )
            }
        }

        compose.onNodeWithText("Importar playlist (M3U)").assertIsDisplayed()
        compose.onNodeWithText("Importar playlist (M3U)").performClick()
        assertEquals(1, imports)
    }

    /** The playlist's own menu button: every song row has one with the same name, below the top bar. */
    private fun openPlaylistMenu() {
        val buttons = compose.onAllNodesWithContentDescription("Más opciones")
        val topmost = buttons.fetchSemanticsNodes().withIndex().minBy { it.value.boundsInRoot.top }.index
        buttons[topmost].performClick()
    }

    @Test
    fun aPlaylistOfYoursCanBeExportedFromItsMenu() {
        var exports = 0
        showDetail(PlaylistDetailUiState(title = "Rock", playlistId = 7, songs = songs, loaded = true)) { exports++ }

        openPlaylistMenu()
        compose.onNodeWithText("Exportar como M3U").performClick()

        assertEquals(1, exports)
    }

    @Test
    fun anEmptyPlaylistHasNothingToExportAndSmartOnesAreNotFiles() {
        showDetail(PlaylistDetailUiState(title = "Vacía", playlistId = 7, songs = emptyList(), loaded = true))
        openPlaylistMenu()
        assertTrue(compose.onAllNodes(hasText("Exportar como M3U")).fetchSemanticsNodes().isEmpty())
        compose.onNodeWithText("Renombrar").assertIsDisplayed()
    }

    @Test
    fun smartPlaylistsHaveNoPlaylistMenuOfTheirOwn() {
        showDetail(PlaylistDetailUiState(smart = SmartPlaylist.FAVORITES, songs = songs, loaded = true))
        // Only the song's own menu: a smart playlist can't be renamed, deleted or exported.
        assertEquals(1, compose.onAllNodesWithContentDescription("Más opciones").fetchSemanticsNodes().size)
    }

    @Test
    fun anExportSaysHowManySongsWentIn() {
        compose.setContent { BackupDialog(BackupUi.PlaylistExported(12)) {} }

        compose.onNodeWithText("Playlist exportada").assertIsDisplayed()
        compose.onNodeWithText("12 canciones").assertIsDisplayed()
    }

    @Test
    fun aFileWithNoPlaylistSaysSo() {
        compose.setContent { BackupDialog(BackupUi.Failed(BackupUi.Reason.NOT_A_PLAYLIST)) {} }

        compose.onNodeWithText("No se pudo terminar").assertIsDisplayed()
        compose.onNodeWithText("Ese archivo no tiene una playlist.").assertIsDisplayed()
    }

    @Test
    fun whenNothingCouldBeAddedBecauseNoneOfItIsHereItDoesNotClaimItWasAllHereAlready() {
        compose.setContent { BackupDialog(BackupUi.Imported(ImportResult(missing = 3))) {} }

        compose.onNodeWithText("No se agregó nada.").assertIsDisplayed()
        compose.onNodeWithText("3 canciones del archivo no están en este teléfono.").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("ya estaba aquí", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun theFailureMappingKnowsPlaylistFiles() {
        assertEquals(BackupUi.Failed(BackupUi.Reason.NOT_A_PLAYLIST), backupFailure(NoPlaylistException(), BackupUi.Reason.UNREADABLE))
        assertEquals(BackupUi.Failed(BackupUi.Reason.UNREADABLE), backupFailure(IOException(), BackupUi.Reason.UNREADABLE))
    }
}
