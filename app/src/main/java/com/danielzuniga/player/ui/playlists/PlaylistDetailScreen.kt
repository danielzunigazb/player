package com.danielzuniga.player.ui.playlists

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.ui.PlaylistDetailUiState
import com.danielzuniga.player.ui.components.BackTopBar
import com.danielzuniga.player.ui.components.EmptyState
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.components.MenuItem
import com.danielzuniga.player.ui.components.PlayShuffleButtons
import com.danielzuniga.player.ui.components.SongRow
import com.danielzuniga.player.ui.formatDuration
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

@Composable
fun PlaylistDetailScreen(
    state: PlaylistDetailUiState,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onDelete: () -> Unit,
    onRemoveSong: (Song) -> Unit,
    onReorder: (List<Song>) -> Unit,
    bottomPadding: PaddingValues,
) {
    val actions = LocalSongActions.current
    val isUserPlaylist = state.playlistId != null
    val title = state.title ?: state.smart?.let { stringResource(it.label()) }.orEmpty()
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }

    // Local copy so drags feel instant; synced back to Room when the drag ends.
    var songs by remember { mutableStateOf(state.songs) }
    LaunchedEffect(state.songs) { songs = state.songs }

    val listState = rememberLazyListState()
    val reorderState = rememberReorderableLazyListState(listState) { from, to ->
        val fromIndex = songs.indexOfFirst { it.id == from.key }
        val toIndex = songs.indexOfFirst { it.id == to.key }
        if (fromIndex >= 0 && toIndex >= 0) {
            songs = songs.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        }
    }

    Scaffold(
        topBar = {
            BackTopBar(title = title, onBack = onBack) {
                IconButton(onClick = { actions.addToPlaylist(songs) }, enabled = songs.isNotEmpty()) {
                    Icon(
                        Icons.AutoMirrored.Rounded.PlaylistAdd,
                        contentDescription = stringResource(if (isUserPlaylist) R.string.add_to_playlist else R.string.save_as_playlist),
                    )
                }
                if (isUserPlaylist) {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        MenuItem(R.string.rename, Icons.Rounded.Edit) { menuOpen = false; renaming = true }
                        MenuItem(R.string.delete, Icons.Rounded.Delete) { menuOpen = false; deleting = true }
                    }
                }
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (state.loaded && songs.isEmpty()) {
            EmptyState(stringResource(R.string.empty_list), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            state = listState,
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = bottomPadding.calculateBottomPadding(),
            ),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(key = "header") {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = pluralStringResource(R.plurals.song_count, songs.size, songs.size) +
                            " · " + formatDuration(songs.sumOf { it.durationMs }),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlayShuffleButtons(songs, Modifier.padding(top = 12.dp))
                }
            }
            itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                ReorderableItem(reorderState, key = song.id, enabled = isUserPlaylist) { isDragging ->
                    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "drag")
                    Surface(shadowElevation = elevation) {
                        SongRow(
                            song = song,
                            onClick = { actions.play(songs, index) },
                            extraMenuItems = { dismiss ->
                                if (isUserPlaylist) {
                                    MenuItem(R.string.remove_from_playlist, Icons.Rounded.RemoveCircleOutline) {
                                        onRemoveSong(song)
                                        dismiss()
                                    }
                                }
                            },
                            trailing = {
                                if (isUserPlaylist) {
                                    IconButton(
                                        onClick = {},
                                        modifier = Modifier.draggableHandle(onDragStopped = { onReorder(songs) }),
                                    ) {
                                        Icon(Icons.Rounded.DragHandle, contentDescription = stringResource(R.string.reorder))
                                    }
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    if (renaming) {
        PlaylistNameDialog(
            title = stringResource(R.string.rename_playlist),
            confirmLabel = stringResource(R.string.save),
            initialName = state.title.orEmpty(),
            onConfirm = {
                onRename(it)
                renaming = false
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDeleteDialog(
            name = title,
            onConfirm = {
                deleting = false
                onDelete()
            },
            onDismiss = { deleting = false },
        )
    }
}
