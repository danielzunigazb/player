package com.danielzuniga.player.data.backup

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import com.danielzuniga.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CharacterCodingException
import java.nio.charset.CodingErrorAction

/**
 * One playlist as an M3U file the person picked ([PlaylistM3u]). Importing goes through the same
 * [BackupRepository] as a backup, so it has the same rules: it only adds, a playlist with that name
 * gets what it lacks, and what this phone doesn't have is counted, not lost.
 */
class PlaylistFiles(
    private val resolver: ContentResolver,
    private val repository: BackupRepository,
    private val library: () -> List<Song>,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    /** Writes [songs] as the playlist [name] to [uri], replacing what it had; returns how many. */
    suspend fun export(uri: Uri, name: String, songs: List<Song>): Int {
        val bytes = PlaylistM3u.format(name, songs).toByteArray(Charsets.UTF_8)
        withContext(Dispatchers.IO) {
            // "wt": truncate, so writing over a longer older file leaves none of its tail.
            val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("No stream for $uri")
            out.use { it.write(bytes) }
        }
        return songs.size
    }

    /** Adds the playlist in [uri] to what's here, or throws [NoPlaylistException] if it isn't one. */
    suspend fun import(uri: Uri): BackupRepository.ImportResult {
        val bytes = try {
            resolver.readLimited(uri, MAX_BYTES)
        } catch (_: BackupException) {
            throw NoPlaylistException() // too big to be a playlist
        }
        val text = decode(bytes) ?: throw NoPlaylistException()
        val parsed = PlaylistM3u.parse(text)
        if (parsed.entries.isEmpty()) throw NoPlaylistException()

        val matcher = SongMatcher(library())
        val refs = parsed.entries.mapNotNull { entry ->
            val candidates = PlaylistM3u.candidates(entry)
            // The first reading the library knows; if none, the likeliest, which then counts as missing.
            candidates.firstOrNull { matcher.find(it) != null } ?: candidates.firstOrNull()
        }
        if (refs.isEmpty()) throw NoPlaylistException()

        val name = parsed.name
            ?: displayName(uri)?.let(PlaylistM3u::nameFromFile)?.ifEmpty { null }
            ?: DEFAULT_NAME
        val playlist = Backup.Playlist(name = name, createdAt = 0, songs = refs)
        return repository.import(Backup(exportedAt = clock(), app = "", playlists = listOf(playlist)))
    }

    /** UTF-8 (`.m3u8`), or Latin-1 for the old `.m3u`; null for binary data, which no playlist is. */
    private fun decode(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: CharacterCodingException) {
            String(bytes, Charsets.ISO_8859_1)
        }
    }

    private fun displayName(uri: Uri): String? {
        val fromProvider = runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment
    }

    private companion object {
        /** A playlist of thousands of songs is a few hundred KB. */
        const val MAX_BYTES = 8 * 1024 * 1024
        const val DEFAULT_NAME = "Playlist"
    }
}
