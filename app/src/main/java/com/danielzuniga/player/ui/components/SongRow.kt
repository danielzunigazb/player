package com.danielzuniga.player.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.ui.formatDuration
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons
import com.danielzuniga.player.ui.theme.DzType

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
    val titleColor = if (isCurrent) Dz.colors.gold else Dz.colors.ink

    val isPlaying = LocalIsPlaying.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .fillMaxWidth()
            .then(if (isCurrent) Modifier.background(Dz.colors.surface) else Modifier)
            .combinedClickable(onClick = onClick, onLongClick = { menuOpen = true })
            .padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        when (leading) {
            SongLeading.ARTWORK -> Box(contentAlignment = Alignment.Center) {
                Artwork(uri = song.artworkUri, modifier = Modifier.size(48.dp))
                if (isCurrent) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(48.dp)
                            .background(Dz.colors.bg.copy(alpha = 0.7f)),
                    ) {
                        PlayingBars(playing = isPlaying, color = Dz.colors.gold, size = 16.dp)
                    }
                }
            }
            SongLeading.TRACK_NUMBER -> Box(contentAlignment = Alignment.Center, modifier = Modifier.width(28.dp)) {
                if (isCurrent) {
                    PlayingBars(playing = isPlaying, color = Dz.colors.gold, size = 14.dp)
                } else {
                    Text(
                        text = if (song.track > 0) (song.track % 1000).toString() else "–",
                        style = DzType.small,
                        color = Dz.colors.inkMuted,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
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
                DzIcons.HeartFilled,
                contentDescription = null,
                tint = Dz.colors.inkMuted,
                modifier = Modifier.size(14.dp),
            )
        }
        Text(
            text = formatDuration(song.durationMs),
            style = DzType.small,
            color = Dz.colors.inkMuted,
        )
        trailing()
        Box {
            IconButton(onClick = { menuOpen = true }) {
                Icon(DzIcons.More, contentDescription = stringResource(R.string.more_options))
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
        MenuItem(R.string.play_next, DzIcons.PlayNext) { actions.playNext(listOf(song)); onDismiss() }
        MenuItem(R.string.add_to_queue, DzIcons.AddToQueue) { actions.addToQueue(listOf(song)); onDismiss() }
        MenuItem(R.string.add_to_playlist, DzIcons.PlaylistAdd) { actions.addToPlaylist(listOf(song)); onDismiss() }
        MenuItem(R.string.share, DzIcons.Share) { actions.share(song); onDismiss() }
        MenuItem(
            if (isFavorite) R.string.remove_favorite else R.string.add_favorite,
            if (isFavorite) DzIcons.HeartFilled else DzIcons.Heart,
        ) { actions.toggleFavorite(song); onDismiss() }
        MenuItem(R.string.go_to_album, DzIcons.Album) { actions.openAlbum(song.albumId); onDismiss() }
        MenuItem(R.string.go_to_artist, DzIcons.Artist) { actions.openArtist(song.artists.first()); onDismiss() }
        if (song.tagsFixed) {
            MenuItem(R.string.restore_tags, DzIcons.Refresh) { actions.restoreTags(song); onDismiss() }
        }
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
