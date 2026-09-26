package com.danielzuniga.player.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface FavoriteDao {
    @Query("SELECT songId FROM favorites ORDER BY addedAt DESC")
    fun observeIds(): Flow<List<Long>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(favorite: FavoriteEntity)

    @Query("DELETE FROM favorites WHERE songId = :songId")
    suspend fun delete(songId: Long)

    @Query("SELECT EXISTS(SELECT 1 FROM favorites WHERE songId = :songId)")
    suspend fun isFavorite(songId: Long): Boolean
}

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, COUNT(ps.songId) AS songCount
        FROM playlists p LEFT JOIN playlist_songs ps ON ps.playlistId = p.id
        GROUP BY p.id ORDER BY p.name COLLATE NOCASE
        """
    )
    fun observeSummaries(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun observePlaylist(id: Long): Flow<PlaylistEntity?>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    fun observeSongIds(playlistId: Long): Flow<List<Long>>

    @Query("SELECT songId FROM playlist_songs WHERE playlistId = :playlistId ORDER BY position")
    suspend fun songIds(playlistId: Long): List<Long>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity): Long

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun rename(id: Long, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylist(id: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongs(songs: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun clearSongs(playlistId: Long)

    @Transaction
    suspend fun appendSongs(playlistId: Long, songIds: List<Long>) {
        val existing = songIds(playlistId)
        val existingSet = existing.toHashSet()
        val newIds = songIds.distinct().filterNot { it in existingSet }
        insertSongs(
            newIds.mapIndexed { i, id -> PlaylistSongEntity(playlistId, id, existing.size + i) }
        )
    }

    @Transaction
    suspend fun replaceSongs(playlistId: Long, songIds: List<Long>) {
        clearSongs(playlistId)
        insertSongs(songIds.distinct().mapIndexed { i, id -> PlaylistSongEntity(playlistId, id, i) })
    }
}

@Dao
interface PlayStatDao {
    @Query("SELECT songId FROM play_stats ORDER BY playCount DESC, lastPlayedAt DESC LIMIT :limit")
    fun observeMostPlayed(limit: Int): Flow<List<Long>>

    @Query("SELECT songId FROM play_stats ORDER BY lastPlayedAt DESC LIMIT :limit")
    fun observeRecentlyPlayed(limit: Int): Flow<List<Long>>

    @Query("UPDATE play_stats SET playCount = playCount + 1, lastPlayedAt = :now WHERE songId = :songId")
    suspend fun increment(songId: Long, now: Long): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(stat: PlayStatEntity)

    // Upsert by hand: SQLite's ON CONFLICT DO UPDATE needs API 30+.
    @Transaction
    suspend fun recordPlay(songId: Long, now: Long) {
        if (increment(songId, now) == 0) insert(PlayStatEntity(songId, 1, now))
    }
}
