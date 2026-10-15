package com.danielzuniga.player.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.backup.BackupFiles
import com.danielzuniga.player.data.backup.BackupRepository
import com.danielzuniga.player.ui.BackupUi

/** What a backup did, or why it didn't: shown when it ends, until the person closes it. */
@Composable
fun BackupDialog(outcome: BackupUi, onDismiss: () -> Unit) {
    if (outcome == BackupUi.Working) return
    val (title, lines) = when (outcome) {
        is BackupUi.Exported -> R.string.backup_exported_title to exportedLines(outcome.written)
        is BackupUi.Imported -> R.string.backup_imported_title to importedLines(outcome.result)
        is BackupUi.Failed -> R.string.backup_failed_title to listOf(stringResource(outcome.reason.message()))
        BackupUi.Working -> return
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                lines.forEach { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) } },
    )
}

@Composable
private fun exportedLines(written: BackupFiles.Written): List<String> = buildList {
    add(pluralStringResource(R.plurals.backup_favorites, written.favorites, written.favorites))
    add(
        stringResource(
            R.string.backup_exported_playlists,
            pluralStringResource(R.plurals.backup_playlists, written.playlists, written.playlists),
            pluralStringResource(R.plurals.song_count, written.songs, written.songs),
        )
    )
    if (written.skipped > 0) add(pluralStringResource(R.plurals.backup_skipped, written.skipped, written.skipped))
}

@Composable
private fun importedLines(result: BackupRepository.ImportResult): List<String> = buildList {
    if (!result.changed) {
        add(stringResource(R.string.backup_nothing_new))
    } else {
        if (result.favoritesAdded > 0) add(pluralStringResource(R.plurals.backup_favorites_added, result.favoritesAdded, result.favoritesAdded))
        if (result.playlistsCreated > 0) add(pluralStringResource(R.plurals.backup_playlists_created, result.playlistsCreated, result.playlistsCreated))
        if (result.playlistsUpdated > 0) add(pluralStringResource(R.plurals.backup_playlists_updated, result.playlistsUpdated, result.playlistsUpdated))
        if (result.songsAdded > 0) add(pluralStringResource(R.plurals.backup_songs_added, result.songsAdded, result.songsAdded))
        if (result.statsUpdated > 0) add(pluralStringResource(R.plurals.backup_stats_updated, result.statsUpdated, result.statsUpdated))
    }
    if (result.missing > 0) add(pluralStringResource(R.plurals.backup_missing, result.missing, result.missing))
}

private fun BackupUi.Reason.message(): Int = when (this) {
    BackupUi.Reason.LIBRARY_EMPTY -> R.string.backup_error_library_empty
    BackupUi.Reason.NOT_A_BACKUP -> R.string.backup_error_not_a_backup
    BackupUi.Reason.TOO_NEW -> R.string.backup_error_too_new
    BackupUi.Reason.BROKEN -> R.string.backup_error_broken
    BackupUi.Reason.UNREADABLE -> R.string.backup_error_unreadable
    BackupUi.Reason.UNWRITABLE -> R.string.backup_error_unwritable
    BackupUi.Reason.UNKNOWN -> R.string.backup_error_unknown
}
