package com.danielzuniga.player.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.danielzuniga.player.data.MusicRepository
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.filterSongs
import com.danielzuniga.player.playback.PlayerConnection
import com.danielzuniga.player.playback.PlayerUiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class LibraryUiState(
    val songs: List<Song> = emptyList(),
    val totalCount: Int = 0,
    val query: String = "",
    val isLoading: Boolean = false,
    val hasLoaded: Boolean = false,
)

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository(application)
    private val player = PlayerConnection(application)

    private val allSongs = MutableStateFlow<List<Song>>(emptyList())
    private val query = MutableStateFlow("")
    private val isLoading = MutableStateFlow(false)
    private val hasLoaded = MutableStateFlow(false)

    val libraryState: StateFlow<LibraryUiState> =
        combine(allSongs, query, isLoading, hasLoaded) { songs, q, loading, loaded ->
            LibraryUiState(
                songs = filterSongs(songs, q),
                totalCount = songs.size,
                query = q,
                isLoading = loading,
                hasLoaded = loaded,
            )
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LibraryUiState())

    val playerState: StateFlow<PlayerUiState> = player.state

    init {
        viewModelScope.launch {
            while (isActive) {
                player.refreshPosition()
                delay(POSITION_POLL_MS)
            }
        }
    }

    fun loadLibrary(force: Boolean = false) {
        if (isLoading.value || (hasLoaded.value && !force)) return
        viewModelScope.launch {
            isLoading.value = true
            allSongs.value = repository.loadSongs()
            hasLoaded.value = true
            isLoading.value = false
        }
    }

    fun onQueryChange(newQuery: String) {
        query.value = newQuery
    }

    fun playFromList(index: Int) {
        player.play(libraryState.value.songs, index)
    }

    fun shuffleAll() {
        val songs = libraryState.value.songs
        if (songs.isEmpty()) return
        player.play(songs, songs.indices.random(), shuffle = true)
    }

    fun togglePlayPause() = player.togglePlayPause()
    fun next() = player.next()
    fun previous() = player.previous()
    fun seekTo(positionMs: Long) = player.seekTo(positionMs)
    fun toggleShuffle() = player.toggleShuffle()
    fun cycleRepeatMode() = player.cycleRepeatMode()

    override fun onCleared() {
        player.release()
    }

    private companion object {
        const val POSITION_POLL_MS = 500L
    }
}
