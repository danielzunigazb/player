package com.danielzuniga.player.ui.library

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class SearchBarTest {

    @get:Rule
    val compose = createComposeRule()

    /**
     * On a phone the query comes back from the view model only after the library is filtered on
     * a background thread, so keys typed in between arrive before it and its older values land
     * afterwards. The cursor must stay after the last character typed.
     */
    @Test
    fun staleQueriesFromTheViewModelDontMoveTheCursor() {
        var published by mutableStateOf("")
        val sent = mutableListOf<String>()
        compose.setContent {
            PlayerTheme { SearchBar(query = published, onQueryChange = { sent += it }, onClose = {}) }
        }
        val field = compose.onNode(hasSetTextAction())

        field.performTextInput("s")
        field.performTextInput("o")
        // The view model catches up one value at a time.
        sent.toList().forEach { published = it; compose.waitForIdle() }

        val node = field.fetchSemanticsNode()
        assertEquals(listOf("s", "so"), sent)
        assertEquals("so", node.config[SemanticsProperties.EditableText].text)
        assertEquals(TextRange(2), node.config[SemanticsProperties.TextSelectionRange])
    }
}
