package com.danielzuniga.player.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.danielzuniga.player.AppContainer
import com.danielzuniga.player.PlayerApplication
import com.danielzuniga.player.data.Album
import com.danielzuniga.player.data.Artist
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.SongSort
import com.danielzuniga.player.data.ThemeMode
import com.danielzuniga.player.data.Folder
import com.danielzuniga.player.data.db.PlaylistSummary
import com.danielzuniga.player.data.lyrics.Lyrics
import com.danielzuniga.player.data.lyrics.LyricsSource
import com.danielzuniga.player.data.sortedBy
import com.danielzuniga.player.playback.EqualizerState
import com.danielzuniga.player.playback.LibraryBrowser
import com.danielzuniga.player.playback.PlayerConnection
import com.danielzuniga.player.playback.PlayerUiState
import com.danielzuniga.player.playback.QueueState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private fun <T> Flow<T>.stateIn(vm: ViewModel, initial: T): StateFlow<T> =
    stateIn(vm.viewModelScope, SharingStarted.WhileSubscribed(5_000), initial)

// ---------------------------------------------------------------- Player

data class LyricsUiState(
    val loading: Boolean = false,
    val lyrics: Lyrics? = null,
    val source: LyricsSource? = null,
)

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerViewModel(private val container: AppContainer) : ViewModel() {

    private val connection = PlayerConnection(container.appContext)

    val state: StateFlow<PlayerUiState> = connection.state
    val queue: StateFlow<QueueState> = connection.queue
    val favoriteIds: StateFlow<Set<Long>> = container.userData.favoriteIdSet.stateIn(this, emptySet())
    val mostPlayedIds: StateFlow<List<Long>> = container.userData.mostPlayedIds(limit = 5).stateIn(this, emptyList())

    val lyrics: StateFlow<LyricsUiState> = connection.state
        .map { it.nowPlaying?.songId }
        .combine(container.musicRepository.library) { id, library -> id?.let(library::song) }
        .distinctUntilChanged()
        .transformLatest { song ->
            if (song == null) {
                emit(LyricsUiState())
            } else {
                emit(LyricsUiState(loading = true))
                val found = container.lyrics.load(song)
                emit(LyricsUiState(lyrics = found?.lyrics, source = found?.source))
            }
        }
        .stateIn(this, LyricsUiState())

    init {
        viewModelScope.launch {
            while (isActive) {
                connection.refreshPosition()
                delay(POSITION_POLL_MS)
            }
        }
    }

    fun play(songs: List<Song>, index: Int) = connection.play(songs, index)

    fun shuffle(songs: List<Song>) {
        if (songs.isNotEmpty()) connection.play(songs, songs.indices.random(), shuffle = true)
    }

    /** Plays what a voice/search request asked for once the player and library are ready. */
    fun playFromSearch(query: String) {
        viewModelScope.launch {
            connection.isConnected.first { it }
            val results = LibraryBrowser.searchResults(query, container.musicRepository.awaitLibrary())
            if (results.isNotEmpty()) connection.play(results, 0)
        }
    }

    fun playNext(songs: List<Song>) = connection.playNext(songs)
    fun addToQueue(songs: List<Song>) = connection.addToQueue(songs)
    fun togglePlayPause() = connection.togglePlayPause()
    fun next() = connection.next()
    fun previous() = connection.previous()
    fun seekTo(positionMs: Long) = connection.seekTo(positionMs)
    fun toggleShuffle() = connection.toggleShuffle()
    fun cycleRepeatMode() = connection.cycleRepeatMode()
    fun setSpeed(speed: Float) = connection.setPlaybackSpeed(speed)
    fun setSleepTimer(minutes: Int) = connection.setSleepTimer(minutes)
    fun skipToQueueItem(index: Int) = connection.skipToQueueItem(index)
    fun removeQueueItem(index: Int) = connection.removeQueueItem(index)
    fun moveQueueItem(from: Int, to: Int) = connection.moveQueueItem(from, to)

    fun toggleFavorite(songId: Long) {
        viewModelScope.launch { container.userData.toggleFavorite(songId) }
    }

    override fun onCleared() = connection.release()

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}

