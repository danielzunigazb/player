package com.danielzuniga.player.data.backup

import android.content.ContentResolver
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException

/**
 * The backup as a file the person picked (the system's file picker gives a [Uri], not a path).
 * Reading and writing happen off the main thread.
 */
class BackupFiles(
    private val resolver: ContentResolver,
    private val repository: BackupRepository,
) {
    /** What was written, for telling the user. */
    data class Written(val favorites: Int, val playlists: Int, val songs: Int, val skipped: Int)

    /** Writes everything saved to [uri], replacing what it had. */
    suspend fun export(uri: Uri): Written {
        val export = repository.export()
        val bytes = export.backup.toJson().toByteArray(Charsets.UTF_8)
        withContext(Dispatchers.IO) {
            // "wt": truncate, so writing over an older, longer backup doesn't leave its tail behind.
            val out = resolver.openOutputStream(uri, "wt") ?: throw IOException("No stream for $uri")
            out.use { it.write(bytes) }
        }
        val backup = export.backup
        return Written(
            favorites = backup.favorites.size,
            playlists = backup.playlists.size,
            songs = backup.playlists.sumOf { it.songs.size },
            skipped = export.skipped,
        )
    }

    /** Reads the backup in [uri] and adds it to what's here. */
    suspend fun import(uri: Uri): BackupRepository.ImportResult = repository.import(read(uri))

    /** The backup in [uri], or a [BackupException]: never reads more than [Backup.MAX_BYTES]. */
    suspend fun read(uri: Uri): Backup {
        val text = withContext(Dispatchers.IO) {
            val input = resolver.openInputStream(uri) ?: throw IOException("No stream for $uri")
            input.use { stream ->
                // Stops as soon as it's over the limit, without holding the rest of a huge file.
                val bytes = ByteArrayOutputStream()
                val buffer = ByteArray(BUFFER)
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) break
                    if (bytes.size() + read > Backup.MAX_BYTES) throw BackupException(BackupException.Reason.NOT_A_BACKUP)
                    bytes.write(buffer, 0, read)
                }
                bytes.toString(Charsets.UTF_8.name())
            }
        }
        return Backup.parse(text)
    }

    private companion object {
        const val BUFFER = 64 * 1024
    }
}
