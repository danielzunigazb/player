package com.danielzuniga.player.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.FakeMediaProvider
import com.danielzuniga.player.FakeSong
import com.google.common.util.concurrent.ListenableFuture
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

/** Browses the library the way Android Auto does. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class LibraryBrowseTest {

    private val app = ApplicationProvider.getApplicationContext<Application>()
    private lateinit var service: ServiceController<PlaybackService>
    private lateinit var browser: MediaBrowser

    @Before
    fun setUp() {
        FakeMediaProvider.install(
            listOf(
                FakeSong(1, "De Música Ligera", "Soda Stereo", "Canción Animal", albumId = 10, track = 1),
                FakeSong(2, "Un Millón de Años Luz", "Soda Stereo", "Canción Animal", albumId = 10, track = 2),
                FakeSong(3, "Eres", "Café Tacvba", "Cuatro Caminos", albumId = 20, track = 1),
            )
        )
        service = Robolectric.buildService(PlaybackService::class.java).create()
        val component = ComponentName(app, PlaybackService::class.java)
        shadowOf(app).setComponentNameAndServiceForBindService(
            component,
            service.get().onBind(Intent(MediaLibraryService.SERVICE_INTERFACE)),
        )
        browser = await(MediaBrowser.Builder(app, SessionToken(app, component)).buildAsync())
    }

    @After
    fun tearDown() {
        browser.release()
        service.destroy()
    }

    @Test
    fun rootListsTheFourSections() {
        val root = await(browser.getLibraryRoot(null)).value!!
        val sections = await(browser.getChildren(root.mediaId, 0, 100, null)).value!!
        assertEquals(listOf("Canciones", "Álbumes", "Artistas", "Playlists"), sections.map { it.mediaMetadata.title.toString() })
        assertTrue(sections.all { it.mediaMetadata.isBrowsable == true })
    }

    @Test
    fun albumsContainTheirSongsInTrackOrder() {
        val albums = await(browser.getChildren(LibraryBrowser.ALBUMS, 0, 100, null)).value!!
        assertEquals(listOf("Canción Animal", "Cuatro Caminos"), albums.map { it.mediaMetadata.title.toString() })

        val songs = await(browser.getChildren(albums.first().mediaId, 0, 100, null)).value!!
        assertEquals(listOf("De Música Ligera", "Un Millón de Años Luz"), songs.map { it.mediaMetadata.title.toString() })
        assertTrue(songs.all { it.mediaMetadata.isPlayable == true })
    }

    @Test
    fun pagesLargeLists() {
        val firstPage = await(browser.getChildren(LibraryBrowser.SONGS, 0, 2, null)).value!!
        val secondPage = await(browser.getChildren(LibraryBrowser.SONGS, 1, 2, null)).value!!
        assertEquals(2, firstPage.size)
        assertEquals(1, secondPage.size)
    }

    @Test
    fun pickingASongQueuesItsWholeAlbum() {
        val songs = await(browser.getChildren("${LibraryBrowser.ALBUM}10", 0, 100, null)).value!!
        browser.setMediaItem(songs[1])
        awaitUntil { browser.mediaItemCount == 2 }

        assertEquals(1, browser.currentMediaItemIndex)
        assertEquals("2", browser.currentMediaItem?.mediaId)
        assertEquals("1", browser.getMediaItemAt(0).mediaId)
    }

    @Test
    fun searchFindsSongsIgnoringAccents() {
        await(browser.search("musica", null))
        val results = await(browser.getSearchResult("musica", 0, 10, null)).value!!
        assertEquals(listOf("De Música Ligera"), results.map { it.mediaMetadata.title.toString() })
    }

    @Test
    fun voiceRequestPlaysMatchingSongs() {
        browser.setMediaItem(
            MediaItem.Builder()
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("eres").build())
                .build()
        )
        awaitUntil { browser.mediaItemCount == 1 }
        assertEquals("3", browser.currentMediaItem?.mediaId)
    }

    @Test
    fun unknownParentIsAnError() {
        val result = await(browser.getChildren("nope", 0, 10, null))
        assertFalse(result.resultCode == LibraryResult.RESULT_SUCCESS)
    }

    private fun <T> await(future: ListenableFuture<T>): T {
        awaitUntil { future.isDone }
        return future.get()
    }

    private fun awaitUntil(condition: () -> Boolean) {
        repeat(300) {
            shadowOf(Looper.getMainLooper()).idle()
            if (condition()) return
            Thread.sleep(10)
        }
        throw AssertionError("Condition not met")
    }
}
