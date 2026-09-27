package com.danielzuniga.player.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.appContainer
import com.danielzuniga.player.data.Song
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config
import java.util.concurrent.TimeUnit

/** Drives the real service through PlayerConnection, as the UI does. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaybackServiceTest {

    private lateinit var service: ServiceController<PlaybackService>
    private lateinit var connection: PlayerConnection

    private val songs = (1L..3L).map { Song(it, "Song $it", "Artist", "Album", 1, 180_000) }

    @Before
    fun setUp() {
        val app = ApplicationProvider.getApplicationContext<Application>()
        // The service resolves every queue against the library before the player gets it, and the
        // first scan in a JVM opens Room's SQLite: seconds on a cold Robolectric JVM. The controller
        // shows its own guess of the queue meanwhile, so a wait on the service's play order raced
        // that scan. Scanning first leaves the tests waiting only on the player.
        app.appContainer.musicRepository.load()
        awaitUntil(timeoutMs = 30_000) { app.appContainer.musicRepository.hasScanned.value }
        service = Robolectric.buildService(PlaybackService::class.java).create()
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, PlaybackService::class.java),
            service.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE)),
        )
        connection = PlayerConnection(app)
        awaitUntil { connection.isConnected.value }
    }

    @After
    fun tearDown() {
        connection.release()
        service.destroy()
    }

    @Test
    fun playPublishesQueueAndCurrentSong() {
        connection.play(songs, startIndex = 1)
        awaitUntil { connection.queue.value.items.size == 3 }

        assertEquals(1, connection.queue.value.currentIndex)
        assertEquals("Song 2", connection.state.value.nowPlaying?.title)
        assertEquals(2L, connection.state.value.nowPlaying?.songId)
    }

    @Test
    fun queueEditsReachThePlayer() {
        connection.play(songs, startIndex = 0)
        awaitUntil { connection.queue.value.items.size == 3 }

        connection.addToQueue(listOf(Song(4, "Song 4", "Artist", "Album", 1, 1_000)))
        awaitUntil { connection.queue.value.items.size == 4 }
        assertEquals(4L, connection.queue.value.items.last().songId)

        connection.playNext(listOf(Song(5, "Song 5", "Artist", "Album", 1, 1_000)))
        awaitUntil { connection.queue.value.items.size == 5 }
        assertEquals(5L, connection.queue.value.items[1].songId)

        connection.moveQueueItem(1, 4)
        awaitUntil { connection.queue.value.items.last().songId == 5L }

        connection.removeQueueItem(4)
        awaitUntil { connection.queue.value.items.size == 4 }
        assertEquals(listOf(1L, 2L, 3L, 4L), connection.queue.value.items.map { it.songId })
    }

    @Test
    fun playNextWhileShufflingIsReallyNext() {
        val many = (1L..20L).map { Song(it, "Song $it", "Artist", "Album", 1, 180_000) }
        connection.play(many, startIndex = 7, shuffle = true)
        awaitUntil { connection.queue.value.items.size == 20 && connection.queue.value.shuffled }
        // A shuffled queue starts with the song that plays first, so none are left behind it.
        awaitUntil { connection.queue.value.items.first().songId == 8L }

        connection.playNext(listOf(Song(99, "Song 99", "Artist", "Album", 1, 1_000)))
        awaitUntil { connection.queue.value.items.size == 21 }
        connection.addToQueue(listOf(Song(98, "Song 98", "Artist", "Album", 1, 1_000)))
        awaitUntil { connection.queue.value.items.size == 22 }

        // The queue is listed in play order: "play next" follows the current song, "add" goes last.
        val queue = connection.queue.value
        val current = queue.items.indexOfFirst { it.index == queue.currentIndex }
        assertEquals(99L, queue.items[current + 1].songId)
        assertEquals(98L, queue.items.last().songId)
    }

    @Test
    fun removingTheCurrentSongWhileShufflingKeepsTheOrder() {
        val many = (1L..20L).map { Song(it, "Song $it", "Artist", "Album", 1, 180_000) }
        connection.play(many, startIndex = 7, shuffle = true)
        // Only the service's shuffle puts song 8 first; the controller's own guess keeps list order.
        awaitUntil { connection.queue.value.items.size == 20 && connection.queue.value.items.first().songId == 8L }
        val order = connection.queue.value.items.map { it.songId }
        repeat(3) { connection.next() }
        awaitUntil { connection.state.value.nowPlaying?.songId == order[3] }

        // Android Auto or the web monitor can remove the song that's playing; the app's queue can't.
        connection.removeQueueItem(connection.queue.value.currentIndex)
        awaitUntil { connection.queue.value.items.size == 19 }

        // Same order without it: the songs already played don't come back after the current one.
        assertEquals(order - order[3], connection.queue.value.items.map { it.songId })
        assertEquals(order[4], connection.state.value.nowPlaying?.songId)
    }

    @Test
    fun sleepTimerRoundTripsThroughSessionExtras() {
        connection.play(songs, startIndex = 0)
        awaitUntil { connection.queue.value.items.isNotEmpty() }

        connection.setSleepTimer(30)
        awaitUntil { connection.state.value.sleepAtMs > 0 }
        val remaining = connection.state.value.sleepAtMs - System.currentTimeMillis()
        assertTrue(remaining in TimeUnit.MINUTES.toMillis(29)..TimeUnit.MINUTES.toMillis(30))

        connection.setSleepTimer(SessionCommands.SLEEP_END_OF_TRACK)
        awaitUntil { connection.state.value.sleepAtEndOfTrack }
        assertEquals(0L, connection.state.value.sleepAtMs)

        connection.setSleepTimer(SessionCommands.SLEEP_OFF)
        awaitUntil { !connection.state.value.sleepAtEndOfTrack }
        assertFalse(connection.state.value.sleepAtMs > 0)
    }

    @Test
    fun shuffleAndRepeatToggle() {
        connection.play(songs, startIndex = 0)
        awaitUntil { connection.queue.value.items.isNotEmpty() }

        connection.toggleShuffle()
        awaitUntil { connection.state.value.shuffleEnabled }
        assertTrue(connection.queue.value.shuffled)
        assertEquals(3, connection.queue.value.items.size)

        connection.cycleRepeatMode()
        awaitUntil { connection.state.value.repeatMode == Player.REPEAT_MODE_ALL }
        connection.cycleRepeatMode()
        awaitUntil { connection.state.value.repeatMode == Player.REPEAT_MODE_ONE }
    }

    /** Turns the main looper until [condition] holds; the budget is only a ceiling. */
    private fun awaitUntil(timeoutMs: Long = 10_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condition not met")
    }
}