// ---------------------------------------------------------------- Library

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val albums: List<Album> = emptyList(),
    val artists: List<Artist> = emptyList(),
    val folders: List<Folder> = emptyList(),
    val totalSongs: Int = 0,
    val query: String = "",
    val sort: SongSort = SongSort.TITLE,
    val isScanning: Boolean = false,
    val hasScanned: Boolean = false,
)

class LibraryViewModel(private val container: AppContainer) : ViewModel() {

    private val repository = container.musicRepository
    private val query = MutableStateFlow("")

    val library: StateFlow<LibraryIndex> = repository.library

    val state: StateFlow<LibraryUiState> = combine(
        repository.library,
        query,
        container.settings.songSort,
        repository.isScanning,
        repository.hasScanned,
    ) { library, q, sort, scanning, scanned ->
        LibraryUiState(
            songs = library.filterSongs(q).sortedBy(sort),
            albums = library.filterAlbums(q),
            artists = library.filterArtists(q),
            folders = library.filterFolders(q),
            totalSongs = library.songs.size,
            query = q,
            sort = sort,
            isScanning = scanning,
            hasScanned = scanned,
        )
    }.flowOn(Dispatchers.Default).stateIn(this, LibraryUiState())

    fun load(force: Boolean = false) = repository.load(force)
    fun onQueryChange(value: String) {
        query.value = value
    }
    fun setSort(sort: SongSort) = container.settings.setSongSort(sort)
}

// ---------------------------------------------------------------- Playlists

enum class SmartPlaylist { FAVORITES, MOST_PLAYED, RECENTLY_PLAYED, RECENTLY_ADDED }

data class PlaylistsUiState(
    val playlists: List<PlaylistSummary> = emptyList(),
    val smartCounts: Map<SmartPlaylist, Int> = emptyMap(),
)

class PlaylistsViewModel(private val container: AppContainer) : ViewModel() {

    private val userData = container.userData

    val state: StateFlow<PlaylistsUiState> = combine(
        userData.playlistSummaries,
        smartSongIds(container, SmartPlaylist.FAVORITES),
        smartSongIds(container, SmartPlaylist.MOST_PLAYED),
        smartSongIds(container, SmartPlaylist.RECENTLY_PLAYED),
        smartSongIds(container, SmartPlaylist.RECENTLY_ADDED),
    ) { playlists, favorites, most, recent, added ->
        PlaylistsUiState(
            playlists = playlists,
            smartCounts = mapOf(
                SmartPlaylist.FAVORITES to favorites.size,
                SmartPlaylist.MOST_PLAYED to most.size,
                SmartPlaylist.RECENTLY_PLAYED to recent.size,
                SmartPlaylist.RECENTLY_ADDED to added.size,
            ),
        )
    }.stateIn(this, PlaylistsUiState())

    fun create(name: String, songs: List<Song> = emptyList(), onCreated: (Long) -> Unit = {}) {
        viewModelScope.launch {
            onCreated(userData.createPlaylist(name, songs.map { it.id }))
        }
    }

    fun addTo(playlistId: Long, songs: List<Song>) {
        viewModelScope.launch { userData.addToPlaylist(playlistId, songs.map { it.id }) }
    }
}

/** Song ids of a smart playlist, restricted to songs still present on the device. */
private fun smartSongIds(container: AppContainer, smart: SmartPlaylist): Flow<List<Long>> {
    val library = container.musicRepository.library
    return when (smart) {
        SmartPlaylist.FAVORITES -> container.userData.favoriteIds
        SmartPlaylist.MOST_PLAYED -> container.userData.mostPlayedIds()
        SmartPlaylist.RECENTLY_PLAYED -> container.userData.recentlyPlayedIds()
        SmartPlaylist.RECENTLY_ADDED -> library.map { lib ->
            lib.songs.sortedByDescending { it.dateAddedSec }.take(RECENTLY_ADDED_LIMIT).map { it.id }
        }
    }.combine(library) { ids, lib -> ids.filter { lib.song(it) != null } }
}

private const val RECENTLY_ADDED_LIMIT = 100

// ---------------------------------------------------------------- Playlist detail

