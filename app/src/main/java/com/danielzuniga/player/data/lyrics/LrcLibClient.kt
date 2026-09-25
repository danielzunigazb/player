package com.danielzuniga.player.data.lyrics

import com.danielzuniga.player.data.Song
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
                "album_name" to song.album.takeUnless { it.isUnknown() },
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
                "artist_name" to song.artist,
            ),
            userAgent,
        )
        if (search.code != 200 || search.body == null) {
            val definitive = search.code in 400..499 || exact.code == 404
            return if (definitive) Result.NotFound else Result.Failed
        }
        val best = pickBest(JSONArray(search.body), durationSec) ?: return Result.NotFound
        return lyricsFrom(best)?.let { Result.Found(it) } ?: Result.NotFound
    }

    companion object {
        /** Results further than this from the song's length are probably another version. */
        private const val MAX_DURATION_DIFF_SEC = 5.0

        internal fun lyricsFrom(json: JSONObject): String? {
            if (json.optBoolean("instrumental")) return null
            return json.optNonBlank("syncedLyrics") ?: json.optNonBlank("plainLyrics")
        }

        /** Closest-duration match that has lyrics, preferring synced ones on ties. */
        internal fun pickBest(results: JSONArray, durationSec: Long): JSONObject? =
            (0 until results.length())
                .map { results.getJSONObject(it) }
                .filter { lyricsFrom(it) != null }
                .filter { durationSec <= 0 || abs(it.optDouble("duration", 0.0) - durationSec) <= MAX_DURATION_DIFF_SEC }
                .minWithOrNull(
                    compareBy<JSONObject> { abs(it.optDouble("duration", 0.0) - durationSec) }
                        .thenBy { if (it.optNonBlank("syncedLyrics") != null) 0 else 1 },
                )

        /** Drops decorations that rarely match a lyrics database: "(Remastered 2011)", "- Live", "feat. X". */
        internal fun cleanTitle(title: String): String = title
            .replace(Regex("""\s*[(\[][^)\]]*(remaster|live|version|edit|mix|mono|stereo|feat|ft\.|en vivo|versi[oó]n)[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+-\s+.*(remaster|live|version|edit|mix|en vivo).*$""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s+(feat\.|ft\.|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
            .trim()
            .ifEmpty { title }

        private fun Song.hasSearchableTags() = title.isNotBlank() && !artist.isUnknown()

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
