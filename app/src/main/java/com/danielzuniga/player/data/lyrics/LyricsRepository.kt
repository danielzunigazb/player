package com.danielzuniga.player.data.lyrics

import android.content.Context
import android.util.LruCache
import com.danielzuniga.player.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

enum class LyricsSource { FILE, EMBEDDED, ONLINE }

data class FoundLyrics(val lyrics: Lyrics, val source: LyricsSource)

/**
 * Finds lyrics for a song, in order: a `.lrc` file next to it (when storage rules let us read
 * it), lyrics embedded in the audio file's tags, and finally LRCLIB when [onlineEnabled]
 * allows it. Online results are saved under `files/lyrics/` so each song is fetched once.
 */
class LyricsRepository(
    private val context: Context,
    private val onlineEnabled: () -> Boolean,
    private val client: LrcLibClient,
) {

    private val cache = LruCache<Long, Result>(64)
    private val onlineDir by lazy { File(context.filesDir, "lyrics").apply { mkdirs() } }

    private data class Result(val found: FoundLyrics?)

    suspend fun load(song: Song): FoundLyrics? {
        cache.get(song.id)?.let { return it.found }
        val (found, remember) = withContext(Dispatchers.IO) { resolve(song) }
        // Network failures aren't remembered, so the next play tries again.
        if (remember) cache.put(song.id, Result(found))
        return found
    }

    private fun resolve(song: Song): Pair<FoundLyrics?, Boolean> {
        sidecarLrc(song)?.let(LrcParser::parse)?.let { return FoundLyrics(it, LyricsSource.FILE) to true }
        embedded(song)?.let(LrcParser::parse)?.let { return FoundLyrics(it, LyricsSource.EMBEDDED) to true }
        if (!onlineEnabled()) return null to false

        val saved = File(onlineDir, "${song.id}.lrc")
        runCatching { saved.takeIf { it.canRead() }?.readText() }.getOrNull()
            ?.let(LrcParser::parse)
            ?.let { return FoundLyrics(it, LyricsSource.ONLINE) to true }

        return when (val result = client.find(song)) {
            is LrcLibClient.Result.Found -> {
                runCatching { saved.writeText(result.lrc) }
                LrcParser.parse(result.lrc)?.let { FoundLyrics(it, LyricsSource.ONLINE) } to true
            }
            LrcLibClient.Result.NotFound -> null to true
            LrcLibClient.Result.Failed -> null to false
        }
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
