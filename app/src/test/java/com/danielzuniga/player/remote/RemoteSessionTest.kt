package com.danielzuniga.player.remote

import android.content.Context
import android.os.Looper
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.LibraryIndex
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.time.Duration
import java.util.concurrent.TimeUnit

/** When the phone connects to its room, with a stand-in for the relay. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class RemoteSessionTest {

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val server = MockWebServer()
    private val scope = CoroutineScope(Dispatchers.Unconfined)
    private lateinit var player: ExoPlayer
    private lateinit var store: RemoteStore
    private lateinit var session: RemoteSession

    @Before
    fun setUp() {
        server.start()
        repeat(2) { server.enqueue(MockResponse().withWebSocketUpgrade(RelaySocket())) }
        player = ExoPlayer.Builder(context).build()
        store = RemoteStore(context)
        session = RemoteSession(
            context,
            player,
            store,
            OkHttpClient(),
            server.url("/").toString().removeSuffix("/").replace("http", "ws"),
            { LibraryIndex(emptyList()) },
            scope,
        )
        session.start()
    }

    @After
    fun tearDown() {
        session.stop()
        scope.cancel()
        player.release()
        server.shutdown()
    }

    /** Turns the main looper, advancing its clock, for [ms] of real time or until [done]. */
    private fun turnLooper(ms: Long = 500, done: () -> Boolean = { false }) {
        val deadline = System.currentTimeMillis() + ms
        while (!done() && System.currentTimeMillis() < deadline) {
            shadowOf(Looper.getMainLooper()).idleFor(Duration.ofMillis(50))
            Thread.sleep(5)
        }
    }

    private fun nextRoom(): String {
        turnLooper(5_000) { server.requestCount > 0 }
        val path = server.takeRequest(1, TimeUnit.SECONDS)!!.path!!
        return path.removePrefix("/room/").substringBefore("?role=phone")
    }

    @Test
    fun connectsOnlyWhileTheMonitorIsOnAndSomethingIsPaired() {
        store.setEnabled(true)
        turnLooper()
        assertEquals("on, nothing paired", 0, server.requestCount)

        store.setEnabled(false)
        val pairing = store.pairingOrCreate()
        turnLooper()
        assertEquals("paired, monitor off", 0, server.requestCount)

        store.setEnabled(true)
        assertEquals(pairing.room, nextRoom())
        turnLooper()
        assertEquals("one connection", 1, server.requestCount)
    }

    @Test
    fun unpairingAllMovesThePhoneToANewRoom() {
        store.setEnabled(true)
        val first = store.pairingOrCreate()
        assertEquals(first.room, nextRoom())

        store.unpairAll()
        val second = store.pairingOrCreate()

        assertNotEquals(first.room, second.room)
        assertNotEquals(first.key, second.key)
        turnLooper(5_000) { server.requestCount > 1 }
        assertEquals(second.room, nextRoom())
        assertEquals(2, server.requestCount)
    }
}
