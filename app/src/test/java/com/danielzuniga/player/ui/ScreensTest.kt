package com.danielzuniga.player.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.playback.NowPlaying
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.components.SongActions
import com.danielzuniga.player.ui.library.HomeScreen
import com.danielzuniga.player.ui.player.NowPlayingActions
import com.danielzuniga.player.ui.player.NowPlayingScreen
import com.danielzuniga.player.ui.playlists.PlaylistNameDialog
import com.danielzuniga.player.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class ScreensTest {

    @get:Rule
    val compose = createComposeRule()

    private val songs = listOf(
        Song(1, "De Música Ligera", "Soda Stereo", "Canción Animal", 10, 210_000, track = 1),
        Song(2, "Persiana Americana", "Soda Stereo", "Signos", 11, 280_000, track = 1),
    )
    private val index = LibraryIndex(songs)

    private val played = mutableListOf<Pair<List<Song>, Int>>()
    private val queued = mutableListOf<List<Song>>()
    private val actions = SongActions(
        play = { list, i -> played += list to i },
        shuffle = {},
        playNext = {},
        addToQueue = { queued += it },
        addToPlaylist = {},
        toggleFavorite = {},
        openAlbum = {},
        openArtist = {},
    )

    @Test
    fun homeListsSongsAndPlaysTappedOne() {
        showHome()
        compose.onNodeWithText("Persiana Americana").performClick()
        assertEquals(1, played.single().second)
    }

    @Test
    fun songMenuAddsToQueue() {
        showHome()
        compose.onAllNodesWithContentDescription("Más opciones")[0].performClick()
        compose.onNodeWithText("Añadir a la cola").performClick()
        assertEquals("De Música Ligera", queued.single().single().title)
    }

    @Test
    fun albumsTabShowsAlbums() {
        showHome()
        compose.onNodeWithText("Álbumes").performClick()
        compose.onNodeWithText("Canción Animal").assertIsDisplayed()
    }

    @Test
    fun nowPlayingShowsSongAndTogglesFavorite() {
        var favoriteToggled = false
        var playToggled = false
        compose.setContent {
            PlayerTheme {
                NowPlayingScreen(
                    state = PlayerUiState(isPlaying = true, positionMs = 30_000, durationMs = 210_000),
                    nowPlaying = NowPlaying(1, "De Música Ligera", "Soda Stereo", null),
                    isFavorite = false,
                    actions = NowPlayingActions(
                        onClose = {}, onTogglePlay = { playToggled = true }, onNext = {}, onPrevious = {},
                        onSeek = {}, onToggleShuffle = {}, onCycleRepeat = {},
                        onToggleFavorite = { favoriteToggled = true },
                        onOpenQueue = {}, onOpenSleepTimer = {}, onOpenSpeed = {}, onOpenEqualizer = {},
                        onAddToPlaylist = {}, onGoToAlbum = {}, onGoToArtist = {},
                    ),
                )
            }
        }
        compose.onNodeWithText("De Música Ligera").assertIsDisplayed()
        compose.onNodeWithText("0:30").assertIsDisplayed()
        compose.onNodeWithText("3:30").assertIsDisplayed()
        compose.onNodeWithContentDescription("Añadir a favoritas").performClick()
        compose.onNodeWithContentDescription("Pausar").performClick()
        assertTrue(favoriteToggled)
        assertTrue(playToggled)
    }

    @Test
    fun playlistNameDialogTrimsName() {
        var created: String? = null
        compose.setContent {
            PlayerTheme {
                PlaylistNameDialog("Nueva playlist", "Crear", onConfirm = { created = it }, onDismiss = {})
            }
        }
        compose.onNodeWithText("Crear").assertIsNotEnabled()
        compose.onNodeWithText("Nombre").performTextInput("  Viaje  ")
        compose.onNodeWithText("Crear").performClick()
        assertEquals("Viaje", created)
    }

    private fun showHome() {
        compose.setContent {
            PlayerTheme {
                CompositionLocalProvider(LocalSongActions provides actions) {
                    HomeScreen(
                        library = LibraryUiState(
                            songs = songs,
                            albums = index.albums,
                            artists = index.artists,
                            totalSongs = songs.size,
                            hasScanned = true,
                        ),
                        playlists = PlaylistsUiState(),
                        onQueryChange = {},
                        onSortChange = {},
                        onRefresh = {},
                        onOpenAlbum = {},
                        onOpenArtist = {},
                        onOpenPlaylist = {},
                        onOpenSmartPlaylist = {},
                        onCreatePlaylist = {},
                        onOpenSettings = {},
                        contentPadding = PaddingValues(),
                    )
                }
            }
        }
    }
}
