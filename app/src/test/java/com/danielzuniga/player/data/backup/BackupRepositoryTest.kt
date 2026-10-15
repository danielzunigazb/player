package com.danielzuniga.player.data.backup

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.UserDataRepository
import com.danielzuniga.player.data.backup.BackupRepository.ImportResult
import com.danielzuniga.player.data.db.AppDatabase
import com.danielzuniga.player.data.db.PlayStatEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Export from one phone, import on another: same songs, different ids. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupRepositoryTest {

    private fun song(id: Long, title: String, artist: String = "Soda Stereo", album: String = "Canción Animal") =
        Song(id, title, artist, album, albumId = 10, durationMs = 200_000 + id)

    // The old phone's library, and the new one's: the same songs under other ids, plus one extra.
    private val oldPhone = listOf(song(1, "De Música Ligera"), song(2, "Persiana Americana"), song(3, "Trátame Suavemente"))
    private val newPhone = listOf(
        song(901, "Persiana Americana").copy(durationMs = 200_002),
        song(902, "Otra Que Ya Tenía"),
        song(903, "De Música Ligera").copy(durationMs = 200_001),
        song(904, "Trátame Suavemente").copy(durationMs = 200_003),
    )

    private val databases = mutableListOf<AppDatabase>()

    private fun database() = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext<Context>(),
        AppDatabase::class.java,
    ).allowMainThreadQueries().build().also(databases::add)

    @After
    fun tearDown() = databases.forEach(AppDatabase::close)

    private fun repo(db: AppDatabase, library: List<Song>, clock: () -> Long = { 1_000L }) =
        BackupRepository(db, { LibraryIndex(library) }, appVersion = "1.6.1", clock = clock)

    /** The old phone, with things saved. */
    private suspend fun filledOldPhone(): AppDatabase {
        val db = database()
        val user = UserDataRepository(db)
        user.toggleFavorite(2)
        user.toggleFavorite(1)
        user.createPlaylist("Rock", listOf(3, 1, 2))
        user.createPlaylist("Vacía")
        db.playStatDao().upsert(PlayStatEntity(1, playCount = 12, lastPlayedAt = 5_000))
        db.playStatDao().upsert(PlayStatEntity(2, playCount = 3, lastPlayedAt = 9_000))
        return db
    }

    private suspend fun AppDatabase.playlistTitles(library: List<Song>, name: String): List<String> {
        val id = playlistDao().idByName(name) ?: return emptyList()
        return playlistDao().songIds(id).map { songId -> library.first { it.id == songId }.title }
    }

    private suspend fun AppDatabase.favoriteTitles(library: List<Song>) =
        favoriteDao().all().map { fav -> library.first { it.id == fav.songId }.title }

    @Test
    fun exportsWhatIsSavedByNameInTheOrderItWasSaved() = runTest {
        val export = repo(filledOldPhone(), oldPhone).export()
        val backup = export.backup

        assertEquals(0, export.skipped)
        assertEquals("1.6.1", backup.app)
        assertEquals(1_000L, backup.exportedAt)
        assertEquals(listOf("De Música Ligera", "Persiana Americana"), backup.favorites.map { it.song.title }.sorted())
        assertEquals(
            listOf("Trátame Suavemente", "De Música Ligera", "Persiana Americana"),
            backup.playlists.first { it.name == "Rock" }.songs.map { it.title },
        )
        assertTrue(backup.playlists.first { it.name == "Vacía" }.songs.isEmpty())
        assertEquals(listOf(12, 3), backup.stats.sortedBy { it.song.title }.map { it.playCount })
    }

    @Test
    fun aSongOutOfTheLibraryRightNowIsLeftOutAndCountedOnceHoweverOftenItIsSaved() = runTest {
        val db = filledOldPhone()
        UserDataRepository(db).apply {
            toggleFavorite(99)
            createPlaylist("Viaje", listOf(99, 1))
            db.playStatDao().upsert(PlayStatEntity(99, 1, 1))
        }

        val export = repo(db, oldPhone).export()

        assertEquals(1, export.skipped)
        assertEquals(listOf("De Música Ligera"), export.backup.playlists.first { it.name == "Viaje" }.songs.map { it.title })
        assertEquals(2, export.backup.favorites.size)
    }

    @Test
    fun theBackupSurvivesBeingWrittenAndReadAndBringsEverythingToAPhoneWithOtherIds() = runTest {
        val written = repo(filledOldPhone(), oldPhone).export().backup.toJson()

        val newDb = database()
        val result = repo(newDb, newPhone).import(Backup.parse(written))

        assertEquals(ImportResult(favoritesAdded = 2, playlistsCreated = 2, songsAdded = 3, statsUpdated = 2), result)
        assertEquals(setOf("De Música Ligera", "Persiana Americana"), newDb.favoriteTitles(newPhone).toSet())
        assertEquals(listOf("Trátame Suavemente", "De Música Ligera", "Persiana Americana"), newDb.playlistTitles(newPhone, "Rock"))
        assertEquals(emptyList<String>(), newDb.playlistTitles(newPhone, "Vacía"))
        assertEquals(1, newDb.playlistDao().all().count { it.name == "Vacía" })
        assertEquals(12, newDb.playStatDao().get(903)!!.playCount)
        assertEquals(9_000L, newDb.playStatDao().get(901)!!.lastPlayedAt)
    }

    @Test
    fun importingTheSameFileAgainChangesNothing() = runTest {
        val backup = repo(filledOldPhone(), oldPhone).export().backup
        val newDb = database()
        val importer = repo(newDb, newPhone)

        assertTrue(importer.import(backup).changed)
        val before = Triple(newDb.favoriteDao().all(), newDb.playlistDao().allSongs(), newDb.playStatDao().all())

        val again = importer.import(backup)

        assertFalse(again.changed)
        assertEquals(ImportResult(), again)
        assertEquals(before, Triple(newDb.favoriteDao().all(), newDb.playlistDao().allSongs(), newDb.playStatDao().all()))
        assertEquals(2, newDb.playlistDao().all().size)
    }

    @Test
    fun importAddsToWhatIsThereAndNeverReplacesIt() = runTest {
        val newDb = database()
        val user = UserDataRepository(newDb)
        user.toggleFavorite(903) // De Música Ligera, already a favorite here
        val before = newDb.favoriteDao().all().single()
        user.createPlaylist("rock", listOf(902, 903)) // same name in another case, other songs
        user.createPlaylist("Mi lista", listOf(904))
        newDb.playStatDao().upsert(PlayStatEntity(901, playCount = 50, lastPlayedAt = 1))
        newDb.playStatDao().upsert(PlayStatEntity(903, playCount = 1, lastPlayedAt = 99_000))

        val result = repo(newDb, newPhone).import(repo(filledOldPhone(), oldPhone).export().backup)

        // The favorite already there keeps its own date; only the other one is new.
        assertEquals(1, result.favoritesAdded)
        assertEquals(before, newDb.favoriteDao().all().first { it.songId == 903L })
        // "Rock" kept what it had and got the rest at the end, in the saved order; no second Rock.
        assertEquals(1, result.playlistsCreated) // only "Vacía" is new
        assertEquals(1, result.playlistsUpdated)
        assertEquals(
            listOf("Otra Que Ya Tenía", "De Música Ligera", "Trátame Suavemente", "Persiana Americana"),
            newDb.playlistTitles(newPhone, "rock"),
        )
        assertEquals(listOf("Trátame Suavemente"), newDb.playlistTitles(newPhone, "Mi lista"))
        // Play counts keep the higher; the last play keeps the later.
        assertEquals(50, newDb.playStatDao().get(901)!!.playCount)
        assertEquals(12, newDb.playStatDao().get(903)!!.playCount)
        assertEquals(99_000L, newDb.playStatDao().get(903)!!.lastPlayedAt)
    }

    @Test
    fun aPlaylistWithNothingOfItOnThisPhoneIsNotCreatedAndTheMissingSongsAreCounted() = runTest {
        val backup = repo(filledOldPhone(), oldPhone).export().backup
        val strangers = listOf(song(1, "Otra Canción", artist = "Otro"))
        val newDb = database()

        val result = repo(newDb, strangers).import(backup)

        assertEquals(3, result.missing)
        // Only the empty playlist: it had nothing to bring.
        assertEquals(listOf("Vacía"), newDb.playlistDao().all().map { it.name })
        assertEquals(ImportResult(playlistsCreated = 1, missing = 3), result)
    }

    @Test
    fun aSongSavedInSeveralPlacesIsCountedMissingOnce() = runTest {
        val ghost = SongRef("Fantasma", "Nadie", "", 100_000)
        val backup = Backup(
            exportedAt = 1,
            app = "1.6.1",
            favorites = listOf(Backup.Favorite(ghost, 1)),
            playlists = listOf(Backup.Playlist("A", 1, listOf(ghost)), Backup.Playlist("B", 1, listOf(ghost))),
            stats = listOf(Backup.Stat(ghost, 4, 4)),
        )

        assertEquals(1, repo(database(), newPhone).import(backup).missing)
    }

    @Test
    fun aBlankNameGetsOneAndAPlayCountOfNothingIsNotRestored() = runTest {
        val ligera = SongRef("De Música Ligera", "Soda Stereo", "", 200_001)
        val backup = Backup(
            exportedAt = 1,
            app = "1.6.1",
            playlists = listOf(Backup.Playlist("   ", 0, listOf(ligera))),
            stats = listOf(Backup.Stat(ligera, -5, -1)),
        )
        val newDb = database()

        repo(newDb, newPhone).import(backup)

        assertEquals(listOf("Playlist"), newDb.playlistDao().all().map { it.name })
        // Dates of 0 or less aren't dates: the playlist gets today's.
        assertEquals(1_000L, newDb.playlistDao().all().single().createdAt)
        assertNull(newDb.playStatDao().get(903))
    }

    @Test
    fun ifSomethingFailsMidwayNothingOfTheFileIsKept() = runTest {
        val ligera = SongRef("De Música Ligera", "Soda Stereo", "", 200_001)
        val persiana = SongRef("Persiana Americana", "Soda Stereo", "", 200_002)
        // The second favorite has no date, so the import asks the clock; it fails right then.
        val backup = Backup(
            exportedAt = 1,
            app = "1.6.1",
            favorites = listOf(Backup.Favorite(ligera, 5), Backup.Favorite(persiana, 0)),
        )
        val newDb = database()

        try {
            repo(newDb, newPhone, clock = { error("the clock broke") }).import(backup)
            fail("should have failed")
        } catch (e: IllegalStateException) {
            assertEquals("the clock broke", e.message)
        }

        assertTrue(newDb.favoriteDao().all().isEmpty())
    }

    @Test
    fun exportThenImportOnTheSamePhoneIsNothingNew() = runTest {
        val db = filledOldPhone()
        val sameLibrary = repo(db, oldPhone)

        assertFalse(sameLibrary.import(sameLibrary.export().backup).changed)
        assertEquals(setOf(1L, 2L), UserDataRepository(db).favoriteIdSet.first())
    }
}
