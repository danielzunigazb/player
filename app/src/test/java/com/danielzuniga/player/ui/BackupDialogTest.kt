package com.danielzuniga.player.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.backup.BackupException
import com.danielzuniga.player.data.backup.BackupFiles
import com.danielzuniga.player.data.backup.BackupRepository.ImportResult
import com.danielzuniga.player.ui.settings.BackupDialog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.FileNotFoundException

/** What the person is told when a backup ends, and how failures become those words. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], qualifiers = "es-w411dp-h891dp")
class BackupDialogTest {

    @get:Rule
    val compose = createComposeRule()

    private fun show(outcome: BackupUi, onDismiss: () -> Unit = {}) =
        compose.setContent { BackupDialog(outcome, onDismiss) }

    @Test
    fun anExportSaysWhatWentInAndWhatWasLeftOut() {
        show(BackupUi.Exported(BackupFiles.Written(favorites = 12, playlists = 3, songs = 45, skipped = 2)))

        compose.onNodeWithText("Copia guardada").assertIsDisplayed()
        compose.onNodeWithText("12 favoritas").assertIsDisplayed()
        compose.onNodeWithText("3 playlists (45 canciones)").assertIsDisplayed()
        compose.onNodeWithText("2 canciones no estaban en la biblioteca en este momento y quedaron fuera.").assertIsDisplayed()
    }

    @Test
    fun singularsAreSingular() {
        show(BackupUi.Exported(BackupFiles.Written(favorites = 1, playlists = 1, songs = 1, skipped = 1)))

        compose.onNodeWithText("1 favorita").assertIsDisplayed()
        compose.onNodeWithText("1 playlist (1 canción)").assertIsDisplayed()
        compose.onNodeWithText("1 canción no estaba en la biblioteca en este momento y quedó fuera.").assertIsDisplayed()
    }

    @Test
    fun anImportListsOnlyWhatChanged() {
        show(BackupUi.Imported(ImportResult(favoritesAdded = 5, playlistsCreated = 1, songsAdded = 30, missing = 4)))

        compose.onNodeWithText("Importación terminada").assertIsDisplayed()
        compose.onNodeWithText("5 favoritas nuevas").assertIsDisplayed()
        compose.onNodeWithText("1 playlist nueva").assertIsDisplayed()
        compose.onNodeWithText("30 canciones agregadas a playlists").assertIsDisplayed()
        compose.onNodeWithText("4 canciones del archivo no están en este teléfono.").assertIsDisplayed()
        assertTrue(compose.onAllNodes(hasText("actualizad", substring = true)).fetchSemanticsNodes().isEmpty())
    }

    @Test
    fun anImportThatChangedNothingSaysSoInsteadOfShowingAnEmptyList() {
        show(BackupUi.Imported(ImportResult()))

        compose.onNodeWithText("Nada nuevo: todo lo del archivo ya estaba aquí.").assertIsDisplayed()
    }

    @Test
    fun aFailureSaysWhyAndOkCloses() {
        var dismissed = 0
        show(BackupUi.Failed(BackupUi.Reason.TOO_NEW)) { dismissed++ }

        compose.onNodeWithText("No se pudo terminar").assertIsDisplayed()
        compose.onNodeWithText("Esa copia la hizo una versión más nueva de Player. Actualiza la app para importarla.").assertIsDisplayed()
        compose.onNodeWithText("Aceptar").performClick()
        assertEquals(1, dismissed)
    }

    @Test
    fun whileItWorksThereIsNothingToDismiss() {
        show(BackupUi.Working)

        assertTrue(compose.onAllNodes(hasText("Aceptar")).fetchSemanticsNodes().isEmpty())
    }

    @Test
    @Config(qualifiers = "en-w411dp-h891dp")
    fun inEnglishToo() {
        show(BackupUi.Imported(ImportResult(favoritesAdded = 1, playlistsUpdated = 2, statsUpdated = 1, missing = 1)))

        compose.onNodeWithText("Import finished").assertIsDisplayed()
        compose.onNodeWithText("1 new favorite").assertIsDisplayed()
        compose.onNodeWithText("2 playlists got songs added").assertIsDisplayed()
        compose.onNodeWithText("1 play count updated").assertIsDisplayed()
        compose.onNodeWithText("1 song in the file isn't on this phone.").assertIsDisplayed()
        compose.onNodeWithText("OK").assertIsDisplayed()
    }

    @Test
    fun everyKindOfFailureHasItsOwnReason() {
        val io = BackupUi.Reason.UNREADABLE
        assertEquals(BackupUi.Failed(BackupUi.Reason.NOT_A_BACKUP), backupFailure(BackupException(BackupException.Reason.NOT_A_BACKUP), io))
        assertEquals(BackupUi.Failed(BackupUi.Reason.TOO_NEW), backupFailure(BackupException(BackupException.Reason.TOO_NEW), io))
        assertEquals(BackupUi.Failed(BackupUi.Reason.BROKEN), backupFailure(BackupException(BackupException.Reason.BROKEN), io))
        // A file that can't be opened is what the caller says it is: unreadable on import, unwritable on export.
        assertEquals(BackupUi.Failed(BackupUi.Reason.UNREADABLE), backupFailure(FileNotFoundException(), BackupUi.Reason.UNREADABLE))
        assertEquals(BackupUi.Failed(BackupUi.Reason.UNWRITABLE), backupFailure(FileNotFoundException(), BackupUi.Reason.UNWRITABLE))
        assertEquals(BackupUi.Failed(BackupUi.Reason.UNKNOWN), backupFailure(IllegalStateException("boom"), io))
    }
}
