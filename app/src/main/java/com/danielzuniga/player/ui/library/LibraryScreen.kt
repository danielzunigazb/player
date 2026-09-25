package com.danielzuniga.player.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.Surface
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.danielzuniga.player.R
import com.danielzuniga.player.data.Album
import com.danielzuniga.player.data.Artist
import com.danielzuniga.player.data.Folder
import com.danielzuniga.player.data.SongSort
import com.danielzuniga.player.ui.LibraryUiState
import com.danielzuniga.player.ui.PlaylistsUiState
import com.danielzuniga.player.ui.SmartPlaylist
import com.danielzuniga.player.ui.components.Artwork
import com.danielzuniga.player.ui.components.EmptyState
import com.danielzuniga.player.ui.components.LocalSongActions
import com.danielzuniga.player.ui.components.SongRow
import com.danielzuniga.player.ui.playlists.PlaylistsTab
import kotlinx.coroutines.launch

private enum class HomeTab(val label: Int) {
    SONGS(R.string.tab_songs),
    ALBUMS(R.string.tab_albums),
    ARTISTS(R.string.tab_artists),
    FOLDERS(R.string.tab_folders),
    PLAYLISTS(R.string.tab_playlists),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    library: LibraryUiState,
    playlists: PlaylistsUiState,
    onQueryChange: (String) -> Unit,
    onSortChange: (SongSort) -> Unit,
    onRefresh: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onOpenFolder: (String) -> Unit,
    onOpenPlaylist: (Long) -> Unit,
    onOpenSmartPlaylist: (SmartPlaylist) -> Unit,
    onCreatePlaylist: () -> Unit,
    onOpenSettings: () -> Unit,
    contentPadding: PaddingValues,
) {
    val pagerState = rememberPagerState { HomeTab.entries.size }
    val scope = rememberCoroutineScope()
    var searching by rememberSaveable { mutableStateOf(library.query.isNotEmpty()) }

    Column(modifier = Modifier.fillMaxSize()) {
        if (searching) {
            SearchBar(
                query = library.query,
                onQueryChange = onQueryChange,
                onClose = {
                    onQueryChange("")
                    searching = false
                },
            )
        } else {
            HomeHeader(
                library = library,
                showSort = pagerState.currentPage == HomeTab.SONGS.ordinal,
                onSearch = { searching = true },
                onSortChange = onSortChange,
                onOpenSettings = onOpenSettings,
            )
        }
        TabPills(
            selected = pagerState.currentPage,
            onSelect = { scope.launch { pagerState.animateScrollToPage(it) } },
        )

        PullToRefreshBox(
            isRefreshing = library.isScanning && library.hasScanned,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
                when (HomeTab.entries[page]) {
                    HomeTab.SONGS -> SongsTab(library, onOpenAlbum, contentPadding)
                    HomeTab.ALBUMS -> LibraryContent(library, library.albums.isEmpty()) {
                        AlbumGrid(library.albums, onOpenAlbum, contentPadding)
                    }
                    HomeTab.ARTISTS -> LibraryContent(library, library.artists.isEmpty()) {
                        ArtistList(library.artists, onOpenArtist, contentPadding)
                    }
                    HomeTab.FOLDERS -> LibraryContent(library, library.folders.isEmpty()) {
                        FolderList(library.folders, onOpenFolder, contentPadding)
                    }
                    HomeTab.PLAYLISTS -> PlaylistsTab(
                        state = playlists,
                        onOpenPlaylist = onOpenPlaylist,
                        onOpenSmartPlaylist = onOpenSmartPlaylist,
                        onCreatePlaylist = onCreatePlaylist,
                        contentPadding = contentPadding,
                    )
                }
            }
        }
    }
}

@Composable
private fun LibraryContent(
    library: LibraryUiState,
    isEmpty: Boolean,
    content: @Composable () -> Unit,
) {
    when {
        !library.hasScanned -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        library.totalSongs == 0 -> EmptyState(stringResource(R.string.empty_library))
        isEmpty && library.query.isNotBlank() ->
            EmptyState(stringResource(R.string.empty_search, library.query))
        else -> content()
    }
}

@Composable
private fun SongsTab(library: LibraryUiState, onOpenAlbum: (Long) -> Unit, contentPadding: PaddingValues) {
    val actions = LocalSongActions.current
    val recent = remember(library.albums) {
        library.albums.sortedByDescending { album -> album.songs.maxOf { it.dateAddedSec } }.take(12)
    }
    LibraryContent(library, library.songs.isEmpty()) {
        LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
            item(key = "hero") {
                ShuffleHero(count = library.songs.size, onClick = { actions.shuffle(library.songs) })
            }
            if (library.query.isBlank() && recent.size >= 2) {
                item(key = "recent") { RecentlyAdded(recent, onOpenAlbum) }
            }
            itemsIndexed(library.songs, key = { _, song -> song.id }) { index, song ->
                SongRow(song = song, onClick = { actions.play(library.songs, index) })
            }
        }
    }
}

