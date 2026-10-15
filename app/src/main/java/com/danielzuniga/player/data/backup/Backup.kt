package com.danielzuniga.player.data.backup

import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/**
 * Everything the user created, in a file that survives a new phone or a reinstall.
 *
 * Songs can't be saved by id: those are MediaStore's and change from one phone to the next. Each
 * one is saved as a [SongRef] (what a person would say to name it) and found again on import
 * (see [SongMatcher]). The file is plain JSON with one song written out wherever it is used, so
 * another tool can read or write it without knowing anything else.
 *
 * ```
 * { "format": "player-backup", "version": 1, "exportedAt": 1790000000000, "app": "1.6.1",
 *   "favorites": [ { "song": { "title": …, "artist": …, "album": …, "durationMs": … }, "addedAt": … } ],
 *   "playlists": [ { "name": …, "createdAt": …, "songs": [ { "title": …, … } ] } ],
 *   "stats":     [ { "song": { … }, "playCount": 12, "lastPlayedAt": … } ] }
 * ```
 */
data class Backup(
    val exportedAt: Long,
    val app: String,
    val favorites: List<Favorite> = emptyList(),
    val playlists: List<Playlist> = emptyList(),
    val stats: List<Stat> = emptyList(),
) {
    data class Favorite(val song: SongRef, val addedAt: Long)

    data class Playlist(val name: String, val createdAt: Long, val songs: List<SongRef>)

    data class Stat(val song: SongRef, val playCount: Int, val lastPlayedAt: Long)

    val isEmpty: Boolean get() = favorites.isEmpty() && playlists.isEmpty() && stats.isEmpty()

    fun toJson(): String = JSONObject()
        .put("format", FORMAT)
        .put("version", VERSION)
        .put("exportedAt", exportedAt)
        .put("app", app)
        .put("favorites", JSONArray().apply {
            favorites.forEach { put(JSONObject().put("song", it.song.toJson()).put("addedAt", it.addedAt)) }
        })
        .put("playlists", JSONArray().apply {
            playlists.forEach { playlist ->
                put(
                    JSONObject()
                        .put("name", playlist.name)
                        .put("createdAt", playlist.createdAt)
                        .put("songs", JSONArray().apply { playlist.songs.forEach { put(it.toJson()) } })
                )
            }
        })
        .put("stats", JSONArray().apply {
            stats.forEach {
                put(
                    JSONObject()
                        .put("song", it.song.toJson())
                        .put("playCount", it.playCount)
                        .put("lastPlayedAt", it.lastPlayedAt)
                )
            }
        })
        .toString(2)

    companion object {
        const val FORMAT = "player-backup"
        const val VERSION = 1

        /** The biggest file read: a heavy library's backup is a few MB; anything bigger isn't one. */
        const val MAX_BYTES = 24 * 1024 * 1024

        /**
         * The backup in [text], or a [BackupException] saying why it isn't one. Fields it doesn't
         * know are ignored, so a file from a later Player that only added things still reads.
         */
        fun parse(text: String): Backup {
            val root = try {
                JSONObject(text)
            } catch (_: JSONException) {
                throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            }
            if (root.optString("format") != FORMAT) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
            if (root.optInt("version", 0) > VERSION) throw BackupException(BackupException.Reason.TOO_NEW)
            return try {
                Backup(
                    exportedAt = root.optLong("exportedAt"),
                    app = root.optString("app"),
                    favorites = root.objects("favorites").map { Favorite(it.song("song"), it.optLong("addedAt")) },
                    playlists = root.objects("playlists").map { playlist ->
                        Playlist(
                            name = playlist.getString("name"),
                            createdAt = playlist.optLong("createdAt"),
                            songs = playlist.objects("songs").map(SongRef::fromJson),
                        )
                    },
                    stats = root.objects("stats").map {
                        Stat(it.song("song"), it.optInt("playCount"), it.optLong("lastPlayedAt"))
                    },
                )
            } catch (_: JSONException) {
                throw BackupException(BackupException.Reason.BROKEN)
            }
        }

        private fun JSONObject.objects(name: String): List<JSONObject> {
            val array = optJSONArray(name) ?: return emptyList()
            return (0 until array.length()).map { array.getJSONObject(it) }
        }

        private fun JSONObject.song(name: String): SongRef = SongRef.fromJson(getJSONObject(name))
    }
}

/** A song as a person names it: enough to find it again on another phone. */
data class SongRef(
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("title", title)
        .put("artist", artist)
        .put("album", album)
        .put("durationMs", durationMs)

    companion object {
        fun fromJson(json: JSONObject) = SongRef(
            title = json.getString("title"),
            artist = json.text("artist"),
            album = json.text("album"),
            durationMs = json.optLong("durationMs"),
        )

        /** Blank when missing or an explicit null: `optString` would give "null" for that. */
        private fun JSONObject.text(name: String): String = if (isNull(name)) "" else optString(name)
    }
}

/** Why a file isn't a backup this Player can use. */
class BackupException(val reason: Reason) : Exception(reason.name) {
    enum class Reason {
        /** Not JSON, or not one of Player's backups. */
        NOT_A_BACKUP,

        /** Made by a later Player, whose format this one doesn't know. */
        TOO_NEW,

        /** One of Player's, but with a missing or wrong field. */
        BROKEN,
    }
}
