package com.danielzuniga.player.playback

import android.app.Application
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import kotlin.random.Random

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlayOrPauseTest {

    private val exo = ExoPlayer.Builder(ApplicationProvider.getApplicationContext<Application>()).build()

    /**
     * Robolectric can't play the songs to their end, so this one says they did. It refuses a
     * negative index as MediaController does (ExoPlayer lets it pass).
     */
    private val ended = object : ForwardingPlayer(exo) {
        override fun getPlaybackState(): Int = Player.STATE_ENDED

        override fun seekToDefaultPosition(mediaItemIndex: Int) {
            require(mediaItemIndex >= 0) { "MediaController throws on index $mediaItemIndex" }
            super.seekToDefaultPosition(mediaItemIndex)
        }
    }

    @After
    fun tearDown() = exo.release()

    @Test
    fun anEndedShuffledQueueStartsOverFromItsFirstSong() {
        exo.setMediaItems((1..6).map { MediaItem.fromUri("content://media/external/audio/media/$it") })
        exo.setShuffleOrder(QueueShuffleOrder.startingWith(first = 3, length = 6, random = Random(1)))
        exo.shuffleModeEnabled = true
        // Ended on the last song in play order.
        exo.seekToDefaultPosition(exo.currentTimeline.getLastWindowIndex(true))

        ended.playOrPause()

        assertEquals(3, exo.currentMediaItemIndex)
        assertTrue(exo.playWhenReady)
    }

    @Test
    fun anEndedEmptyQueueDoesNotCrash() {
        ended.playOrPause()

        assertTrue(exo.currentTimeline.isEmpty)
        assertTrue(exo.playWhenReady)
    }
}
