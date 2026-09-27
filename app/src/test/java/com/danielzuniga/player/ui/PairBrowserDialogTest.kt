package com.danielzuniga.player.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The dialog on its own: Accept is the only way to go on. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp")
class PairBrowserDialogTest {

    @get:Rule
    val compose = createComposeRule()

    @Test
    fun acceptAndCancelAnswerOnce() {
        var accepted = 0
        var dismissed = 0
        compose.setContent { PairBrowserDialog(code = "MMG42K", onAccept = { accepted++ }, onDismiss = { dismissed++ }) }

        compose.onNodeWithText("¿Vincular este navegador?").assertIsDisplayed()
        compose.onNodeWithText("MMG42K").assertIsDisplayed()
        compose.onNodeWithText("Aceptar").performClick()
        assertEquals(1 to 0, accepted to dismissed)
        compose.onNodeWithText("Cancelar").performClick()
        assertEquals(1 to 1, accepted to dismissed)
    }
}
