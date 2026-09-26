package com.danielzuniga.player.data.lyrics

import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.TrackMatch
import com.danielzuniga.player.data.tags.TitleSplit
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs

/**
 * Client for LRCLIB (https://lrclib.net), a free, key-less lyrics database that serves
 * time-synced LRC when it has it and plain text otherwise.
 *
 * Only the song's title, artist, album and duration are sent. [fetch] is injectable so the
 * matching logic can be tested without the network.
 */
class LrcLibClient(
    private val userAgent: String,
    private val baseUrl: String = "https://lrclib.net",
    private val fetch: (url: String, userAgent: String) -> Response = ::httpGet,
) {

    sealed interface Result {
        data class Found(val lrc: String) : Result
        /** LRCLIB answered and has nothing (or the song is instrumental): safe to remember. */
        data object NotFound : Result
        /** Network or server trouble: worth trying again later. */
        data object Failed : Result
    }

    data class Response(val code: Int, val body: String?)

    /** Blocking; call from an IO dispatcher. */
    fun find(song: Song): Result {
        if (!song.hasSearchableTags()) return Result.NotFound
        val durationSec = song.durationMs / 1000

        // 1) Exact signature lookup: cheapest for the server and the most precise.
        val exact = fetch(
            "$baseUrl/api/get?" + query(
                "track_name" to song.title,
                "artist_name" to song.artist,
                "album_name" to song.album.takeIf { song.hasAlbumTag && !it.isUnknown() },
                "duration" to durationSec.takeIf { it > 0 }?.toString(),
            ),
            userAgent,
        )
        if (exact.code == 200) {
            exact.body?.let(::JSONObject)?.let { json ->
                if (json.optBoolean("instrumental")) return Result.NotFound
                lyricsFrom(json)?.let { return Result.Found(it) }
            }
        }
        // Anything else falls through: LRCLIB answers 503 "busy" when the album doesn't match
        // its records, and the search below usually still finds the song.

        // 2) Fuzzy search with a cleaned-up title, keeping the closest duration.
        val search = fetch(
            "$baseUrl/api/search?" + query(
                "track_name" to cleanTitle(song.title),
                "artist_name" to song.artists.first(),
            ),
            userAgent,
        )
        if (search.code != 200 || search.body == null) {
            val definitive = search.code in 400..499 || exact.code == 404
            return if (definitive) Result.NotFound else Result.Failed
        }
        val best = pickBest(JSONArray(search.body), song) ?: return Result.NotFound
        return lyricsFrom(best)?.let { Result.Found(it) } ?: Result.NotFound
    }

    /** What [identify] learned about a song. */
    sealed interface Identity {
        /** LRCLIB has this recording: its title and artist, spelled as the catalogue does. */
        data class Found(val title: String, val artist: String) : Identity
        data object NotFound : Identity
        /** Network or server trouble: worth asking again later. */
        data object Failed : Identity
    }

    /**
     * Which of [readings] (artist/title guesses from an untagged file's name) is a real recording
     * of this length. A reading only counts when a catalogue entry matches its title, its artist
     * and the duration within [IDENTIFY_DURATION_DIFF_SEC]; the first reading that does wins.
     * Blocking; call from an IO dispatcher.
     */
    fun identify(readings: List<TitleSplit.Reading>, durationMs: Long): Identity {
        if (durationMs <= 0) return Identity.NotFound
        var failed = false
        for (reading in readings) {
            val response = fetch(
                "$baseUrl/api/search?" + query("track_name" to reading.title, "artist_name" to reading.artist),
                userAgent,
            )
            if (response.code != 200 || response.body == null) {
                if (response.code !in 400..499) failed = true
                continue
            }
            val results = runCatching { JSONArray(response.body) }.getOrNull() ?: continue
            val best = (0 until results.length())
                .map { results.getJSONObject(it) }
                .filter { abs(it.optDouble("duration", 0.0) - durationMs / 1000.0) <= IDENTIFY_DURATION_DIFF_SEC }
                .filter {
                    TrackMatch.trackTitlesMatch(reading.title, it.optString("trackName")) &&
                        TrackMatch.artistNamesMatch(reading.artist, it.optString("artistName"))
                }
                .minByOrNull { abs(it.optDouble("duration", 0.0) - durationMs / 1000.0) }
            if (best != null) {
                val title = best.optString("trackName").trim()
                val artist = best.optString("artistName").trim()
                if (title.isNotEmpty() && artist.isNotEmpty()) return Identity.Found(title, artist)
            }
        }
        return if (failed) Identity.Failed else Identity.NotFound
    }

    companion object {
        /** Results further than this from the song's length are probably another version. */
        private const val MAX_DURATION_DIFF_SEC = 5.0

        /** Stricter than for lyrics: this renames a song, so the length must really agree. */
        private const val IDENTIFY_DURATION_DIFF_SEC = 3.0

        internal fun lyricsFrom(json: JSONObject): String? {
            if (json.optBoolean("instrumental")) return null
            return json.optNonBlank("syncedLyrics") ?: json.optNonBlank("plainLyrics")
        }

        /**
         * The search result that is really this song: same recording by title and artist
         * (TrackMatch, so another song that happens to last as long never qualifies), within
         * a few seconds so synced lines land on time, closest length first, synced preferred.
         */
        internal fun pickBest(results: JSONArray, song: Song): JSONObject? {
            val durationSec = song.durationMs / 1000
            return (0 until results.length())
                .map { results.getJSONObject(it) }
                .filter { lyricsFrom(it) != null }
                .filter { durationSec <= 0 || abs(it.optDouble("duration", 0.0) - durationSec) <= MAX_DURATION_DIFF_SEC }
                .filter {
                    TrackMatch.trackTitlesMatch(song.title, it.optString("trackName")) &&
                        TrackMatch.artistNamesMatch(song.artist, it.optString("artistName"))
                }
                .minWithOrNull(
                    compareBy<JSONObject> { abs(it.optDouble("duration", 0.0) - durationSec) }
                        .thenBy { if (it.optNonBlank("syncedLyrics") != null) 0 else 1 },
                )
        }

        /** Drops decorations that rarely match a lyrics database: "(Remastered 2011)", "- Live", "feat. X". */
        internal fun cleanTitle(title: String): String = title
            .replace(Regex("""\s*[(\[][^)\]]*(remaster|live|version|edit|mix|mono|stereo|feat|ft\.|en vivo|versi[oó]n)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+.*(remaster|live|version|edit|mix|en vivo).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(feat\.|ft\.|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { title }

        // Without an artist tag, [Song.artist] is only the "unknown artist" label: nothing to search by.
        private fun Song.hasSearchableTags() = title.isNotBlank() && hasArtistTag && !artist.isUnknown()

        private fun String.isUnknown() = isBlank() || equals("<unknown>", ignoreCase = true)

        private fun JSONObject.optNonBlank(key: String): String? =
            if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

        private fun query(vararg params: Pair<String, String?>): String =
            params.filter { it.second != null }
                .joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

        private fun httpGet(url: String, userAgent: String): Response = try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 8_000
            conn.readTimeout = 8_000
            conn.setRequestProperty("User-Agent", userAgent)
            conn.setRequestProperty("Accept", "application/json")
            try {
                val code = conn.responseCode
                val body = if (code == 200) conn.inputStream.bufferedReader().use { it.readText() } else null
                Response(code, body)
            } finally {
                conn.disconnect()
            }
        } catch (e: Exception) {
            Response(-1, null)
        }
    }
}
