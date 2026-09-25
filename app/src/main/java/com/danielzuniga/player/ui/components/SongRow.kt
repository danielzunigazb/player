package com.danielzuniga.player.ui.components

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Album
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.ui.formatDuration

enum class SongLeading { ARTWORK, TRACK_NUMBER }

@Composable
fun SongRow(
    song: Song,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: SongLeading = SongLeading.ARTWORK,
    showAlbum: Boolean = true,
    extraMenuItems: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {},
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val isCurrent = LocalCurrentSongId.current == song.id
    val isFavorite = song.id in LocalFavoriteIds.current
    var menuOpen by remember { mutableStateOf(false) }
    val titleColor = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
    ) {
        when (leading) {
            SongLeading.ARTWORK ->
                Artwork(uri = song.artworkUri, cornerRadius = 8.dp, modifier = Modifier.size(48.dp))
            SongLeading.TRACK_NUMBER -> Text(
                text = if (song.track > 0) (song.track % 1000).toString() else "–",
                style = MaterialTheme.typography.bodyMedium,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.width(28.dp),
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else null,
                color = titleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (showAlbum) "${song.artist} · ${song.album}" else song.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (isFavorite) {
            Icon(
                Icons.Rounded.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(16.dp),
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing()
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Rounded.MoreVert, contentDescription = stringResource(R.string.more_options))
            }
            SongMenu(
                song = song,
                expanded = menuOpen,
                onDismiss = { menuOpen = false },
                extraItems = extraMenuItems,
            )
        }
    }
}

@Composable
fun SongMenu(
    song: Song,
    expanded: Boolean,
    onDismiss: () -> Unit,
    extraItems: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit = {},
) {
    val actions = LocalSongActions.current
    val isFavorite = song.id in LocalFavoriteIds.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        MenuItem(R.string.play_next, Icons.AutoMirrored.Rounded.PlaylistPlay) { actions.playNext(listOf(song)); onDismiss() }
        MenuItem(R.string.add_to_queue, Icons.AutoMirrored.Rounded.QueueMusic) { actions.addToQueue(listOf(song)); onDismiss() }
        MenuItem(R.string.add_to_playlist, Icons.AutoMirrored.Rounded.PlaylistAdd) { actions.addToPlaylist(listOf(song)); onDismiss() }
        MenuItem(
            if (isFavorite) R.string.remove_favorite else R.string.add_favorite,
            if (isFavorite) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
        ) { actions.toggleFavorite(song); onDismiss() }
        MenuItem(R.string.go_to_album, Icons.Rounded.Album) { actions.openAlbum(song.albumId); onDismiss() }
        MenuItem(R.string.go_to_artist, Icons.Rounded.Person) { actions.openArtist(song.artist); onDismiss() }
        extraItems(onDismiss)
    }
}

@Composable
fun MenuItem(label: Int, icon: ImageVector, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(stringResource(label)) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        onClick = onClick,
    )
}