data class PlaylistDetailUiState(
    val title: String? = null,
    val smart: SmartPlaylist? = null,
    val playlistId: Long? = null,
    val songs: List<Song> = emptyList(),
    val loaded: Boolean = false,
)

class PlaylistDetailViewModel(
    private val container: AppContainer,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val kind: String = checkNotNull(savedStateHandle[Routes.ARG_KIND])
    private val id: Long = checkNotNull(savedStateHandle.get<Long>(Routes.ARG_ID))
    private val smart = SmartPlaylist.entries.firstOrNull { it.name == kind }
    private val userData = container.userData

    val state: StateFlow<PlaylistDetailUiState> =
        if (smart != null) {
            smartSongIds(container, smart).combine(container.musicRepository.library) { ids, lib ->
                PlaylistDetailUiState(smart = smart, songs = lib.songs(ids), loaded = true)
            }
        } else {
            combine(
                userData.playlist(id),
                userData.playlistSongIds(id),
                container.musicRepository.library,
            ) { playlist, ids, lib ->
                PlaylistDetailUiState(
                    title = playlist?.name,
                    playlistId = playlist?.id,
                    songs = lib.songs(ids),
                    loaded = true,
                )
            }
        }.stateIn(this, PlaylistDetailUiState())

    fun rename(name: String) {
        viewModelScope.launch { userData.renamePlaylist(id, name) }
    }

    // App scope: the screen pops right away, which would cancel viewModelScope mid-delete.
    fun delete() {
        container.appScope.launch { userData.deletePlaylist(id) }
    }

    fun remove(song: Song) {
        viewModelScope.launch { userData.removeFromPlaylist(id, song.id) }
    }

    fun saveOrder(songs: List<Song>) {
        viewModelScope.launch { userData.reorderPlaylist(id, songs.map { it.id }) }
    }
}

// ---------------------------------------------------------------- Equalizer & settings

class EqualizerViewModel(private val container: AppContainer) : ViewModel() {
    private val effects = container.audioEffects
    val state: StateFlow<EqualizerState> = effects.state

    fun setEnabled(enabled: Boolean) = effects.setEnabled(enabled)
    fun setBandLevel(band: Int, level: Int) = effects.setBandLevel(band, level)
    fun usePreset(index: Int) = effects.usePreset(index)
    fun setBassStrength(strength: Int) = effects.setBassStrength(strength)
}

data class SettingsUiState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = false,
    val onlineLyrics: Boolean = true,
    val minDurationSec: Int = 10,
    val onlineTags: Boolean = true,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val settings = container.settings

    val state: StateFlow<SettingsUiState> = combine(
        settings.themeMode,
        settings.dynamicColor,
        settings.onlineLyrics,
        settings.minDurationSec,
        settings.onlineTags,
    ) { theme, dynamic, online, minDuration, tags -> SettingsUiState(theme, dynamic, online, minDuration, tags) }
        .stateIn(
            this,
            SettingsUiState(
                settings.themeMode.value,
                settings.dynamicColor.value,
                settings.onlineLyrics.value,
                settings.minDurationSec.value,
                settings.onlineTags.value,
            ),
        )

    fun setThemeMode(mode: ThemeMode) = settings.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = settings.setDynamicColor(enabled)
    fun setOnlineLyrics(enabled: Boolean) = settings.setOnlineLyrics(enabled)
    fun setMinDuration(seconds: Int) = settings.setMinDurationSec(seconds)
    fun setOnlineTags(enabled: Boolean) = settings.setOnlineTags(enabled)
    fun rescan() = container.musicRepository.load(force = true)
}

// ---------------------------------------------------------------- Factory

object AppViewModels {
    val Factory: ViewModelProvider.Factory = viewModelFactory {
        initializer { PlayerViewModel(container()) }
        initializer { LibraryViewModel(container()) }
        initializer { PlaylistsViewModel(container()) }
        initializer { PlaylistDetailViewModel(container(), createSavedStateHandle()) }
        initializer { EqualizerViewModel(container()) }
        initializer { SettingsViewModel(container()) }
    }

    private fun CreationExtras.container(): AppContainer =
        (checkNotNull(this[APPLICATION_KEY]) as PlayerApplication).container
}
