package com.danielzuniga.player.data.backup

import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.TrackMatch
import kotlin.math.abs

/**
 * Finds, in this phone's [library], the song a [SongRef] names. The same rules as everywhere else
 * in Player ([TrackMatch]): accents, "feat." credits and "(Remastered)" tails don't matter, a
 * "Remix" or a "Live" is another song, and the lengths must be within a few seconds.
 *
 * Candidates come from an index by title, so a backup of thousands of songs against a library of
 * thousands isn't thousands × thousands comparisons. The index only narrows the search ([TrackMatch]
 * still decides), and it is by title as written, not by a title that merely contains the other:
 * this brings back one's own songs, it doesn't look for similar ones.
 */
class SongMatcher(library: List<Song>) {

    private val byTitle: Map<String, List<Song>> = buildMap<String, MutableList<Song>> {
        for (song in library) for (key in titleKeys(song.title)) getOrPut(key) { mutableListOf() } += song
    }

    /** The library's song for [ref], or null when this phone doesn't have it. */
    fun find(ref: SongRef): Song? {
        val candidates = titleKeys(ref.title).flatMap { byTitle[it].orEmpty() }.distinctBy { it.id }
        val sameTrack = candidates.filter {
            TrackMatch.sameTrack(ref.title, ref.artist, ref.durationMs, it.title, it.artist, it.durationMs)
        }
        // Two copies of a song (another folder, another album): the same album first, then the
        // length closest to the saved one, so the choice doesn't change from one import to the next.
        return sameTrack.minWithOrNull(
            compareBy<Song> { if (sameAlbum(ref, it)) 0 else 1 }
                .thenBy { abs(it.durationMs - ref.durationMs) }
                .thenBy { it.id }
        )
    }

    private fun sameAlbum(ref: SongRef, song: Song) =
        ref.album.isNotBlank() && TrackMatch.normalizeLooseTitle(ref.album) == TrackMatch.normalizeLooseTitle(song.album)

    private companion object {
        /**
         * The ways a title is looked up: as written without accents, case or "feat." credits, and
         * also without what doesn't change which song it is ("(Remastered 2011)", "- Radio Edit").
         * Titles of only symbols keep themselves.
         */
        fun titleKeys(title: String): Set<String> {
            val stripped = TrackMatch.stripTitleAnnotations(title)
            val exact = TrackMatch.normalizeLooseTitle(stripped)
            val core = TrackMatch.normalizeLooseTitle(TrackMatch.extractCoreTitle(TrackMatch.cleanTitle(stripped)))
            val raw = title.trim().lowercase()
            return setOf(exact.ifEmpty { raw }, core.ifEmpty { raw })
        }
    }
}
