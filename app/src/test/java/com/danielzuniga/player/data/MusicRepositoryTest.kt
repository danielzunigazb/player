package com.danielzuniga.player.data

import android.app.Application
import android.os.Looper
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.FakeMediaProvider
import com.danielzuniga.player.FakeSong
import com.danielzuniga.player.appContainer
import kotlinx.coroutines.launch
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MusicRepositoryTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private val container = app.appContainer

    @Test
    fun aRescanDoesNotHoldUpPlayback() {
        FakeMediaProvider.install(listOf(FakeSong(1, "De Música Ligera")))
        // The first scan opens Room: seconds on a cold JVM.
        awaitLibrary(timeoutMs = 30_000)

        val held = FakeMediaProvider.holdQueries()
        try {
            container.musicRepository.load(force = true)
            awaitUntil { container.musicRepository.isScanning.value }

            // A song picked while the library rescans plays with the library already scanned.
            assertEquals(listOf(1L), awaitLibrary().songs.map { it.id })
        } finally {
            held.countDown()
        }
        awaitUntil { !container.musicRepository.isScanning.value }
    }

    private fun awaitLibrary(timeoutMs: Long = 2_000): LibraryIndex {
        var library: LibraryIndex? = null
        container.appScope.launch { library = container.musicRepository.awaitLibrary() }
        awaitUntil(timeoutMs) { library != null }
        return library!!
    }

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
