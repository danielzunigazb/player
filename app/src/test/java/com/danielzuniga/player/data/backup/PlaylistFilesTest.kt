package com.danielzuniga.player.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.UserDataRepository
import com.danielzuniga.player.data.backup.BackupRepository.ImportResult
import com.danielzuniga.player.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/** Playlists out to M3U and in from M3U, between phones with other ids and other folders. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class PlaylistFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val databases = mutableListOf<AppDatabase>()

    private fun song(id: Long, title: String, artist: String = "Soda Stereo", path: String = "") =
        Song(id, title, artist, "Álbum", 1, 200_000 + id, path = path)

    private val oldPhone = listOf(
        song(1, "De Música Ligera", path = "/storage/emulated/0/Music/De Música Ligera.mp3"),
        song(2, "Persiana Americana", path = "/storage/emulated/0/Music/Persiana Americana.mp3"),
        song(3, "Hola - Adiós", artist = "", path = "/storage/emulated/0/Music/Hola - Adiós.mp3"),
    )
    private val newPhone = listOf(
        song(901, "Persiana Americana").copy(durationMs = 200_002),
        song(902, "Otra Que Ya Tenía"),
        song(903, "De Música Ligera").copy(durationMs = 200_001),
        song(904, "Hola - Adiós", artist = "").copy(durationMs = 200_003),
    )

    private fun database() = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
        .allowMainThreadQueries().build().also(databases::add)

    @After
    fun tearDown() = databases.forEach(AppDatabase::close)

    private fun files(db: AppDatabase, library: List<Song>) = PlaylistFiles(
        context.contentResolver,
        BackupRepository(db, { LibraryIndex(library) }, "1.6.1"),
        { library },
        clock = { 1_000L },
    )

    private fun uri(file: File): Uri = Uri.fromFile(file)

    private fun file(name: String, content: String) = folder.newFile(name).apply { writeText(content) }

    private suspend fun AppDatabase.titles(library: List<Song>, name: String): List<String>? {
        val id = playlistDao().idByName(name) ?: return null
        return playlistDao().songIds(id).map { songId -> library.first { it.id == songId }.title }
    }

    private suspend fun refusal(db: AppDatabase, file: File): Boolean {
        try {
            files(db, newPhone).import(uri(file))
        } catch (_: NoPlaylistException) {
            return true
        }
        return false
    }

    @Test
    fun aPlaylistLeavesOnePhoneAndArrivesOnAnotherInTheSameOrder() = runTest {
        val out = folder.newFile("Rock.m3u8")
        val written = files(database(), oldPhone).export(uri(out), "Rock", listOf(oldPhone[2], oldPhone[0], oldPhone[1]))
        assertEquals(3, written)
        assertTrue(out.readText().startsWith("#EXTM3U\n#PLAYLIST:Rock\n"))

        val newDb = database()
        val result = files(newDb, newPhone).import(uri(out))

        assertEquals(ImportResult(playlistsCreated = 1, songsAdded = 3), result)
        // "Hola - Adiós" has a dash in its own title: read whole, it's found.
        assertEquals(listOf("Hola - Adiós", "De Música Ligera", "Persiana Americana"), newDb.titles(newPhone, "Rock"))
    }

    @Test
    fun aFileFromAnotherProgramWithOnlyFileNamesIsFoundByThem() = runTest {
        val m3u = file(
            "Del PC.m3u",
            "C:\\Music\\Soda Stereo\\01 - Soda Stereo - De Música Ligera.mp3\r\n" +
                "C:\\Music\\Soda Stereo\\Persiana Americana.flac\r\n",
        )
        val newDb = database()

        val result = files(newDb, newPhone).import(uri(m3u))

        assertEquals(ImportResult(playlistsCreated = 1, songsAdded = 2), result)
        // No #PLAYLIST: the playlist is named after the file.
        assertEquals(listOf("De Música Ligera", "Persiana Americana"), newDb.titles(newPhone, "Del PC"))
    }

    @Test
    fun anOldLatin1FileIsReadToo() = runTest {
        val m3u = folder.newFile("viejo.m3u")
        m3u.writeBytes("#EXTINF:200,Soda Stereo - De M\u00fasica Ligera\nx.mp3\n".toByteArray(Charsets.ISO_8859_1))
        val newDb = database()

        assertEquals(1, files(newDb, newPhone).import(uri(m3u)).songsAdded)
        assertEquals(listOf("De Música Ligera"), newDb.titles(newPhone, "viejo"))
    }

    @Test
    fun songsThePhoneDoesNotHaveAreCountedAndTheRestIsStillImported() = runTest {
        val m3u = file("Mezcla.m3u8", "#EXTM3U\n#EXTINF:200,Soda Stereo - Persiana Americana\na.mp3\n#EXTINF:200,Nadie - Fantasma\nb.mp3\n")
        val newDb = database()

        val result = files(newDb, newPhone).import(uri(m3u))

        assertEquals(ImportResult(playlistsCreated = 1, songsAdded = 1, missing = 1), result)
        assertEquals(listOf("Persiana Americana"), newDb.titles(newPhone, "Mezcla"))
    }

    @Test
    fun aPlaylistOfNothingThePhoneHasIsNotCreatedButSaysSo() = runTest {
        val m3u = file("Ajena.m3u8", "#EXTINF:200,Nadie - Uno\na.mp3\n#EXTINF:200,Nadie - Dos\nb.mp3\n")
        val newDb = database()

        val result = files(newDb, newPhone).import(uri(m3u))

        assertEquals(ImportResult(missing = 2), result)
        assertTrue(newDb.playlistDao().all().isEmpty())
    }

    @Test
    fun importingTheSameFileTwiceChangesNothingAndAPlaylistWithTheNameGetsWhatItLacks() = runTest {
        val m3u = file("Rock.m3u8", "#PLAYLIST:Rock\n#EXTINF:200,Soda Stereo - De Música Ligera\na\n#EXTINF:200,Soda Stereo - Persiana Americana\nb\n")
        val newDb = database()
        UserDataRepository(newDb).createPlaylist("rock", listOf(902, 903))
        val importer = files(newDb, newPhone)

        val first = importer.import(uri(m3u))
        val second = importer.import(uri(m3u))

        assertEquals(ImportResult(playlistsUpdated = 1, songsAdded = 1), first)
        assertEquals(ImportResult(), second)
        assertEquals(listOf("Otra Que Ya Tenía", "De Música Ligera", "Persiana Americana"), newDb.titles(newPhone, "rock"))
        assertEquals(1, newDb.playlistDao().all().size)
    }

    @Test
    fun whatIsNotAPlaylistIsRefused() = runTest {
        val db = database()
        assertTrue(refusal(db, file("vacio.m3u", "")))
        assertTrue(refusal(db, file("solo-comentarios.m3u", "#EXTM3U\n# nada que ver\n")))
        val binary = folder.newFile("song.mp3").apply { writeBytes(byteArrayOf(0x49, 0x44, 0x33, 0x03, 0x00, 0x00, 0x00, 0x7f)) }
        assertTrue(refusal(db, binary))
        assertTrue(db.playlistDao().all().isEmpty())
    }

    @Test
    fun aFileTooBigToBeAPlaylistIsRefusedNotLoaded() = runTest {
        val huge = folder.newFile("enorme.m3u")
        huge.outputStream().use { out ->
            val chunk = ByteArray(1024 * 1024) { 'a'.code.toByte() }
            repeat(9) { out.write(chunk) }
        }
        assertTrue(refusal(database(), huge))
    }

    @Test
    fun writingOverALongerOldFileLeavesNoTailAndAMissingFileIsAnIoError() = runTest {
        val out = folder.newFile("lista.m3u8").apply { writeText("x".repeat(50_000)) }
        files(database(), oldPhone).export(uri(out), "Corta", listOf(oldPhone[0]))
        assertTrue(out.length() < 500)

        try {
            files(database(), newPhone).import(uri(File(folder.root, "gone.m3u8")))
            fail("should have failed")
        } catch (_: NoPlaylistException) {
            fail("a file that can't be opened is not 'not a playlist'")
        } catch (_: java.io.IOException) {
            // the caller tells this apart from a file that isn't a playlist
        }
    }
}
