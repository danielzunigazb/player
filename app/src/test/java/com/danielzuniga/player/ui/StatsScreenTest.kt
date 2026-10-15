package com.danielzuniga.player.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.hasScrollAction
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.ListeningStats
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.db.PlayStatEntity
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.components.SongActions
import com.danielzuniga.player.ui.playlists.PlaylistsTab
import com.danielzuniga.player.ui.stats.StatsScreen
import com.danielzuniga.player.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp")
class StatsScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private val songs = listOf(
        Song(1, "De Música Ligera", "Soda Stereo", "Canción Animal", 10, 210_000),
        Song(2, "Persiana Americana", "Soda Stereo", "Signos", 11, 280_000),
        Song(3, "Puente", "Gustavo Cerati", "Bocanada", 12, 250_000),
    )
    private val stats = ListeningStats.compute(
        listOf(PlayStatEntity(1, 12, 3), PlayStatEntity(2, 1, 2), PlayStatEntity(3, 7, 1)),
        LibraryIndex(songs),
    )

    private val played = mutableListOf<Pair<List<Song>, Int>>()
    private val actions = SongActions(
        play = { list, index -> played += list to index }, shuffle = {}, playNext = {}, addToQueue = {},
        addToPlaylist = {}, toggleFavorite = {}, openAlbum = {}, openArtist = {},
    )
    private val artists = mutableListOf<String>()
    private val albums = mutableListOf<Long>()

    private fun show(stats: ListeningStats) = compose.setContent {
        PlayerTheme {
            CompositionLocalProvider(LocalSongActions provides actions) {
                StatsScreen(
                    stats = stats,
                    onBack = {},
                    onOpenArtist = { artists += it },
                    onOpenAlbum = { albums += it },
                    bottomPadding = PaddingValues(),
                )
            }
        }
    }

    /** Section titles are drawn in capitals, so the search ignores case. */
    private fun scrollTo(text: String) =
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text, ignoreCase = true))

    @Test
    fun showsTheTotalsAndTheMostPlayedSongsWithTheirPlays() {
        show(stats)

        compose.onNodeWithText("20").assertIsDisplayed() // 12 + 1 + 7 plays
        compose.onNodeWithText("reproducciones", ignoreCase = true).assertIsDisplayed()
        compose.onNodeWithText("canciones distintas", ignoreCase = true).assertIsDisplayed()
        // "3" is both the songs heard and the third rank.
        compose.onAllNodesWithText("3").assertCountEquals(2)
        compose.onNodeWithText("De Música Ligera").assertIsDisplayed()
        // The song, and its album, which only has that song.
        compose.onAllNodesWithText("Soda Stereo · 12 reproducciones").assertCountEquals(2)
        // Singular when it's one.
        scrollTo("Soda Stereo · 1 reproducción")
        compose.onNodeWithText("Soda Stereo · 1 reproducción").assertIsDisplayed()
    }

    @Test
    fun aSongRowPlaysTheListFromThatSong() {
        show(stats)

        compose.onNodeWithText("Puente").performClick()

        val (list, index) = played.single()
        assertEquals(listOf("De Música Ligera", "Puente", "Persiana Americana"), list.map { it.title })
        assertEquals(1, index)
    }

    @Test
    fun artistsAndAlbumsHaveTheirOwnSectionsAndOpenFromTheirRow() {
        show(stats)

        scrollTo("Artistas más reproducidos")
        compose.onNodeWithText("Artistas más reproducidos", ignoreCase = true).assertIsDisplayed()
        scrollTo("2 canciones · 13 reproducciones")
        compose.onNodeWithText("2 canciones · 13 reproducciones").performClick()
        assertEquals(listOf("Soda Stereo"), artists)

        scrollTo("Bocanada")
        compose.onNodeWithText("Bocanada").performClick()
        assertEquals(listOf(12L), albums)
    }

    @Test
    fun saysWhatAPlayIsSoTheNumbersAreNotOverRead() {
        show(stats)

        scrollTo("Una reproducción cuenta desde que la canción empieza a sonar, así que una que saltas también cuenta.")
        compose.onNodeWithText("Una reproducción cuenta desde que la canción empieza a sonar, así que una que saltas también cuenta.")
            .assertIsDisplayed()
    }

    @Test
    fun withNothingPlayedItSaysSoInsteadOfShowingZeros() {
        show(ListeningStats.EMPTY)

        compose.onNodeWithText("Aún no has reproducido nada. Cuando escuches música, aquí verás lo que más suena.").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("reproducciones", substring = true, ignoreCase = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun thePlaylistsTabLeadsToIt() {
        var opened = 0
        compose.setContent {
            PlayerTheme {
                PlaylistsTab(
                    state = PlaylistsUiState(),
                    onOpenPlaylist = {}, onOpenSmartPlaylist = {}, onCreatePlaylist = {},
                    contentPadding = PaddingValues(),
                    onOpenStats = { opened++ },
                )
            }
        }

        compose.onNodeWithText("Estadísticas de escucha").performClick()
        assertEquals(1, opened)
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun inEnglishToo() {
        show(stats)

        compose.onAllNodesWithText("Soda Stereo · 12 plays").onFirst().assertIsDisplayed()
        compose.onNodeWithText("different songs", ignoreCase = true).assertIsDisplayed()
    }
}
