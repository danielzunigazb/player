package com.danielzuniga.player.data.lyrics

sealed interface Lyrics {
    data class Synced(val lines: List<LyricLine>) : Lyrics {
        /** Index of the line being sung at [positionMs], or -1 before the first one. */
        fun indexAt(positionMs: Long): Int {
            var low = 0
            var high = lines.lastIndex
            var result = -1
            while (low <= high) {
                val mid = (low + high) ushr 1
                if (lines[mid].timeMs <= positionMs) {
                    result = mid
                    low = mid + 1
                } else {
                    high = mid - 1
                }
            }
            return result
        }
    }

    data class Plain(val text: String) : Lyrics
}

data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")
    private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)
    private val METADATA_TAG = Regex("""^\[[a-zA-Z]+:.*]$""")

    /** Parses LRC text; falls back to plain lyrics when there are no timestamps. */
    fun parse(raw: String): Lyrics? {
        val text = raw.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (text.isEmpty()) return null

        // A positive offset shows lyrics earlier, per the LRC convention.
        val offsetMs = OFFSET.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
        val lines = mutableListOf<LyricLine>()
        text.lineSequence().forEach { line ->
            val stamps = TIMESTAMP.findAll(line).toList()
            if (stamps.isEmpty() || stamps.first().range.first != 0) return@forEach
            val lyric = line.substring(stamps.last().range.last + 1).trim()
            stamps.forEach { stamp ->
                val (min, sec, frac) = stamp.destructured
                val fracMs = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.toLong()
                }
                val time = min.toLong() * 60_000 + sec.toLong() * 1_000 + fracMs - offsetMs
                lines += LyricLine(time.coerceAtLeast(0L), lyric)
            }
        }

        if (lines.isNotEmpty()) return Lyrics.Synced(lines.sortedBy { it.timeMs })

        val plain = text.lineSequence()
            .filterNot { METADATA_TAG.matches(it.trim()) }
            .joinToString("\n")
            .trim()
        return if (plain.isEmpty()) null else Lyrics.Plain(plain)
    }
}
