package com.danielzuniga.player.data.lyrics

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.tags.TitleSplit
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LrcLibClientTest {

    private val song = Song(1, "De Música Ligera (Remastered 2007)", "Soda Stereo", "Canción Animal", 10, 211_000)
    private val requests = mutableListOf<String>()

    private fun client(vararg responses: LrcLibClient.Response) = LrcLibClient(
        userAgent = "test",
        fetch = { url, _ -> requests += url; responses[requests.size - 1] },
    )

    private fun track(
        duration: Double,
        synced: String? = null,
        plain: String? = null,
        instrumental: Boolean = false,
        name: String = "De Música Ligera",
        artist: String = "Soda Stereo",
    ) =
        JSONObject()
            .put("trackName", name)
            .put("artistName", artist)
            .put("duration", duration)
            .put("instrumental", instrumental)
            .put("syncedLyrics", synced ?: JSONObject.NULL)
            .put("plainLyrics", plain ?: JSONObject.NULL)

    @Test
    fun exactMatchPrefersSyncedLyrics() {
        val result = client(LrcLibClient.Response(200, track(211.0, "[00:01.00]Ella durmió", "Ella durmió").toString()))
            .find(song)
        assertEquals(LrcLibClient.Result.Found("[00:01.00]Ella durmió"), result)
        assertEquals(1, requests.size)
        assertTrue(requests.single().contains("/api/get?track_name=De+M%C3%BAsica+Ligera"))
        assertTrue(requests.single().contains("duration=211"))
    }

    @Test
    fun fallsBackToSearchAndPicksClosestDuration() {
        val results = JSONArray()
            .put(track(260.0, synced = "[00:01.00]en vivo"))
            .put(track(212.0, plain = "plain"))
            .put(track(210.0, synced = "[00:01.00]estudio"))
        val result = client(
            LrcLibClient.Response(404, null),
            LrcLibClient.Response(200, results.toString()),
        ).find(song)
        assertEquals(LrcLibClient.Result.Found("[00:01.00]estudio"), result)
        // The search uses the title without "(Remastered 2007)".
        assertTrue(requests[1].contains("/api/search?track_name=De+M%C3%BAsica+Ligera&"))
    }

    @Test
    fun instrumentalAndEmptySearchAreNotFound() {
        assertEquals(
            LrcLibClient.Result.NotFound,
            client(LrcLibClient.Response(200, track(211.0, instrumental = true).toString())).find(song),
        )
        requests.clear()
        assertEquals(
            LrcLibClient.Result.NotFound,
            client(LrcLibClient.Response(404, null), LrcLibClient.Response(200, "[]")).find(song),
        )
    }

    @Test
    fun searchIgnoresOtherSongsThatLastAsLong() {
        val results = JSONArray()
            // Same length, wrong song and artist: must not be picked just for its duration.
            .put(track(211.0, synced = "[00:01.00]otra letra", name = "Lamento Boliviano", artist = "Enanitos Verdes"))
            // A remix of the right song is a different recording.
            .put(track(210.0, synced = "[00:01.00]remix", name = "De Música Ligera (Remix)"))
            .put(track(212.0, plain = "Ella durmió", name = "De musica ligera", artist = "Soda Stereo, Gustavo Cerati"))
        val result = client(
            LrcLibClient.Response(404, null),
            LrcLibClient.Response(200, results.toString()),
        ).find(song)
        assertEquals(LrcLibClient.Result.Found("Ella durmió"), result)
    }

    @Test
    fun searchWithOnlyMismatchesIsNotFound() {
        val results = JSONArray().put(track(211.0, synced = "[00:01.00]x", name = "Lamento Boliviano", artist = "Enanitos Verdes"))
        assertEquals(
            LrcLibClient.Result.NotFound,
            client(LrcLibClient.Response(404, null), LrcLibClient.Response(200, results.toString())).find(song),
        )
    }

    @Test
    fun busyExactLookupStillTriesTheSearch() {
        val results = JSONArray().put(track(212.0, synced = "[00:01.00]Ella durmió"))
        val result = client(
            LrcLibClient.Response(503, null),
            LrcLibClient.Response(200, results.toString()),
        ).find(song)
        assertEquals(LrcLibClient.Result.Found("[00:01.00]Ella durmió"), result)
    }

    @Test
    fun networkTroubleIsAFailureNotANotFound() {
        assertEquals(
            LrcLibClient.Result.Failed,
            client(LrcLibClient.Response(-1, null), LrcLibClient.Response(-1, null)).find(song),
        )
        requests.clear()
        assertEquals(
            LrcLibClient.Result.Failed,
            client(LrcLibClient.Response(503, null), LrcLibClient.Response(503, null)).find(song),
        )
    }

    @Test
    fun unknownArtistIsNotSearched() {
        val result = client().find(song.copy(artist = "<unknown>"))
        assertEquals(LrcLibClient.Result.NotFound, result)
        assertTrue(requests.isEmpty())
    }

    @Test
    fun missingTagsAreNotSentAsTheirLabels() {
        // What the library scan produces for a file without an artist tag: the label, not "<unknown>".
        val untagged = song.copy(artist = "Artista desconocido", artists = listOf("Artista desconocido"), hasArtistTag = false)
        assertEquals(LrcLibClient.Result.NotFound, client().find(untagged))
        assertTrue(requests.isEmpty())

        // Without an album tag the lookup still runs, just without the "unknown album" label.
        client(LrcLibClient.Response(404, null), LrcLibClient.Response(200, "[]"))
            .find(song.copy(album = "Álbum desconocido", hasAlbumTag = false))
        assertTrue(requests.none { "album_name" in it })
    }

    @Test
    fun identifyKeepsTheReadingTheCatalogueConfirms() {
        val readings = TitleSplit.readings("24K - T3R Elemento")
        val result = client(
            // "artist 24K, song T3R Elemento": nothing like it.
            LrcLibClient.Response(200, "[]"),
            // "artist T3R Elemento, song 24K": there it is, with the same length.
            LrcLibClient.Response(200, JSONArray().put(track(193.0, name = "24K", artist = "T3R Elemento")).toString()),
        ).identify(readings, durationMs = 192_400)

        assertEquals(LrcLibClient.Identity.Found("24K", "T3R Elemento"), result)
        assertTrue(requests[0].contains("artist_name=24K"))
        assertTrue(requests[1].contains("artist_name=T3R+Elemento"))
    }

    @Test
    fun identifyRejectsOtherLengthsAndReportsNetworkTrouble() {
        val readings = TitleSplit.readings("24K - T3R Elemento")
        val otherVersion = JSONArray().put(track(230.0, name = "24K", artist = "T3R Elemento")).toString()
        assertEquals(
            LrcLibClient.Identity.NotFound,
            client(LrcLibClient.Response(200, "[]"), LrcLibClient.Response(200, otherVersion)).identify(readings, 192_000),
        )
        requests.clear()
        assertEquals(
            LrcLibClient.Identity.Failed,
            client(LrcLibClient.Response(-1, null), LrcLibClient.Response(-1, null)).identify(readings, 192_000),
        )
    }

    @Test
    fun cleansDecoratedTitles() {
        assertEquals("Persiana Americana", LrcLibClient.cleanTitle("Persiana Americana (Remastered 2007)"))
        assertEquals("Eres", LrcLibClient.cleanTitle("Eres - En Vivo"))
        assertEquals("Clandestino", LrcLibClient.cleanTitle("Clandestino feat. Anouk"))
        assertEquals("(Nothing But) Flowers", LrcLibClient.cleanTitle("(Nothing But) Flowers"))
    }

    @Test
    fun repositorySavesOnlineLyricsAndRetriesAfterFailures() = runTest {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var online = LrcLibClient.Response(-1, null) // every request fails while "offline"
        var calls = 0
        val repo = LyricsRepository(
            context = context,
            onlineEnabled = { true },
            client = LrcLibClient("test", fetch = { _, _ -> calls++; online }),
        )
        val offlineSong = song.copy(id = 99)

        // Offline: nothing, and not remembered.
        assertNull(repo.load(offlineSong))
        online = LrcLibClient.Response(200, track(211.0, synced = "[00:01.00]Ella durmió").toString())

        val found = repo.load(offlineSong)
        assertEquals(LyricsSource.ONLINE, found?.source)
        assertEquals("Ella durmió", (found?.lyrics as Lyrics.Synced).lines.single().text)

        // A fresh repository reads the saved copy instead of the network.
        val callsBefore = calls
        val again = LyricsRepository(context, { true }, LrcLibClient("test", fetch = { _, _ -> calls++; online }))
            .load(offlineSong)
        assertEquals(LyricsSource.ONLINE, again?.source)
        assertEquals(callsBefore, calls)
    }
}
