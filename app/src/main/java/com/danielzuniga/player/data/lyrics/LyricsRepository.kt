package com.danielzuniga.player.data.lyrics

import android.content.Context
import android.util.LruCache
import com.danielzuniga.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Finds lyrics for a song: a `.lrc` file next to it (when storage rules let us read it),
 * then lyrics embedded in the audio file's tags.
 */
class LyricsRepository(private val context: Context) {

    private val cache = LruCache<Long, Result>(64)

    private data class Result(val lyrics: Lyrics?)

    suspend fun load(song: Song): Lyrics? {
        cache.get(song.id)?.let { return it.lyrics }
        val lyrics = withContext(Dispatchers.IO) {
            (sidecarLrc(song) ?: embedded(song))?.let(LrcParser::parse)
        }
        cache.put(song.id, Result(lyrics))
        return lyrics
    }

    private fun sidecarLrc(song: Song): String? {
        if (song.path.isBlank()) return null
        val base = song.path.substringBeforeLast('.')
        return listOf("$base.lrc", "$base.LRC").firstNotNullOfOrNull { candidate ->
            runCatching { File(candidate).takeIf { it.canRead() }?.readText() }.getOrNull()
        }
    }

    private fun embedded(song: Song): String? = runCatching {
        context.contentResolver.openInputStream(song.uri)?.use(EmbeddedLyrics::read)
    }.getOrNull()
}
