package com.danielzuniga.player.ui.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistAdd
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.Album
import com.danielzuniga.player.data.Artist
import com.danielzuniga.player.data.Folder
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.ui.components.Artwork
import com.danielzuniga.player.ui.components.BackTopBar
import com.danielzuniga.player.ui.components.EmptyState
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.components.PlayShuffleButtons
import com.danielzuniga.player.ui.components.SongLeading
import com.danielzuniga.player.ui.components.SongRow
import com.danielzuniga.player.ui.formatDuration
import com.danielzuniga.player.ui.library.AlbumCard

@Composable
fun AlbumScreen(album: Album?, onBack: () -> Unit, bottomPadding: PaddingValues) {
    val actions = LocalSongActions.current
    Scaffold(
        topBar = {
            BackTopBar(title = album?.title.orEmpty(), onBack = onBack) {
                if (album != null) CollectionActions(album.songs)
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (album == null) {
            EmptyState(stringResource(R.string.empty_list), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomPadding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    Artwork(
                        uri = album.songs.first().artworkUri,
                        cornerRadius = 16.dp,
                        modifier = Modifier.size(220.dp),
                    )
                    Text(
                        text = album.title,
                        style = MaterialTheme.typography.headlineSmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 16.dp),
                    )
                    Text(
                        text = album.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .clickable { actions.openArtist(album.artist) }
                            .padding(4.dp),
                    )
                    Text(
                        text = listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            pluralStringResource(R.plurals.song_count, album.songs.size, album.songs.size),
                            formatDuration(album.durationMs),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlayShuffleButtons(album.songs, Modifier.padding(top = 16.dp))
                }
            }
            itemsIndexed(album.songs, key = { _, song -> song.id }) { index, song ->
                SongRow(
                    song = song,
                    onClick = { actions.play(album.songs, index) },
                    leading = SongLeading.TRACK_NUMBER,
                    showAlbum = false,
                )
            }
        }
    }
}

@Composable
fun ArtistScreen(artist: Artist?, onBack: () -> Unit, bottomPadding: PaddingValues) {
    val actions = LocalSongActions.current
    Scaffold(
        topBar = {
            BackTopBar(title = artist?.name.orEmpty(), onBack = onBack) {
                if (artist != null) CollectionActions(artist.songs)
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (artist == null) {
            EmptyState(stringResource(R.string.empty_list), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomPadding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = pluralStringResource(R.plurals.album_count, artist.albums.size, artist.albums.size) +
                            " · " + pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlayShuffleButtons(artist.songs, Modifier.padding(top = 12.dp))
                }
            }
            if (artist.albums.isNotEmpty()) {
                item {
                    SectionTitle(stringResource(R.string.tab_albums))
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(artist.albums, key = { it.id }) { album ->
                            AlbumCard(
                                album = album,
                                onClick = { actions.openAlbum(album.id) },
                                modifier = Modifier.width(140.dp),
                            )
                        }
                    }
                }
            }
            item { SectionTitle(stringResource(R.string.tab_songs)) }
            itemsIndexed(artist.songs, key = { _, song -> song.id }) { index, song ->
                SongRow(song = song, onClick = { actions.play(artist.songs, index) })
            }
        }
    }
}

@Composable
fun FolderScreen(folder: Folder?, onBack: () -> Unit, bottomPadding: PaddingValues) {
    val actions = LocalSongActions.current
    Scaffold(
        topBar = {
            BackTopBar(title = folder?.name.orEmpty(), onBack = onBack) {
                if (folder != null) CollectionActions(folder.songs)
            }
        },
        contentWindowInsets = WindowInsets(0),
    ) { padding ->
        if (folder == null) {
            EmptyState(stringResource(R.string.empty_list), Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = bottomPadding.calculateBottomPadding()),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = folder.path,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.song_count, folder.songs.size, folder.songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    PlayShuffleButtons(folder.songs, Modifier.padding(top = 12.dp))
                }
            }
            itemsIndexed(folder.songs, key = { _, song -> song.id }) { index, song ->
                SongRow(song = song, onClick = { actions.play(folder.songs, index) })
            }
        }
    }
}

@Composable
private fun CollectionActions(songs: List<Song>) {
    val actions = LocalSongActions.current
    IconButton(onClick = { actions.addToQueue(songs) }) {
        Icon(Icons.AutoMirrored.Rounded.QueueMusic, contentDescription = stringResource(R.string.add_to_queue))
    }
    IconButton(onClick = { actions.addToPlaylist(songs) }) {
        Icon(Icons.AutoMirrored.Rounded.PlaylistAdd, contentDescription = stringResource(R.string.add_to_playlist))
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 8.dp),
    )
}
