package com.danielzuniga.player.remote

import android.content.Context
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.playback.toMediaItem
import org.json.JSONArray
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteControlTest {

    private val songs = listOf(
        Song(1, "De Música Ligera", "Soda Stereo", "Canción Animal", 10, 210_000),
        Song(2, "Persiana Americana", "Soda Stereo", "Signos", 11, 280_000),
        Song(3, "Eres", "Café Tacvba", "Cuatro Caminos", 12, 250_000),
    )
    private val library = LibraryIndex(songs)
    private var volume = 0.5f
    private lateinit var player: ExoPlayer
    private lateinit var control: RemoteControl

    @Before
    fun setUp() {
        player = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Context>()).build()
        control = RemoteControl(player, { library }, object : RemoteControl.Volume {
            override fun get() = volume
            override fun set(value: Float) {
                volume = value
            }
        })
        player.setMediaItems(songs.map { it.toMediaItem() }, 1, 0L)
    }

    @After
    fun tearDown() = player.release()

    private fun cmd(op: String, vararg extra: Pair<String, Any>) =
        JSONObject().put("type", "cmd").put("op", op).apply { extra.forEach { (k, v) -> put(k, v) } }

    @Test
    fun stateDescribesTheSongQueueAndModes() {
        player.repeatMode = Player.REPEAT_MODE_ONE
        val state = control.state()
        assertEquals("state", state.getString("type"))
        assertEquals("Persiana Americana", state.getJSONObject("song").getString("title"))
        assertEquals("Signos", state.getJSONObject("song").getString("album"))
        assertEquals(280_000L, state.getJSONObject("song").getLong("durationMs"))
        assertEquals("one", state.getString("repeat"))
        assertEquals(1, state.getInt("index"))
        assertEquals(3, state.getInt("queueTotal"))
        assertEquals(listOf("1", "2", "3"), state.getJSONArray("queue").let { q -> (0 until q.length()).map { q.getJSONObject(it).getString("id") } })
        assertEquals(0.5, state.getDouble("volume"), 0.001)
    }

    @Test
    fun theQueueSentIsAWindowAroundTheCurrentSong() {
        val many = (1L..400L).map { Song(it, "Song $it", "A", "B", 1, 1000) }
        player.setMediaItems(many.map { it.toMediaItem() }, 100, 0L)
        val state = control.state()
        assertEquals(400, state.getInt("queueTotal"))
        assertEquals(80, state.getInt("queueStart"))
        assertEquals(150, state.getJSONArray("queue").length())
        assertEquals("101", state.getJSONArray("queue").getJSONObject(20).getString("id"))
    }

    @Test
    fun movesThroughTheQueue() {
        control.handle(cmd("next"))
        assertEquals(2, player.currentMediaItemIndex)
        control.handle(cmd("skipTo", "index" to 0))
        assertEquals(0, player.currentMediaItemIndex)
        assertTrue(player.playWhenReady)
        control.handle(cmd("pause"))
        assertFalse(player.playWhenReady)
    }

    @Test
    fun editsTheQueueAndIgnoresIndexesOutsideIt() {
        control.handle(cmd("remove", "index" to 2))
        assertEquals(2, player.mediaItemCount)
        control.handle(cmd("move", "fromIndex" to 0, "toIndex" to 1))
        assertEquals("2", player.getMediaItemAt(0).mediaId)
        control.handle(cmd("remove", "index" to 9))
        control.handle(cmd("skipTo", "index" to -1))
        assertEquals(2, player.mediaItemCount)
    }

    @Test
    fun playsQueuesAndEnqueuesSongsByIdSkippingUnknownOnes() {
        control.handle(cmd("playSongs", "ids" to JSONArray(listOf("3", "99", "1")), "index" to 1))
        assertEquals(listOf("3", "1"), (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
        assertEquals(1, player.currentMediaItemIndex)
        control.handle(cmd("playNext", "ids" to JSONArray(listOf("2"))))
        control.handle(cmd("addToQueue", "ids" to JSONArray(listOf("3"))))
        assertEquals(listOf("3", "1", "2", "3"), (0 until player.mediaItemCount).map { player.getMediaItemAt(it).mediaId })
    }

    @Test
    fun searchAnswersWithMatchingSongs() {
        val reply = control.handle(cmd("search", "query" to "soda"))!!
        assertEquals("results", reply.getString("type"))
        assertEquals("soda", reply.getString("query"))
        assertEquals(2, reply.getJSONArray("songs").length())
    }

    @Test
    fun setsModesAndVolumeWithinRange() {
        control.handle(cmd("shuffle", "on" to true))
        control.handle(cmd("repeat", "mode" to "all"))
        control.handle(cmd("volume", "value" to 0.8))
        assertTrue(player.shuffleModeEnabled)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
        assertEquals(0.8f, volume, 0.001f)
        control.handle(cmd("volume", "value" to 3))
        control.handle(cmd("repeat", "mode" to "sideways"))
        assertEquals(0.8f, volume, 0.001f)
        assertEquals(Player.REPEAT_MODE_ALL, player.repeatMode)
    }

    @Test
    fun ignoresWhatIsNotACommand() {
        assertNull(control.handle(JSONObject().put("type", "state").put("op", "next")))
        assertNull(control.handle(cmd("format-disk")))
        assertEquals(1, player.currentMediaItemIndex)
    }
}
