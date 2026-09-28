package com.danielzuniga.player.playback

import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.os.Looper
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaBrowser
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession.MediaItemsWithStartPosition
import androidx.media3.session.SessionError
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.FakeMediaProvider
import com.danielzuniga.player.FakeSong
import com.danielzuniga.player.R
import com.danielzuniga.player.appContainer
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.launch
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
    /** Errors the session sent to [browser]. */
    private val errors = mutableListOf<SessionError>()

    @Before
    fun setUp() {
        FakeMediaProvider.install(
            listOf(
                FakeSong(1, "De Música Ligera", "Soda Stereo", "Canción Animal", albumId = 10, track = 1),
                FakeSong(2, "Un Millón de Años Luz", "Soda Stereo", "Canción Animal", albumId = 10, track = 2),
                FakeSong(3, "Eres", "Café Tacvba", "Cuatro Caminos", albumId = 20, track = 1),
                // A "|" in the name used to be read as the separator before a song id.
                FakeSong(4, "All the Small Things", "Blink|182", "Enema of the State", albumId = 30, track = 1),
                FakeSong(5, "What's My Age Again?", "Blink|182", "Enema of the State", albumId = 30, track = 2),
            )
        )
        service = Robolectric.buildService(PlaybackService::class.java).create()
        val component = ComponentName(app, PlaybackService::class.java)
        shadowOf(app).setComponentNameAndServiceForBindService(
            component,
            service.get().onBind(Intent(MediaLibraryService.SERVICE_INTERFACE)),
        )
        val listener = object : MediaBrowser.Listener {
            override fun onError(controller: MediaController, sessionError: SessionError) {
                errors += sessionError
            }
        }
        browser = await(MediaBrowser.Builder(app, SessionToken(app, component)).setListener(listener).buildAsync())
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
        assertEquals(listOf("Canción Animal", "Cuatro Caminos", "Enema of the State"), albums.map { it.mediaMetadata.title.toString() })

        val songs = await(browser.getChildren(albums.first().mediaId, 0, 100, null)).value!!
        assertEquals(listOf("De Música Ligera", "Un Millón de Años Luz"), songs.map { it.mediaMetadata.title.toString() })
        assertTrue(songs.all { it.mediaMetadata.isPlayable == true })
    }

    @Test
    fun pagesLargeLists() {
        val firstPage = await(browser.getChildren(LibraryBrowser.SONGS, 0, 2, null)).value!!
        val lastPage = await(browser.getChildren(LibraryBrowser.SONGS, 2, 2, null)).value!!
        assertEquals(2, firstPage.size)
        assertEquals(1, lastPage.size)
    }

    @Test
    fun artistNamesWithABarBrowseAndQueue() {
        val artists = await(browser.getChildren(LibraryBrowser.ARTISTS, 0, 100, null)).value!!
        val blink = artists.single { it.mediaMetadata.title.toString() == "Blink|182" }
        // The node is the artist, not "song 182".
        assertEquals(null, LibraryBrowser.songIdOf(blink.mediaId))
        val songs = await(browser.getChildren(blink.mediaId, 0, 100, null)).value!!
        assertEquals(listOf("4", "5"), songs.map { LibraryBrowser.songIdOf(it.mediaId).toString() })

        browser.setMediaItem(songs[1])
        awaitUntil { browser.mediaItemCount == 2 }
        assertEquals("5", browser.currentMediaItem?.mediaId)
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
        // The browser shows the placeholder item at once; wait for the session's resolved queue.
        awaitUntil { browser.currentMediaItem?.mediaId == "3" }
        assertEquals(1, browser.mediaItemCount)
    }

    @Test
    fun voiceRequestWithNoMatchFails() {
        val request = MediaItem.Builder()
            .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("zzz").build())
            .build()
        // Nothing to play: the request fails instead of reaching the player without a URI.
        assertTrue(resolveQueue(listOf(request), 0).isFailure)
        assertTrue(resolveQueue(listOf(MediaItem.Builder().setMediaId(LibraryBrowser.ALBUMS).build()), 0).isFailure)
    }

    @Test
    fun aVoiceRequestWithNoMatchIsReported() {
        browser.setMediaItem(
            MediaItem.Builder()
                .setRequestMetadata(MediaItem.RequestMetadata.Builder().setSearchQuery("zzz").build())
                .build()
        )
        // Android Auto shows the message; without it the request just did nothing.
        awaitUntil { errors.isNotEmpty() }
        assertEquals(SessionError.ERROR_BAD_VALUE, errors.single().code)
        assertEquals(app.getString(R.string.nothing_to_play), errors.single().message)
    }

    @Test
    fun addingItemsThatCantPlay() {
        val song = { id: String -> MediaItem.Builder().setMediaId(id).build() }
        val folder = MediaItem.Builder().setMediaId(LibraryBrowser.ALBUMS).build()
        browser.setMediaItem(song("1"))
        awaitUntil { browser.mediaItemCount == 1 && browser.currentMediaItem?.localConfiguration != null }

        // The folder is dropped; the song is added.
        browser.addMediaItems(listOf(folder, song("3")))
        awaitUntil { browser.mediaItemCount == 2 }
        assertEquals(listOf("1", "3"), (0 until 2).map { browser.getMediaItemAt(it).mediaId })

        // Only the folder: nothing is added and the caller hears why.
        browser.addMediaItem(folder)
        awaitUntil { errors.isNotEmpty() }
        assertEquals(SessionError.ERROR_BAD_VALUE, errors.single().code)
    }

    @Test
    fun itemsThatCantPlayAreDropped() {
        val items = listOf("1", LibraryBrowser.ALBUMS, "3").map { MediaItem.Builder().setMediaId(it).build() }
        val queue = resolveQueue(items, 2).getOrThrow()
        assertEquals(listOf("1", "3"), queue.mediaItems.map { it.mediaId })
        assertEquals(1, queue.startIndex)
    }

    @Test
    fun theStartPositionStaysWithTheChosenSong() {
        val song = { id: String -> MediaItem.Builder().setMediaId(id).build() }
        val folder = MediaItem.Builder().setMediaId(LibraryBrowser.ALBUMS).build()

        // The chosen item can't play: the next song starts from its beginning.
        browser.setMediaItems(listOf(song("1"), folder, song("3")), 1, 5_000)
        awaitUntil { browser.mediaItemCount == 2 }
        assertEquals("3", browser.currentMediaItem?.mediaId)
        assertEquals(0L, browser.currentPosition)

        // The chosen song moves up a place and keeps its position.
        browser.setMediaItems(listOf(folder, song("1")), 1, 5_000)
        awaitUntil { browser.mediaItemCount == 1 }
        assertEquals("1", browser.currentMediaItem?.mediaId)
        assertEquals(5_000L, browser.currentPosition)
    }

    @Test
    fun unknownParentIsAnError() {
        val result = await(browser.getChildren("nope", 0, 10, null))
        assertFalse(result.resultCode == LibraryResult.RESULT_SUCCESS)
    }

    private fun resolveQueue(items: List<MediaItem>, startIndex: Int): Result<MediaItemsWithStartPosition> {
        val container = app.appContainer
        var result: Result<MediaItemsWithStartPosition>? = null
        container.appScope.launch { result = runCatching { LibraryBrowser(app, container).resolveQueue(items, startIndex, C.TIME_UNSET) } }
        awaitUntil { result != null }
        return result!!
    }

    private fun <T> await(future: ListenableFuture<T>): T {
        awaitUntil { future.isDone }
        return future.get()
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
