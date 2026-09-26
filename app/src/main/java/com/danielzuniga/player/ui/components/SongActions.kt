package com.danielzuniga.player.ui.components

import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.danielzuniga.player.data.Song

/** Everything a song list can ask the app to do; provided once at the root. */
class SongActions(
    val play: (songs: List<Song>, index: Int) -> Unit,
    val shuffle: (songs: List<Song>) -> Unit,
    val playNext: (songs: List<Song>) -> Unit,
    val addToQueue: (songs: List<Song>) -> Unit,
    val addToPlaylist: (songs: List<Song>) -> Unit,
    val toggleFavorite: (song: Song) -> Unit,
    val openAlbum: (albumId: Long) -> Unit,
    val openArtist: (name: String) -> Unit,
    /** Undo the app's own artist/title fix for a song (see Song.tagsFixed). */
    val restoreTags: (song: Song) -> Unit = {},
    /** Share as a story image; with lines, as a lyrics card. */
    val share: (song: Song) -> Unit = {},
)

val LocalSongActions = staticCompositionLocalOf<SongActions> {
    error("SongActions not provided")
}

val LocalFavoriteIds = compositionLocalOf<Set<Long>> { emptySet() }

/** Id of the song currently loaded in the player, to highlight it in lists. */
val LocalCurrentSongId = compositionLocalOf<Long?> { null }

/** Whether the player is actually producing sound, to animate the "now playing" indicators. */
val LocalIsPlaying = compositionLocalOf { false }
