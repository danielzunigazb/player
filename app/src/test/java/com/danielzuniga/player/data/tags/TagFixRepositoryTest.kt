package com.danielzuniga.player.data.tags

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.db.AppDatabase
import com.danielzuniga.player.data.db.TagFixEntity
import com.danielzuniga.player.data.lyrics.LrcLibClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class TagFixRepositoryTest {

    private lateinit var db: AppDatabase
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val requests = mutableListOf<String>()
    private var online = true

    /** LRCLIB knows one recording: "24K" by T3R Elemento, 3:12. */
    private val client = LrcLibClient("test", fetch = { url, _ ->
        requests += url
        val hit = "track_name=24K" in url && "artist_name=T3R+Elemento" in url
        val body = if (hit) {
            JSONArray().put(JSONObject().put("trackName", "24K").put("artistName", "T3R Elemento").put("duration", 192.0))
        } else {
            JSONArray()
        }
        LrcLibClient.Response(200, body.toString())
    })

    private lateinit var repo: TagFixRepository

    private val untagged = Song(1, "24K - T3R Elemento", "Artista desconocido", "Descargas", 1, 192_000, hasArtistTag = false)
    private val nameless = Song(2, "Sin separador", "Artista desconocido", "Descargas", 1, 150_000, hasArtistTag = false)
    private val tagged = Song(3, "Soda Stereo - De Música Ligera", "Soda Stereo", "Canción Animal", 2, 210_000)

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext<Context>(), AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TagFixRepository(db.tagFixDao(), client, { online }, scope)
    }

    @After
    fun tearDown() {
        scope.cancel()
        db.close()
    }

    private fun awaitEntry(id: Long, status: String) = runBlocking {
        withTimeout(5_000) { repo.entries.first { it[id]?.status == status } }
    }

    @Test
    fun identifiesUntaggedSongsOnceAndCanRestoreThem() = runBlocking {
        repo.identifyUntagged(listOf(untagged, nameless, tagged))
        awaitEntry(1, TagFixEntity.FIXED)

        val fixed = repo.apply(untagged)
        assertEquals("24K", fixed.title)
        assertEquals("T3R Elemento", fixed.artist)
        assertTrue(fixed.hasArtistTag && fixed.tagsFixed)
        // Two readings for the dashed name; nothing for the song without one, or the tagged one.
        assertEquals(2, requests.size)

        repo.identifyUntagged(listOf(untagged))
        assertEquals("already known: no new lookups", 2, requests.size)

        repo.restore(1)
        awaitEntry(1, TagFixEntity.RESTORED)
        assertEquals(untagged, repo.apply(untagged))
    }

    @Test
    fun remembersMissesAndStaysOfflineWhenAskedTo() = runBlocking {
        val unknown = untagged.copy(id = 5, title = "Nadie - Nada")
        repo.identifyUntagged(listOf(unknown))
        awaitEntry(5, TagFixEntity.NO_MATCH)
        assertEquals("Nadie - Nada", repo.apply(unknown).title)

        online = false
        requests.clear()
        repo.identifyUntagged(listOf(untagged))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun offlineCleanupsNeedNoLookup() {
        val noisy = untagged.copy(id = 6, title = "01. Algo - Alguien (Official Video)")
        assertEquals("Algo - Alguien", TagFixRepository.apply(noisy, null).title)

        val repeated = TagFixRepository.apply(tagged, null)
        assertEquals("De Música Ligera", repeated.title)
        assertTrue(repeated.tagsFixed)

        val restored = TagFixEntity(3, TagFixEntity.RESTORED, null, null, 0)
        assertEquals(tagged, TagFixRepository.apply(tagged, restored))
    }

    @Test
    fun tagsTheUserAddsLaterWin() {
        val fix = TagFixEntity(1, TagFixEntity.FIXED, "24K", "T3R Elemento", 0)
        val retagged = untagged.copy(title = "24 Kilates", artist = "T3R Elemento", artists = listOf("T3R Elemento"), hasArtistTag = true)
        val shown = TagFixRepository.apply(retagged, fix)
        assertEquals("24 Kilates", shown.title)
        assertFalse(shown.tagsFixed)
    }
}
