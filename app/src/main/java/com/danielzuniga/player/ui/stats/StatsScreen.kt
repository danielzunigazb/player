package com.danielzuniga.player.ui.stats

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.ListeningStats
import com.danielzuniga.player.ui.components.BackTopBar
import com.danielzuniga.player.ui.components.EmptyState
import com.danielzuniga.player.ui.components.Eyebrow
import com.danielzuniga.player.ui.components.Hairline
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzType

/**
 * What the play counts say: totals and the most played songs, artists and albums. A song row plays
 * the list from there, like a playlist; an artist or album row opens it.
 */
@Composable
fun StatsScreen(
    stats: ListeningStats,
    onBack: () -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenAlbum: (Long) -> Unit,
    bottomPadding: PaddingValues,
) {
    val actions = LocalSongActions.current
    Scaffold(
        topBar = { BackTopBar(stringResource(R.string.stats_title), onBack) },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (stats.isEmpty) {
            EmptyState(stringResource(R.string.stats_empty), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding(),
                bottom = bottomPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                ) {
                    Total(stats.totalPlays, R.string.stats_plays_label)
                    Total(stats.songsHeard, R.string.stats_songs_label)
                }
            }
            item { Section(stringResource(R.string.stats_top_songs)) }
            val songs = stats.topSongs.map { it.song }
            stats.topSongs.forEachIndexed { index, ranked ->
                item(key = "song-${ranked.song.id}") {
                    RankRow(
                        rank = index + 1,
                        title = ranked.song.title,
                        detail = detail(ranked.song.artist, ranked.plays),
                        onClick = { actions.play(songs, index) },
                    )
                }
            }
            if (stats.topArtists.isNotEmpty()) {
                item { Section(stringResource(R.string.stats_top_artists)) }
                stats.topArtists.forEachIndexed { index, artist ->
                    item(key = "artist-${artist.name}") {
                        RankRow(
                            rank = index + 1,
                            title = artist.name,
                            detail = detail(pluralStringResource(R.plurals.song_count, artist.songs, artist.songs), artist.plays),
                            onClick = { onOpenArtist(artist.name) },
                        )
                    }
                }
            }
            if (stats.topAlbums.isNotEmpty()) {
                item { Section(stringResource(R.string.stats_top_albums)) }
                stats.topAlbums.forEachIndexed { index, album ->
                    item(key = "album-${album.id}") {
                        RankRow(
                            rank = index + 1,
                            title = album.title,
                            detail = detail(album.artist, album.plays),
                            onClick = { onOpenAlbum(album.id) },
                        )
                    }
                }
            }
            item {
                Text(
                    text = stringResource(R.string.stats_note),
                    style = DzType.small,
                    color = Dz.colors.inkMuted,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        }
    }
}

@Composable
private fun detail(first: String, plays: Int): String =
    stringResource(R.string.stats_row_detail, first, pluralStringResource(R.plurals.play_count, plays, plays))

@Composable
private fun Total(value: Int, label: Int) {
    Column {
        Text(value.toString(), style = DzType.h1, color = Dz.colors.gold)
        Eyebrow(stringResource(label))
    }
}

@Composable
private fun Section(text: String) {
    Column {
        Hairline(Modifier.padding(top = 8.dp))
        Eyebrow(text = text, modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp))
    }
}

@Composable
private fun RankRow(rank: Int, title: String, detail: String, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Text(
            text = rank.toString(),
            style = DzType.label,
            color = Dz.colors.gold,
            modifier = Modifier.width(32.dp),
        )
        Column {
            Text(title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
