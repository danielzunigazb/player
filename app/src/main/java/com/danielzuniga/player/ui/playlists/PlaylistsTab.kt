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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.ui.theme.Dz
import androidx.compose.foundation.border
import com.danielzuniga.player.ui.components.Eyebrow
import com.danielzuniga.player.ui.components.Hairline
import com.danielzuniga.player.ui.PlaylistsUiState
import com.danielzuniga.player.ui.SmartPlaylist
import com.danielzuniga.player.ui.theme.DzIcons

fun SmartPlaylist.label(): Int = when (this) {
    SmartPlaylist.FAVORITES -> R.string.favorites
    SmartPlaylist.MOST_PLAYED -> R.string.most_played
    SmartPlaylist.RECENTLY_PLAYED -> R.string.recently_played
    SmartPlaylist.RECENTLY_ADDED -> R.string.recently_added
}

fun SmartPlaylist.icon(): ImageVector = when (this) {
    SmartPlaylist.FAVORITES -> DzIcons.HeartFilled
    SmartPlaylist.MOST_PLAYED -> DzIcons.Trending
    SmartPlaylist.RECENTLY_PLAYED -> DzIcons.History
    SmartPlaylist.RECENTLY_ADDED -> DzIcons.New
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
                icon = DzIcons.Add,
                title = stringResource(R.string.new_playlist),
                songCount = null,
                onClick = onCreatePlaylist,
                highlighted = true,
            )
        }
        items(state.playlists, key = { it.id }) { playlist ->
            PlaylistRow(
                icon = DzIcons.Queue,
                title = playlist.name,
                songCount = playlist.songCount,
                onClick = { onOpenPlaylist(playlist.id) },
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Column {
        Hairline(Modifier.padding(top = 16.dp))
        Eyebrow(text = text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
    }
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
                .background(Dz.colors.surface)
                .border(1.dp, Dz.colors.line),
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = if (highlighted) Dz.colors.gold else Dz.colors.inkMuted,
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
