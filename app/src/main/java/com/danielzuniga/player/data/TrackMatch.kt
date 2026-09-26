package com.danielzuniga.player.data

import java.text.Normalizer
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Decides whether a track found in an external catalogue (e.g. an LRCLIB search result) is
 * the same recording as one in the library. Titles and artist credits are compared loosely —
 * accents, punctuation, "(Remastered)" tails and feat. credits don't matter — while version
 * words like "remix" or "live" still keep different recordings apart.
 *
 * Matching rules adapted from the track-identification logic of the SpotiFLAC TIDAL
 * extension (only the matching; nothing that talks to TIDAL).
 */
object TrackMatch {

    /** Durations further apart than this belong to different recordings. */
    const val DURATION_TOLERANCE_SEC = 10L

    private val COMBINING_MARKS = Regex("\\p{Mn}+")
    private val D_STROKE = Regex("[đĐ]")
    private val SHARP_S = Regex("[ßẞ]")
    private val AE = Regex("[æÆ]")
    private val OE = Regex("[œŒ]")
    private val NON_WORD = Regex("[^\\w\\s]+")
    private val SPACES = Regex("\\s+")
    private val LOOSE_PUNCTUATION = Regex("[/\\\\_\\-|.&+]")
    private val TRAILING_PARENS = Regex("\\s*\\([^)]*\\)\\s*$")
    private val TRAILING_BRACKETS = Regex("\\s*\\[[^\\]]*]\\s*$")
    private val DASH_SUFFIX = Regex("\\s+-\\s+.*$")
    private val BRACKETED = Regex("\\(([^)]*)\\)|\\[([^\\]]*)]")
    private val NON_LATIN = Regex("[^\\u0000-\\u024f]")
    private val ALPHANUMERIC = Regex("[a-z0-9]", RegexOption.IGNORE_CASE)
    private val ASCII_AND_PUNCTUATION = Regex("""[a-z0-9\s!"#$%&'()*+,\-./:;<=>?@\[\\\]^_`{|}~]""", RegexOption.IGNORE_CASE)
    private val FEAT_OR_FROM_ANNOTATION = Regex(
        """[(\[]\s*(?:(?:feat\.?|ft\.?|featuring)\s+[^)\]]+|from\s+["“][^)\]]+["”]\s*)[)\]]""",
        RegexOption.IGNORE_CASE,
    )

    /** Words that name a different recording, so a match can't be assumed from containment alone. */
    private val VERSION_WORDS = Regex("""\b(?:mix|remix|live|acoustic|demo|instrumental|karaoke|edit|extended|slowed|sped)\b""")

    /** Bracketed tails that don't change the recording's identity when comparing. */
    private val DECORATIONS = listOf(
        "remaster", "remastered", "deluxe", "bonus", "single", "album version", "radio edit",
        "original mix", "extended", "club mix", "remix", "live", "acoustic", "demo",
    )

    private val ARTIST_JOINERS = listOf(
        Regex("\\bfeat\\b"), Regex("\\bfeaturing\\b"), Regex("\\bft\\b"), Regex("\\band\\b"),
        Regex("[,&;]"), Regex("\\bx\\b"),
    )

    // ---------------------------------------------------------------- public API

    /** Same recording by title, artist and length. Blank/unknown fields are not held against a match. */
    fun sameTrack(
        expectedTitle: String,
        expectedArtist: String,
        expectedDurationMs: Long,
        foundTitle: String,
        foundArtist: String,
        foundDurationMs: Long,
    ): Boolean =
        trackTitlesMatch(expectedTitle, foundTitle) &&
            (expectedArtist.isBlank() || foundArtist.isBlank() || artistNamesMatch(expectedArtist, foundArtist)) &&
            durationMatches(expectedDurationMs, foundDurationMs)

    /** Title match that refuses to equate different versions ("Song" vs "Song (Remix)"). */
    fun trackTitlesMatch(expected: String, found: String): Boolean {
        val a = normalizeLooseTitle(stripTitleAnnotations(expected))
        val b = normalizeLooseTitle(stripTitleAnnotations(found))
        if (a.isNotEmpty() && a == b) return true
        // Version words identify recordings; punctuation around them does not.
        if (VERSION_WORDS.containsMatchIn(a) || VERSION_WORDS.containsMatchIn(b)) return false
        return titlesMatch(stripTitleAnnotations(expected), stripTitleAnnotations(found))
    }

