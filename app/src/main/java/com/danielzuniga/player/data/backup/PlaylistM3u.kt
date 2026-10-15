package com.danielzuniga.player.data.backup

import com.danielzuniga.player.data.Song
import java.net.URLDecoder

/**
 * A playlist as an M3U file, which almost every player reads and writes, so a playlist can leave
 * Player and come back. Written as UTF-8 with `#EXTINF` lines (`seconds,Artist - Title`) and each
 * song's path; read tolerating what other programs write: Windows line endings, a byte order
 * mark, `#EXTINF` with attributes, entries without any `#EXTINF` at all.
 *
 * A path means little on another phone, so reading goes by what the file says the song is: the
 * `#EXTINF` text, or else the file's name ("Artist - Title.mp3"); [candidates] lists what to try.
 */
object PlaylistM3u {

    /** One song of the file: what its `#EXTINF` said (length and text), and its path or URL. */
    data class Entry(val durationMs: Long, val text: String?, val path: String?)

    /** [name] comes from `#PLAYLIST:` when the file has it. */
    data class Parsed(val name: String?, val entries: List<Entry>)

    fun format(name: String, songs: List<Song>): String = buildString {
        append("#EXTM3U\n")
        append("#PLAYLIST:").append(oneLine(name)).append('\n')
        for (song in songs) {
            val label = if (song.artist.isBlank()) song.title else "${song.artist} - ${song.title}"
            append("#EXTINF:").append(song.durationMs / 1000).append(',').append(oneLine(label)).append('\n')
            // Without a path (MediaStore may not give one) the name still lets a reader find it.
            append(song.path.ifBlank { oneLine(label) }).append('\n')
        }
    }

    fun parse(text: String): Parsed {
        var name: String? = null
        val entries = mutableListOf<Entry>()
        var pending: Entry? = null
        for (raw in withoutByteOrderMark(text).split(LINE_BREAK)) {
            val line = raw.trim()
            when {
                line.isEmpty() || line.startsWith("#EXTM3U", ignoreCase = true) -> Unit
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    pending?.let(entries::add) // an #EXTINF that never got its path still names a song
                    pending = extinf(line.substring("#EXTINF:".length))
                }
                line.startsWith("#PLAYLIST:", ignoreCase = true) ->
                    name = line.substring("#PLAYLIST:".length).trim().ifEmpty { null }
                line.startsWith("#") -> Unit
                else -> {
                    entries += (pending ?: Entry(0, null, null)).copy(path = line)
                    pending = null
                }
            }
        }
        pending?.let(entries::add)
        return Parsed(name, entries)
    }

    /**
     * What to look the [entry] up as, most likely first. "Artist - Title" is the usual text, but a
     * title can hold " - " itself ("Song - Live"), so the whole text is tried as a title too. A
     * file name loses its folders, its extension and a leading track number ("03 - ").
     */
    fun candidates(entry: Entry): List<SongRef> {
        val refs = buildList {
            entry.text?.let { addAll(splits(it)) }
            entry.path?.let { path ->
                val stem = fileStem(path)
                val untracked = stem.replace(TRACK_NUMBER, "")
                addAll(splits(untracked))
                if (untracked != stem) addAll(splits(stem))
            }
        }
        return refs.map { (artist, title) -> SongRef(title, artist, "", entry.durationMs) }.distinct()
    }

    /** The name a playlist file suggests for the playlist: its own, without the extension. */
    fun nameFromFile(fileName: String): String =
        fileName.substringAfterLast('/').substringBeforeLast('.', fileName).trim()

    /** A file name safe to suggest in the file picker. */
    fun fileNameFor(playlistName: String): String =
        playlistName.replace(UNSAFE_IN_NAMES, "_").trim().ifEmpty { "playlist" } + ".m3u8"

    private fun extinf(rest: String): Entry {
        val comma = rest.indexOf(',')
        val head = if (comma < 0) rest else rest.substring(0, comma)
        val text = if (comma < 0) null else rest.substring(comma + 1).trim().ifEmpty { null }
        // "210" or "-1", maybe followed by attributes: tvg-id="x" group-title="y".
        val seconds = head.trim().substringBefore(' ').toDoubleOrNull() ?: 0.0
        return Entry(durationMs = if (seconds > 0) (seconds * 1000).toLong() else 0L, text = text, path = null)
    }

    /** (artist, title) readings of [text]: split at the first " - " if there is one, then as a whole title. */
    private fun splits(text: String): List<Pair<String, String>> {
        val clean = text.trim()
        if (clean.isEmpty()) return emptyList()
        val at = clean.indexOf(" - ")
        return if (at > 0) {
            listOf(clean.substring(0, at).trim() to clean.substring(at + 3).trim(), "" to clean)
        } else {
            listOf("" to clean)
        }.filter { (_, title) -> title.isNotEmpty() }
    }

    private fun fileStem(path: String): String {
        val last = path.substringAfterLast('/').substringAfterLast('\\')
        // file:// and http:// entries come percent-encoded ("Mi%20Canci%C3%B3n.mp3").
        val decoded = if (path.startsWith("file:") || path.startsWith("http")) {
            runCatching { URLDecoder.decode(last.replace("+", "%2B"), "UTF-8") }.getOrDefault(last)
        } else {
            last
        }
        return decoded.substringBeforeLast('.', decoded).trim()
    }

    /** Some editors start a UTF-8 file with U+FEFF; it isn't part of the first line. */
    private fun withoutByteOrderMark(text: String) =
        if (text.isNotEmpty() && text[0].code == BYTE_ORDER_MARK) text.substring(1) else text

    private fun oneLine(text: String) = text.replace(LINE_BREAK, " ")

    private const val BYTE_ORDER_MARK = 0xFEFF
    private val LINE_BREAK = Regex("\r\n|\r|\n")
    private val TRACK_NUMBER = Regex("""^\d{1,3}\s*[-.)]\s*|^\d{1,3}\s+""")
    private val UNSAFE_IN_NAMES = Regex("""[\\/:*?"<>|\u0000-\u001f]""")
}

/** A file that was supposed to be a playlist but has no songs in it, or isn't text at all. */
class NoPlaylistException : Exception()
