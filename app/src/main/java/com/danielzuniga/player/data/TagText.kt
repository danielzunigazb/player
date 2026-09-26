package com.danielzuniga.player.data

import java.text.Normalizer

/**
 * Cleans the text that comes out of audio tags, which is often messy: artists joined with
 * stray separators ("A, ,, B"), NUL-separated ID3v2.4 lists, invisible characters, doubled
 * spaces and SHOUTED titles. Applied once when the library is scanned, so every screen,
 * the widget, Android Auto and the lyrics lookup all see the same clean strings.
 */
object TagText {

    /** Characters that are never meant to be visible: controls, zero-width marks, BOM (but not the emoji joiner). */
    private val INVISIBLE = Regex("[\\p{Cc}\\p{Cf}&&[^\\u200D]]")
    private val SPACES = Regex("\\s+")

    /**
     * Where one artist ends and the next begins. `&`, `x` and `/` are left alone on purpose:
     * they're part of too many real names (Simon & Garfunkel, AC/DC).
     */
    private val ARTIST_SEPARATORS = Regex(
        """\u0000|\s*[,;]\s*|\s+/\s+|\s+(?:feat\.?|ft\.?|featuring)\s+""",
        RegexOption.IGNORE_CASE,
    )

    /** Tokens kept upper-case when an ALL-CAPS string is re-cased: short roman numerals, initials, numbers. */
    private val KEEP_UPPER = Regex("""^(?:[IVX]{2,4}|[A-Z](?:\.[A-Z])+\.?|\d+\w*)$""")

    /** Trims, drops invisible characters and collapses runs of whitespace. */
    fun clean(raw: String?): String {
        if (raw == null) return ""
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
            .replace('\u0000', ' ')
            // Whitespace first, so a tab or newline between words becomes a space rather than vanishing.
            .replace(SPACES, " ")
            .replace(INVISIBLE, "")
            .trim()
    }

    /** A title or album name: cleaned, and calmed down if the whole thing is in capitals. */
    fun title(raw: String?): String = unshout(clean(raw))

    /**
     * The individual artists in an artist tag, in order, without blanks or repeats
     * (case- and accent-insensitive).
     */
    fun artists(raw: String?): List<String> {
        if (raw == null) return emptyList()
        val seen = HashSet<String>()
        return Normalizer.normalize(raw, Normalizer.Form.NFC)
            .split(ARTIST_SEPARATORS)
            .map { unshout(clean(it)) }
            .filter { it.isNotEmpty() && it != "-" && seen.add(it.normalizedForSearch()) }
    }

    /** How several artists read on one line. */
    fun joinArtists(names: List<String>): String = names.joinToString(", ")

    /**
     * A short credit for tight spots: the first [max] artists and a count of the rest,
     * e.g. "Natanael Cano, Tito Double P +2".
     */
    fun shortCredit(names: List<String>, max: Int = 2, keepNamesWhole: Boolean = false): String {
        // Non-breaking spaces inside each name (and before "+N") so a wrap only happens between artists.
        val shown = names.take(max).map { if (keepNamesWhole) it.replace(' ', NBSP) else it }
        val rest = names.size - shown.size
        val sep = if (keepNamesWhole) NBSP else ' '
        return joinArtists(shown) + if (rest > 0) "$sep+$rest" else ""
    }

    private const val NBSP = '\u00A0'

    /** A lower-case letter right after an opening bracket, quote or hyphen. */
    private val CAPITAL_AFTER_OPENER = Regex("""([(\["'¿¡-])(\p{Ll})""")

    /**
     * "OLIVIA LA FLAKA" → "Olivia La Flaka". Only strings with no lower-case letters and at
     * least two words are touched, so one-word stylisations ("HUMBLE.") and names with
     * deliberate casing ("deadmau5", "BTS" alone) keep their look.
     */
    internal fun unshout(text: String): String {
        val letters = text.filter { it.isLetter() }
        val words = text.split(' ').filter { word -> word.any { it.isLetter() } }
        if (letters.length < 4 || words.size < 2 || letters.any { it.isLowerCase() }) return text
        return text.split(' ').joinToString(" ") { word ->
            if (KEEP_UPPER.matches(word.trim('(', ')', '[', ']', ',', '-'))) {
                word
            } else {
                word.lowercase().replaceFirstChar { it.titlecase() }
                    // Keep the letter after an opening bracket, quote or hyphen capitalised: "(Remix)", "Hip-Hop".
                    .replace(CAPITAL_AFTER_OPENER) { it.groupValues[1] + it.groupValues[2].uppercase() }
            }
        }
    }
}
