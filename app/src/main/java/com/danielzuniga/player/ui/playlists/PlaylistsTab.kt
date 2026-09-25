package com.danielzuniga.player.ui.playlists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.ui.PlaylistsUiState
import com.danielzuniga.player.ui.SmartPlaylist

fun SmartPlaylist.label(): Int = when (this) {
    SmartPlaylist.FAVORITES -> R.string.favorites
    SmartPlaylist.MOST_PLAYED -> R.string.most_played
    SmartPlaylist.RECENTLY_PLAYED -> R.string.recently_played
    SmartPlaylist.RECENTLY_ADDED -> R.string.recently_added
}

fun SmartPlaylist.icon(): ImageVector = when (this) {
    SmartPlaylist.FAVORITES -> Icons.Rounded.Favorite
    SmartPlaylist.MOST_PLAYED -> Icons.AutoMirrored.Rounded.TrendingUp
    SmartPlaylist.RECENTLY_PLAYED -> Icons.Rounded.History
    SmartPlaylist.RECENTLY_ADDED -> Icons.Rounded.NewReleases
}

@Composable
fun PlaylistsTab(
    state: PlaylistsUiState,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmartPlaylist: (SmartPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    contentPadding: PaddingValues,
) {
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        item { SectionHeader(stringResource(R.string.smart_playlists)) }
        items(SmartPlaylist.entries) { smart ->
            PlaylistRow(
                icon = smart.icon(),
                title = stringResource(smart.label()),
                songCount = state.smartCounts[smart] ?: 0,
                onClick = { onOpenSmartPlaylist(smart) },
            )
        }
        item { SectionHeader(stringResource(R.string.my_playlists)) }
        item {
            PlaylistRow(
                icon = Icons.Rounded.Add,
                title = stringResource(R.string.new_playlist),
                songCount = null,
                onClick = onCreatePlaylist,
                highlighted = true,
            )
        }
        items(state.playlists, key = { it.id }) { playlist ->
            PlaylistRow(
                icon = Icons.AutoMirrored.Rounded.QueueMusic,
                title = playlist.name,
                songCount = playlist.songCount,
                onClick = { onOpenPlaylist(playlist.id) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun PlaylistRow(
    icon: ImageVector,
    title: String,
    songCount: Int?,
    onClick: () -> Unit,
    highlighted: Boolean = false,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    if (highlighted) MaterialTheme.colorScheme.primaryContainer
                    else MaterialTheme.colorScheme.surfaceVariant
                ),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) MaterialTheme.colorScheme.onPrimaryContainer
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Column(modifier = Modifier.padding(start = 16.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (songCount != null) {
                Text(
                    text = pluralStringResource(R.plurals.song_count, songCount, songCount),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
