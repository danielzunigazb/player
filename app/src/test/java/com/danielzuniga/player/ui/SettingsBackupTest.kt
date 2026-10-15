package com.danielzuniga.player.ui

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.ThemeMode
import com.danielzuniga.player.data.backup.BackupRepository.ImportResult
import com.danielzuniga.player.data.update.UpdateState
import com.danielzuniga.player.ui.settings.AppLanguage
import com.danielzuniga.player.ui.settings.SettingsScreen
import com.danielzuniga.player.ui.theme.PlayerTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp")
class SettingsBackupTest {

    @get:Rule
    val compose = createComposeRule()

    private var exports = 0
    private var imports = 0
    private var dismissed = 0

    private fun show(backup: BackupUi?) = compose.setContent {
        PlayerTheme(ThemeMode.DARK) {
            SettingsScreen(
                state = SettingsUiState(),
                onBack = {}, onThemeMode = {}, language = AppLanguage.SYSTEM, onLanguage = {},
                onDynamicColor = {}, onOnlineLyrics = {}, onMinDuration = {}, onOnlineTags = {},
                autoUpdates = true, onAutoUpdates = {}, updateState = UpdateState.UpToDate, onCheckUpdates = {},
                onRescan = {}, remoteEnabled = false, onRemoteEnabled = {}, remotePaired = false, onUnpairAll = {},
                backup = backup,
                onExportBackup = { exports++ },
                onImportBackup = { imports++ },
                onDismissBackup = { dismissed++ },
                bottomPadding = PaddingValues(),
            )
        }
    }

    private fun scrollTo(text: String) =
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))

    @Test
    fun theTwoRowsAskForTheFilePicker() {
        show(null)

        scrollTo("Exportar favoritas, playlists y estadísticas")
        compose.onNodeWithText("Guarda un archivo que puedes llevar a otro teléfono.").assertExists()
        compose.onNodeWithText("Exportar favoritas, playlists y estadísticas").performClick()
        scrollTo("Importar desde un archivo")
        compose.onNodeWithText("Suma lo que trae el archivo a lo que ya tienes. No borra ni reemplaza nada.").assertExists()
        compose.onNodeWithText("Importar desde un archivo").performClick()

        assertEquals(1, exports)
        assertEquals(1, imports)
    }

    @Test
    fun whileItWorksTheRowsDoNothingSoItCannotRunTwice() {
        show(BackupUi.Working)

        scrollTo("Importar desde un archivo")
        compose.onNodeWithText("Importar desde un archivo").assertIsNotEnabled()
        compose.onNodeWithText("Exportar favoritas, playlists y estadísticas").assertIsNotEnabled()
        compose.onNodeWithText("Importar desde un archivo").performClick()
        assertEquals(0, imports)
    }

    @Test
    fun theOutcomeShowsOverSettingsAndOkDismissesIt() {
        show(BackupUi.Imported(ImportResult(favoritesAdded = 2)))

        compose.onNodeWithText("2 favoritas nuevas").assertExists()
        compose.onNodeWithText("Aceptar").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun withNothingGoingOnTheRowsAreEnabled() {
        show(null)

        scrollTo("Importar desde un archivo")
        compose.onNodeWithText("Importar desde un archivo").assertIsEnabled()
    }
}
