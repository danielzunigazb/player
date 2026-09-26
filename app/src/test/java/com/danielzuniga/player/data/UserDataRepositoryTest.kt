package com.danielzuniga.player.data

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.db.AppDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class UserDataRepositoryTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: UserDataRepository

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(
            ApplicationProvider.getApplicationContext<Context>(),
            AppDatabase::class.java,
        ).allowMainThreadQueries().build()
        repo = UserDataRepository(db)
    }

    @After
    fun tearDown() = db.close()

    @Test
    fun toggleFavoriteAddsThenRemoves() = runTest {
        repo.toggleFavorite(7)
        assertEquals(setOf(7L), repo.favoriteIdSet.first())
        repo.toggleFavorite(7)
        assertTrue(repo.favoriteIdSet.first().isEmpty())
    }

    @Test
    fun playlistKeepsOrderAndIgnoresDuplicates() = runTest {
        val id = repo.createPlaylist("  Rock  ", listOf(3, 1))
        repo.addToPlaylist(id, listOf(1, 5, 5, 2))

        assertEquals(listOf(3L, 1L, 5L, 2L), repo.playlistSongIds(id).first())
        val summary = repo.playlistSummaries.first().single()
        assertEquals("Rock", summary.name)
        assertEquals(4, summary.songCount)
    }

    @Test
    fun reorderRenameAndDeletePlaylist() = runTest {
        val id = repo.createPlaylist("Chill", listOf(1, 2, 3))
        repo.setPlaylistOrder(id, listOf(3, 1))
        repo.renamePlaylist(id, "Relax")

        assertEquals(listOf(3L, 1L), repo.playlistSongIds(id).first())
        assertEquals("Relax", repo.playlist(id).first()?.name)

        repo.deletePlaylist(id)
        assertNull(repo.playlist(id).first())
        assertTrue(repo.playlistSongIds(id).first().isEmpty())
    }

    @Test
    fun playStatsRankByCount() = runTest {
        repo.recordPlay(1)
        repo.recordPlay(2)
        repo.recordPlay(2)
        repo.recordPlay(3)

        assertEquals(2L, repo.mostPlayedIds().first().first())
        assertEquals(3L, repo.recentlyPlayedIds().first().first())
        assertEquals(3, repo.mostPlayedIds().first().size)
    }
}
