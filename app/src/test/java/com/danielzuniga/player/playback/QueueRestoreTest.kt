package com.danielzuniga.player.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.media3.common.Player
import androidx.media3.session.MediaSessionService
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.FakeMediaProvider
import com.danielzuniga.player.FakeSong
import com.danielzuniga.player.appContainer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.android.controller.ServiceController
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class QueueRestoreTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private var service: ServiceController<PlaybackService>? = null
    private var connection: PlayerConnection? = null

    @After
    fun tearDown() {
        connection?.release()
        service?.destroy()
    }

    @Test
    fun restoresSavedQueueSkippingDeletedSongs() {
        fakeMediaStore(ids = listOf(10L, 20L, 30L))
        app.appContainer.playbackState.save(
            SavedQueue(
                songIds = listOf(10, 99, 20, 30),
                index = 2,
                positionMs = 42_000,
                shuffle = false,
                repeatMode = Player.REPEAT_MODE_ALL,
            )
        )

        val connection = startAndConnect()
        awaitUntil { connection.queue.value.items.size == 3 }

        assertEquals(listOf(10L, 20L, 30L), connection.queue.value.items.map { it.songId })
        assertEquals(1, connection.queue.value.currentIndex)
        val state = connection.state.value
        assertEquals("Song 20", state.nowPlaying?.title)
        assertEquals(42_000L, state.positionMs)
        assertEquals(Player.REPEAT_MODE_ALL, state.repeatMode)
        assertTrue(!state.isPlaying)
    }

    private fun fakeMediaStore(ids: List<Long>) = FakeMediaProvider.install(ids.map { FakeSong(it, "Song $it") })

    private fun startAndConnect(): PlayerConnection {
        val controller = Robolectric.buildService(PlaybackService::class.java).create()
        service = controller
        shadowOf(app).setComponentNameAndServiceForBindService(
            ComponentName(app, PlaybackService::class.java),
            controller.get().onBind(Intent(MediaSessionService.SERVICE_INTERFACE)),
        )
        return PlayerConnection(app).also { connection = it }
    }

    private fun awaitUntil(condition: () -> Boolean) {
        repeat(200) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condition not met")
    }
}