    /** Loose title match: normalized equality or containment, then with decorations removed. */
    fun titlesMatch(expected: String, found: String): Boolean {
        val rawA = expected.trim()
        val rawB = found.trim()
        val a = normalizeSearchText(rawA)
        val b = normalizeSearchText(rawB)
        if (a.isNotEmpty() && b.isNotEmpty()) {
            if (a == b || a.contains(b) || b.contains(a)) return true

            val cleanA = cleanTitle(a)
            val cleanB = cleanTitle(b)
            if (cleanA.isNotEmpty() && cleanB.isNotEmpty() &&
                (cleanA == cleanB || cleanA.contains(cleanB) || cleanB.contains(cleanA))
            ) {
                return true
            }

            val coreA = extractCoreTitle(a)
            val coreB = extractCoreTitle(b)
            if (coreA.isNotEmpty() && coreA == coreB) return true

            val looseA = normalizeLooseTitle(rawA)
            val looseB = normalizeLooseTitle(rawB)
            if (looseA.isNotEmpty() && looseB.isNotEmpty() &&
                (looseA == looseB || looseA.contains(looseB) || looseB.contains(looseA))
            ) {
                return true
            }
        }

        // Titles made only of symbols ("÷", "★") compare by those symbols.
        if (rawA.isNotEmpty() && rawB.isNotEmpty() && (!hasAlphaNumeric(rawA) || !hasAlphaNumeric(rawB))) {
            val symbolsA = normalizeSymbolOnlyTitle(rawA)
            val symbolsB = normalizeSymbolOnlyTitle(rawB)
            return symbolsA.isNotEmpty() && symbolsA == symbolsB
        }

        // A title in another script (e.g. Japanese vs romanised) can't be compared letter by letter.
        return isLatinScript(rawA) != isLatinScript(rawB)
    }

    /** Artist credits match when any credited artist on one side is on the other. */
    fun artistNamesMatch(expected: String, found: String): Boolean {
        val a = normalizeSearchText(expected)
        val b = normalizeSearchText(found)
        if (a.isNotEmpty() && b.isNotEmpty()) {
            if (a == b || containmentMatch(a, b)) return true
            val aParts = splitArtists(expected)
            val bParts = splitArtists(found)
            for (x in aParts) for (y in bParts) {
                if (x == y || containmentMatch(x, y) || sameWordsUnordered(x, y)) return true
            }
        } else if (expected.isBlank() || found.isBlank()) {
            return false
        }
        return isLatinScript(expected) != isLatinScript(found)
    }

    /** Within [DURATION_TOLERANCE_SEC]; an unknown length on either side is not a mismatch. */
    fun durationMatches(expectedMs: Long, foundMs: Long): Boolean {
        val expected = (expectedMs / 1000.0).roundToLong()
        val found = (foundMs / 1000.0).roundToLong()
        if (expected <= 0 || found <= 0) return true
        return abs(expected - found) <= DURATION_TOLERANCE_SEC
    }

    // ---------------------------------------------------------------- normalisation

    internal fun removeDiacritics(value: String): String =
        Normalizer.normalize(value, Normalizer.Form.NFD).replace(COMBINING_MARKS, "")
            .replace(D_STROKE, "dj")
            .replace(SHARP_S, "ss")
            .replace(AE, "ae")
            .replace(OE, "oe")

    internal fun normalizeSearchText(value: String): String =
        removeDiacritics(value).lowercase()
            .replace("&", " and ")
            .replace(NON_WORD, " ")
            .replace(SPACES, " ")
            .trim()

    internal fun normalizeLooseTitle(value: String): String =
        removeDiacritics(value).lowercase()
            .replace(LOOSE_PUNCTUATION, " ")
            .replace(NON_WORD, " ")
            .replace(SPACES, " ")
            .trim()

    /** Removes bracketed tails that don't change identity: "(Remastered 2011)", "[Radio Edit]". */
    internal fun cleanTitle(value: String): String {
        var cleaned = value
        var changed = true
        while (changed) {
            changed = false
            cleaned = BRACKETED.replace(cleaned) { m ->
                val content = (m.groupValues[1].ifEmpty { m.groupValues[2] }).lowercase()
                if (DECORATIONS.any { content.contains(it) }) {
                    changed = true
                    " "
                } else {
                    m.value
                }
            }
        }
        return cleaned.replace(SPACES, " ").trim()
    }

    internal fun extractCoreTitle(value: String): String =
        value.replace(TRAILING_PARENS, "").replace(TRAILING_BRACKETS, "").replace(DASH_SUFFIX, "").trim()

    internal fun stripTitleAnnotations(value: String): String = value.replace(FEAT_OR_FROM_ANNOTATION, " ")

    internal fun splitArtists(value: String): List<String> {
        var text = value.lowercase()
        ARTIST_JOINERS.forEach { text = text.replace(it, "|") }
        return text.split('|').map(::normalizeSearchText).filter { it.isNotEmpty() }
    }

    // ---------------------------------------------------------------- helpers

    /** Containment only counts when the shorter name has 2+ words: "rock" must not match "one ok rock". */
    private fun containmentMatch(a: String, b: String): Boolean {
        val (shorter, longer) = if (a.length <= b.length) a to b else b to a
        if (shorter.split(' ').count { it.isNotEmpty() } < 2) return false
        return longer.contains(shorter)
    }

    private fun sameWordsUnordered(a: String, b: String): Boolean {
        val wordsA = a.split(SPACES).filter { it.isNotEmpty() }.sorted()
        val wordsB = b.split(SPACES).filter { it.isNotEmpty() }.sorted()
        return wordsA.isNotEmpty() && wordsA == wordsB
    }

    private fun normalizeSymbolOnlyTitle(value: String): String =
        value.trim().lowercase().replace(ASCII_AND_PUNCTUATION, "").replace(COMBINING_MARKS, "")

    private fun hasAlphaNumeric(value: String) = ALPHANUMERIC.containsMatchIn(value)

    private fun isLatinScript(value: String): Boolean {
        val text = value.trim()
        return text.isEmpty() || !NON_LATIN.containsMatchIn(text)
    }
}
