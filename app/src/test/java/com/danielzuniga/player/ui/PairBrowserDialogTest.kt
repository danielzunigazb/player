package com.danielzuniga.player.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The dialog on its own: the code to type in the browser, and Cancel as the only button. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp")
class PairBrowserDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun showsTheCodeToTypeAndCancels() {
        var cancelled = 0
        compose.setContent { PairBrowserDialog(code = "AB3K9Z", onCancel = { cancelled++ }) }

        compose.onNodeWithText("Vincular un navegador").assertIsDisplayed()
        compose.onNodeWithText("Escríbelo solo en player.danzuniga.xyz/monitor, en tu computadora. No se lo digas a nadie.").assertIsDisplayed()
        compose.onNodeWithText("AB3K9Z").assertIsDisplayed()
        assertFalse(compose.onAllNodes(hasText("Aceptar")).fetchSemanticsNodes().isNotEmpty())
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1, cancelled)
    }
}
