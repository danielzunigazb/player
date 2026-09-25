package com.danielzuniga.player.ui.library

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
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
import com.danielzuniga.player.ui.components.DzButton
import com.danielzuniga.player.ui.components.DzIconButton
import com.danielzuniga.player.ui.components.DzMark
import com.danielzuniga.player.ui.components.DzTitle
import com.danielzuniga.player.ui.components.Eyebrow
import com.danielzuniga.player.ui.components.Hairline
import com.danielzuniga.player.ui.theme.Dz
import com.danielzuniga.player.ui.theme.DzIcons
import com.danielzuniga.player.ui.theme.DzType
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
    Column(modifier = modifier.clickable(onClick = onClick)) {
        Artwork(
            uri = album.songs.first().artworkUri,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .border(1.dp, Dz.colors.line),
        )
        Text(
            text = album.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 8.dp),
        )
        Text(
            text = album.artist,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
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
                    modifier = Modifier
                        .size(56.dp)
                        .border(1.dp, Dz.colors.line),
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
                    DzIcons.Folder,
                    contentDescription = null,
                    tint = Dz.colors.inkMuted,
                    modifier = Modifier.size(32.dp),
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
        DzIconButton(DzIcons.Sort, stringResource(R.string.sort), { open = true })
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
                Icon(DzIcons.Back, contentDescription = stringResource(R.string.close_search))
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
                            Icon(DzIcons.Close, contentDescription = stringResource(R.string.clear_search))
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
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            DzMark(size = 28.dp)
            Spacer(Modifier.weight(1f))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DzIconButton(DzIcons.Search, stringResource(R.string.search), onSearch)
                if (showSort) SortMenu(current = library.sort, onSortChange = onSortChange)
                DzIconButton(DzIcons.Settings, stringResource(R.string.settings), onOpenSettings)
            }
        }
        Eyebrow(
            text = stringResource(greeting),
            modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
        )
        // The signature: mono title, last word as the gold whisper, plus the hero cursor.
        DzTitle(
            text = stringResource(R.string.home_title),
            whisper = stringResource(R.string.home_title_whisper),
            cursor = true,
            style = DzType.h1.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.035).em),
        )
    }
}

/** `DZ.Nav`-style section links: label type, ink-muted at rest, gold with a 1px underline when active. */
@Composable
private fun TabPills(selected: Int, onSelect: (Int) -> Unit) {
    Column {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.padding(top = 20.dp),
        ) {
            items(HomeTab.entries, key = { it.name }) { tab ->
                val isSelected = selected == tab.ordinal
                val color = if (isSelected) Dz.colors.gold else Dz.colors.inkMuted
                Column(
                    modifier = Modifier
                        .semantics { this.selected = isSelected }
                        .clickable { onSelect(tab.ordinal) }
                        .padding(vertical = 10.dp),
                ) {
                    Text(stringResource(tab.label).uppercase(), style = DzType.label, color = color)
                    Box(
                        Modifier
                            .padding(top = 6.dp)
                            .height(1.dp)
                            .width(24.dp)
                            .background(if (isSelected) Dz.colors.gold else Color.Transparent),
                    )
                }
            }
        }
        Hairline()
    }
}

/** The screen's one primary action, with the count as quiet metadata beside it. */
@Composable
private fun ShuffleHero(count: Int, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        DzButton(text = stringResource(R.string.shuffle_all), icon = DzIcons.Shuffle, onClick = onClick)
        Spacer(Modifier.weight(1f))
        Text(
            text = pluralStringResource(R.plurals.song_count, count, count),
            style = DzType.small,
            color = Dz.colors.inkMuted,
        )
    }
}

@Composable
private fun RecentlyAdded(albums: List<Album>, onOpenAlbum: (Long) -> Unit) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Eyebrow(
            text = "01 — " + stringResource(R.string.recently_added),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
        ) {
            items(albums, key = { it.id }) { album ->
                AlbumCard(album = album, onClick = { onOpenAlbum(album.id) }, modifier = Modifier.width(128.dp))
            }
        }
        Eyebrow(
            text = "02 — " + stringResource(R.string.tab_songs),
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp, bottom = 4.dp),
        )
    }
}
