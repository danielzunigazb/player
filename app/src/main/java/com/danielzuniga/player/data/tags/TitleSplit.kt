package com.danielzuniga.player.data.tags

import com.danielzuniga.player.data.normalizedForSearch

/**
 * Reads artist and title out of the kind of title downloaded files end up with when they have
 * no tags: the file name, e.g. "01. 24K - T3R Elemento (Official Video) [320kbps]".
 *
 * The order around the dash isn't reliable (both "Artist - Song" and "Song - Artist" are common),
 * so this only proposes readings; [TagFixRepository] keeps the one a catalogue confirms.
 */
object TitleSplit {

    data class Reading(val artist: String, val title: String)

    private val SEPARATOR = Regex("""\s+[-–—~]\s+""")
    /** "01. ", "01) ", "01 - ": not "1-800" or "24K". */
    private val LEADING_TRACK_NUMBER = Regex("""^\s*\d{1,3}(?:[.)]|\s+-)\s+""")
    /** Bracketed tags that describe the upload, not the song. */
    private val NOISE = Regex(
        """\b(official|oficial|video|vídeo|videoclip|audio|lyrics?|letra|visuali[sz]er|hd|hq|4k|\d*\s?kbps|mp3|m4a|flac|""" +
            """free download|download|descarga|estreno|premiere)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val BRACKETED = Regex("""\s*[(\[【]([^)\]】]*)[)\]】]""")
    private val SPACES = Regex("""\s+""")

    /** Title with the download noise removed: track numbers, "(Official Video)", "[320kbps]", underscores. */
    fun clean(raw: String): String {
        var text = raw.replace('_', ' ').replace(SPACES, " ").trim()
        text = BRACKETED.replace(text) { m ->
            if (NOISE.containsMatchIn(m.groupValues[1])) "" else m.value
        }
        text = text.replace(LEADING_TRACK_NUMBER, "")
        return text.replace(SPACES, " ").trim()
    }

    /**
     * Possible (artist, title) readings, most likely first. Empty when the title has no
     * separator: guessing where a name ends without one would be wrong too often.
     */
    fun readings(rawTitle: String): List<Reading> {
        val text = clean(rawTitle)
        val match = SEPARATOR.find(text) ?: return emptyList()
        val left = text.substring(0, match.range.first).trim()
        val right = text.substring(match.range.last + 1).trim()
        if (left.isEmpty() || right.isEmpty()) return emptyList()
        return listOf(Reading(artist = left, title = right), Reading(artist = right, title = left))
    }

    /** "Soda Stereo - De Música Ligera" tagged as Soda Stereo: the title repeats the artist. */
    fun withoutArtistPrefix(title: String, artist: String): String? {
        val match = SEPARATOR.find(title) ?: return null
        val prefix = title.substring(0, match.range.first)
        if (prefix.normalizedForSearch() != artist.normalizedForSearch()) return null
        return title.substring(match.range.last + 1).trim().ifEmpty { null }
    }
}