@Composable
private fun AlbumGrid(albums: List<Album>, onOpenAlbum: (Long) -> Unit, contentPadding: PaddingValues) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 150.dp),
        contentPadding = PaddingValues(
            start = 12.dp,
            end = 12.dp,
            top = 12.dp,
            bottom = contentPadding.calculateBottomPadding() + 12.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(albums, key = { it.id }) { album ->
            AlbumCard(album = album, onClick = { onOpenAlbum(album.id) })
        }
    }
}

@Composable
fun AlbumCard(album: Album, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
    ) {
        Artwork(
            uri = album.songs.first().artworkUri,
            cornerRadius = 20.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f),
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp),
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
    }
}

@Composable
private fun ArtistList(artists: List<Artist>, onOpenArtist: (String) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(artists, key = { it.name }) { artist ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenArtist(artist.name) }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            ) {
                Artwork(
                    uri = artist.albums.firstOrNull()?.songs?.first()?.artworkUri,
                    cornerRadius = 28.dp,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = artist.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.album_count, artist.albums.size, artist.albums.size) +
                            " · " + pluralStringResource(R.plurals.song_count, artist.songs.size, artist.songs.size),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun FolderList(folders: List<Folder>, onOpenFolder: (String) -> Unit, contentPadding: PaddingValues) {
    LazyColumn(contentPadding = contentPadding, modifier = Modifier.fillMaxSize()) {
        items(folders, key = { it.path }) { folder ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onOpenFolder(folder.path) }
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            ) {
                Icon(
                    Icons.Rounded.Folder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
                Column(modifier = Modifier.padding(start = 16.dp)) {
                    Text(
                        text = folder.name,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = pluralStringResource(R.plurals.song_count, folder.songs.size, folder.songs.size) +
                            " · " + folder.path.substringBeforeLast('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun SortMenu(current: SongSort, onSortChange: (SongSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.AutoMirrored.Rounded.Sort, contentDescription = stringResource(R.string.sort))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SongSort.entries.forEach { sort ->
                DropdownMenuItem(
                    text = { Text(stringResource(sort.label())) },
                    leadingIcon = { RadioButton(selected = sort == current, onClick = null) },
                    onClick = {
                        onSortChange(sort)
                        open = false
                    },
                )
            }
        }
    }
}

private fun SongSort.label(): Int = when (this) {
    SongSort.TITLE -> R.string.sort_title
    SongSort.ARTIST -> R.string.sort_artist
    SongSort.ALBUM -> R.string.sort_album
    SongSort.RECENT -> R.string.sort_recent
    SongSort.DURATION -> R.string.sort_duration
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun SearchBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.close_search))
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(stringResource(R.string.search_hint)) },
                singleLine = true,
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
        },
    )
}

@Composable
private fun HomeHeader(
    library: LibraryUiState,
    showSort: Boolean,
    onSearch: () -> Unit,
    onSortChange: (SongSort) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val greeting = remember {
        when (java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)) {
            in 5..11 -> R.string.greeting_morning
            in 12..18 -> R.string.greeting_afternoon
            else -> R.string.greeting_night
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(greeting),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = stringResource(R.string.home_title),
                style = MaterialTheme.typography.displaySmall,
                maxLines = 1,
            )
        }
        HeaderButton(onClick = onSearch) {
            Icon(Icons.Rounded.Search, contentDescription = stringResource(R.string.search))
        }
        if (showSort) SortMenu(current = library.sort, onSortChange = onSortChange)
        HeaderButton(onClick = onOpenSettings) {
            Icon(Icons.Rounded.Settings, contentDescription = stringResource(R.string.settings))
        }
    }
}

@Composable
private fun HeaderButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(44.dp),
    ) {
        Box(contentAlignment = Alignment.Center) { content() }
    }
}

/** Pill-shaped section switcher; the selected pill fills with the accent colour. */
@Composable
private fun TabPills(selected: Int, onSelect: (Int) -> Unit) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        items(HomeTab.entries, key = { it.name }) { tab ->
            val isSelected = selected == tab.ordinal
            val container by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
                label = "pill",
            )
            val content by animateColorAsState(
                if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                label = "pillText",
            )
            Surface(
                onClick = { onSelect(tab.ordinal) },
                shape = CircleShape,
                color = container,
                contentColor = content,
                modifier = Modifier.semantics { this.selected = isSelected },
            ) {
                Text(
                    text = stringResource(tab.label),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/** Big gradient card that starts a shuffled session of the whole list. */
@Composable
private fun ShuffleHero(count: Int, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = MaterialTheme.shapes.large,
        color = Color.Transparent,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .background(Brush.linearGradient(listOf(colors.primary, colors.tertiary)))
                .padding(horizontal = 20.dp, vertical = 18.dp),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.shuffle_all),
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.onPrimary,
                )
                Text(
                    text = pluralStringResource(R.plurals.song_count, count, count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onPrimary.copy(alpha = 0.8f),
                )
            }
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(52.dp)
                    .clip(CircleShape)
                    .background(colors.onPrimary),
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null, tint = colors.primary)
            }
        }
    }
}

@Composable
private fun RecentlyAdded(albums: List<Album>, onOpenAlbum: (Long) -> Unit) {
    Column(modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)) {
        Text(
            text = stringResource(R.string.recently_added),
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album = album, onClick = { onOpenAlbum(album.id) }, modifier = Modifier.width(132.dp))
            }
        }
    }
}
