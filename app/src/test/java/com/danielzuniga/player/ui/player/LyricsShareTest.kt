package com.danielzuniga.player.ui.player

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.lyrics.LyricLine
import com.danielzuniga.player.data.lyrics.Lyrics
import com.danielzuniga.player.data.lyrics.LyricsSource
import com.danielzuniga.player.ui.LyricsUiState
import com.danielzuniga.player.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w400dp-h860dp")
class LyricsShareTest {

    @get:Rule
    val compose = createComposeRule()

    private val lyrics = Lyrics.Synced(
        listOf(
            LyricLine(0, "Ella durmió al calor de las masas"),
            LyricLine(5_000, "y yo desperté queriendo soñarla"),
            LyricLine(10_000, "algún tiempo atrás pensé en escribirle"),
        ),
    )

    @Test
    fun holdALineThenTapMoreToShareThemInOrder() {
        val seeks = mutableListOf<Long>()
        val shared = mutableListOf<List<String>>()
        compose.setContent {
            PlayerTheme {
                LyricsView(
                    state = LyricsUiState(lyrics = lyrics, source = LyricsSource.FILE),
                    positionMs = 0,
                    onSeek = { seeks += it },
                    onShareLines = { shared += it },
                )
            }
        }

        // A tap still seeks while nothing is picked.
        compose.onNodeWithText("algún tiempo atrás pensé en escribirle").performClick()
        assertEquals(listOf(10_000L), seeks)

        compose.onNodeWithText("y yo desperté queriendo soñarla").performTouchInput { longClick() }
        compose.onNodeWithText("Ella durmió al calor de las masas").performClick()
        compose.onNodeWithText("Compartir 2 líneas", ignoreCase = true).performClick()

        assertEquals(listOf(listOf("Ella durmió al calor de las masas", "y yo desperté queriendo soñarla")), shared)
        assertEquals("picking doesn't seek", 1, seeks.size)
        // Sharing ends the selection.
        compose.onNodeWithText("Mantén presionada una línea para compartirla", substring = true, ignoreCase = true).assertExists()
    }
}
