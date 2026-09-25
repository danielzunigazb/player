package com.danielzuniga.player.data

import com.danielzuniga.player.data.db.AppDatabase
import com.danielzuniga.player.data.db.FavoriteEntity
import com.danielzuniga.player.data.db.PlaylistEntity
import com.danielzuniga.player.data.db.PlaylistSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Everything the user creates: favorites, playlists and listening history. */
class UserDataRepository(private val db: AppDatabase) {

    private val favorites = db.favoriteDao()
    private val playlists = db.playlistDao()
    private val stats = db.playStatDao()

    val favoriteIds: Flow<List<Long>> = favorites.observeIds()
    val favoriteIdSet: Flow<Set<Long>> = favoriteIds.map { it.toSet() }

    val playlistSummaries: Flow<List<PlaylistSummary>> = playlists.observeSummaries()

    fun mostPlayedIds(limit: Int = SMART_LIMIT): Flow<List<Long>> = stats.observeMostPlayed(limit)

    fun recentlyPlayedIds(limit: Int = SMART_LIMIT): Flow<List<Long>> = stats.observeRecentlyPlayed(limit)

    fun playlist(id: Long): Flow<PlaylistEntity?> = playlists.observePlaylist(id)

    fun playlistSongIds(id: Long): Flow<List<Long>> = playlists.observeSongIds(id)

    suspend fun toggleFavorite(songId: Long) {
        if (favorites.isFavorite(songId)) {
            favorites.delete(songId)
        } else {
            favorites.insert(FavoriteEntity(songId, System.currentTimeMillis()))
        }
    }

    suspend fun createPlaylist(name: String, songIds: List<Long> = emptyList()): Long {
        val id = playlists.insertPlaylist(
            PlaylistEntity(name = name.trim(), createdAt = System.currentTimeMillis())
        )
        if (songIds.isNotEmpty()) playlists.appendSongs(id, songIds)
        return id
    }

    suspend fun renamePlaylist(id: Long, name: String) = playlists.rename(id, name.trim())

    suspend fun deletePlaylist(id: Long) = playlists.deletePlaylist(id)

    suspend fun addToPlaylist(id: Long, songIds: List<Long>) = playlists.appendSongs(id, songIds)

    suspend fun setPlaylistOrder(id: Long, songIds: List<Long>) = playlists.replaceSongs(id, songIds)

    suspend fun recordPlay(songId: Long) = stats.recordPlay(songId, System.currentTimeMillis())

    private companion object {
        const val SMART_LIMIT = 100
    }
}
