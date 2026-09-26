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

    /** Removes one song, keeping every other entry, visible in the library or not. */
    suspend fun removeFromPlaylist(id: Long, songId: Long) = playlists.editSongs(id) { ids -> ids - songId }

    /**
     * Applies a new order of the songs the screen shows. Songs hidden right now (on an unmounted
     * card, or shorter than the minimum length) keep their places instead of being dropped.
     */
    suspend fun reorderPlaylist(id: Long, visibleOrder: List<Long>) =
        playlists.editSongs(id) { stored -> mergeOrder(stored, visibleOrder) }

    suspend fun recordPlay(songId: Long) = stats.recordPlay(songId, System.currentTimeMillis())

    companion object {
        private const val SMART_LIMIT = 100

        /**
         * [stored] with its visible entries replaced, in place, by [visibleOrder]: the slots the
         * visible songs occupied get them in the new order, hidden songs stay where they were.
         */
        internal fun mergeOrder(stored: List<Long>, visibleOrder: List<Long>): List<Long> {
            val visible = visibleOrder.toHashSet()
            val next = visibleOrder.iterator()
            val merged = stored.map { id -> if (id in visible && next.hasNext()) next.next() else id }
            // Anything the screen had that the stored list didn't (shouldn't happen) goes last.
            return (merged + visibleOrder).distinct()
        }
    }
}
