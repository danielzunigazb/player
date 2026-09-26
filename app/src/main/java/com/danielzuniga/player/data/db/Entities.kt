package com.danielzuniga.player.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "favorites")
data class FavoriteEntity(
    @PrimaryKey val songId: Long,
    val addedAt: Long,
)

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long,
)

@Entity(
    tableName = "playlist_songs",
    primaryKeys = ["playlistId", "songId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("playlistId")],
)
data class PlaylistSongEntity(
    val playlistId: Long,
    val songId: Long,
    val position: Int,
)

@Entity(tableName = "play_stats")
data class PlayStatEntity(
    @PrimaryKey val songId: Long,
    val playCount: Int,
    val lastPlayedAt: Long,
)

/**
 * What the app learned about a song whose file has no usable tags. [status] is one of
 * [TagFixEntity.FIXED] (use [title] and [artist]), [TagFixEntity.NO_MATCH] (nothing confirmed,
 * don't ask again) or [TagFixEntity.RESTORED] (the user wants the file's own tags back).
 */
@Entity(tableName = "tag_fixes")
data class TagFixEntity(
    @PrimaryKey val songId: Long,
    val status: String,
    val title: String?,
    val artist: String?,
    val checkedAt: Long,
) {
    companion object {
        const val FIXED = "fixed"
        const val NO_MATCH = "no_match"
        const val RESTORED = "restored"
    }
}

data class PlaylistSummary(
    val id: Long,
    val name: String,
    val songCount: Int,
)
