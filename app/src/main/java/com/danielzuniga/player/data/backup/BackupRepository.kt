package com.danielzuniga.player.data.backup

import androidx.room.withTransaction
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.db.AppDatabase
import com.danielzuniga.player.data.db.FavoriteEntity
import com.danielzuniga.player.data.db.PlayStatEntity
import com.danielzuniga.player.data.db.PlaylistEntity
import kotlin.math.max

/**
 * Saves favorites, playlists and listening history to a [Backup] and puts one back.
 *
 * Importing only adds, and importing the same file again changes nothing: favorites already there
 * stay as they are, a playlist with the same name gets the songs it lacks at the end, and a
 * song's play count and last play keep the higher of the two. Nothing is ever removed or replaced.
 */
class BackupRepository(
    private val db: AppDatabase,
    private val library: () -> LibraryIndex,
    private val appVersion: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val favorites = db.favoriteDao()
    private val playlists = db.playlistDao()
    private val stats = db.playStatDao()

    /** The [backup] to write, and how many saved songs it had to leave out. */
    data class Export(val backup: Backup, val skipped: Int)

    /** What an import did, for telling the user; all zeros means everything was already there. */
    data class ImportResult(
        val favoritesAdded: Int = 0,
        val playlistsCreated: Int = 0,
        val playlistsUpdated: Int = 0,
        val songsAdded: Int = 0,
        val statsUpdated: Int = 0,
        /** Different songs in the file that this phone's library doesn't have. */
        val missing: Int = 0,
    ) {
        val changed: Boolean
            get() = favoritesAdded + playlistsCreated + playlistsUpdated + songsAdded + statsUpdated > 0
    }

    /**
     * Everything saved right now. A song that isn't in the library at this moment (an SD card
     * that's out, or shorter than the minimum length) has no title to save under: it's left out
     * and counted in [Export.skipped] rather than written as something unreadable.
     */
    suspend fun export(): Export {
        val index = library()
        val skipped = HashSet<Long>()
        fun ref(songId: Long): SongRef? = index.song(songId)?.toRef().also { if (it == null) skipped += songId }

        val backup = db.withTransaction {
            val songIdsByPlaylist = playlists.allSongs().groupBy({ it.playlistId }, { it.songId })
            Backup(
                exportedAt = clock(),
                app = appVersion,
                favorites = favorites.all().mapNotNull { fav -> ref(fav.songId)?.let { Backup.Favorite(it, fav.addedAt) } },
                playlists = playlists.all().map { playlist ->
                    Backup.Playlist(
                        name = playlist.name,
                        createdAt = playlist.createdAt,
                        songs = songIdsByPlaylist[playlist.id].orEmpty().mapNotNull(::ref),
                    )
                },
                stats = stats.all().mapNotNull { stat ->
                    ref(stat.songId)?.let { Backup.Stat(it, stat.playCount, stat.lastPlayedAt) }
                },
            )
        }
        return Export(backup, skipped.size)
    }

    /** Adds what [backup] holds to what's here, all or nothing. */
    suspend fun import(backup: Backup): ImportResult {
        val matcher = SongMatcher(library().songs)
        // Null is a result too: a song this phone lacks is looked for once, however often it appears.
        val found = HashMap<SongRef, Song?>()
        fun find(ref: SongRef): Song? = if (ref in found) found[ref] else matcher.find(ref).also { found[ref] = it }

        return db.withTransaction {
            var favoritesAdded = 0
            var playlistsCreated = 0
            var playlistsUpdated = 0
            var songsAdded = 0
            var statsUpdated = 0

            val known = favorites.all().mapTo(HashSet()) { it.songId }
            for (fav in backup.favorites) {
                val song = find(fav.song) ?: continue
                if (!known.add(song.id)) continue
                favorites.insert(FavoriteEntity(song.id, fav.addedAt.takeIf { it > 0 } ?: clock()))
                favoritesAdded++
            }

            for (playlist in backup.playlists) {
                val name = playlist.name.trim().ifEmpty { DEFAULT_PLAYLIST_NAME }
                val ids = playlist.songs.mapNotNull(::find).map { it.id }.distinct()
                val existing = playlists.idByName(name)
                if (existing == null) {
                    // Nothing of it is on this phone: an empty playlist would only be clutter.
                    if (ids.isEmpty() && playlist.songs.isNotEmpty()) continue
                    val id = playlists.insertPlaylist(
                        PlaylistEntity(name = name, createdAt = playlist.createdAt.takeIf { it > 0 } ?: clock())
                    )
                    playlists.appendSongs(id, ids)
                    playlistsCreated++
                    songsAdded += ids.size
                } else {
                    val present = playlists.songIds(existing).toHashSet()
                    val added = ids.count { it !in present }
                    if (added == 0) continue
                    playlists.appendSongs(existing, ids)
                    playlistsUpdated++
                    songsAdded += added
                }
            }

            for (stat in backup.stats) {
                // No plays is nothing to restore: it would only list the song as "listened to".
                if (stat.playCount <= 0) continue
                val song = find(stat.song) ?: continue
                val current = stats.get(song.id)
                val merged = PlayStatEntity(
                    songId = song.id,
                    playCount = max(current?.playCount ?: 0, stat.playCount),
                    lastPlayedAt = max(current?.lastPlayedAt ?: 0L, stat.lastPlayedAt.coerceAtLeast(0L)),
                )
                if (merged == current) continue
                stats.upsert(merged)
                statsUpdated++
            }

            ImportResult(
                favoritesAdded = favoritesAdded,
                playlistsCreated = playlistsCreated,
                playlistsUpdated = playlistsUpdated,
                songsAdded = songsAdded,
                statsUpdated = statsUpdated,
                missing = found.values.count { it == null },
            )
        }
    }

    private fun Song.toRef() = SongRef(title = title, artist = artist, album = album, durationMs = durationMs)

    private companion object {
        const val DEFAULT_PLAYLIST_NAME = "Playlist"
    }
}
