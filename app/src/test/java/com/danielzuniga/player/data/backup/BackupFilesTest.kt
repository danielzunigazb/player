package com.danielzuniga.player.data.backup

import android.content.Context
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.danielzuniga.player.data.LibraryIndex
import com.danielzuniga.player.data.Song
import com.danielzuniga.player.data.UserDataRepository
import com.danielzuniga.player.data.db.AppDatabase
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupFilesTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val library = listOf(
        Song(1, "De Música Ligera", "Soda Stereo", "Canción Animal", 10, 210_000),
        Song(2, "Persiana Americana", "Soda Stereo", "Signos", 11, 280_000),
    )
    private lateinit var db: AppDatabase
    private lateinit var files: BackupFiles

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        files = BackupFiles(context.contentResolver, BackupRepository(db, { LibraryIndex(library) }, "1.6.1"))
    }

    @After
    fun tearDown() = db.close()

    private fun uri(file: File): Uri = Uri.fromFile(file)

    private suspend fun refusal(file: File): BackupException.Reason {
        try {
            files.read(uri(file))
        } catch (e: BackupException) {
            return e.reason
        }
        fail("should not be a backup")
        error("unreachable")
    }

    @Test
    fun whatIsWrittenToAFileComesBackFromIt() = runTest {
        val user = UserDataRepository(db)
        user.toggleFavorite(1)
        user.createPlaylist("Rock", listOf(2, 1))
        val file = folder.newFile("player-backup.json")

        val written = files.export(uri(file))

        assertEquals(BackupFiles.Written(favorites = 1, playlists = 1, songs = 2, skipped = 0), written)
        val read = files.read(uri(file))
        assertEquals(listOf("Persiana Americana", "De Música Ligera"), read.playlists.single().songs.map { it.title })
        // Same phone, same library: nothing in it is new.
        assertFalse(files.import(uri(file)).changed)
    }

    @Test
    fun writingOverALongerOlderBackupLeavesNoTailOfIt() = runTest {
        val file = folder.newFile("player-backup.json")
        file.writeText("x".repeat(100_000))

        files.export(uri(file))

        assertTrue(file.length() < 2_000)
        assertTrue(files.read(uri(file)).isEmpty)
    }

    @Test
    fun aFileThatIsNotABackupIsRefused() = runTest {
        val text = folder.newFile("notes.txt").apply { writeText("lista de la compra") }
        val other = folder.newFile("other.json").apply { writeText("""{"playlists":[]}""") }

        assertEquals(BackupException.Reason.NOT_A_BACKUP, refusal(text))
        assertEquals(BackupException.Reason.NOT_A_BACKUP, refusal(other))
    }

    @Test
    fun aFileOverTheLimitIsRefusedNotLoaded() = runTest {
        val huge = folder.newFile("huge.json")
        huge.outputStream().use { out ->
            val chunk = ByteArray(1024 * 1024) { 'a'.code.toByte() }
            repeat(Backup.MAX_BYTES / chunk.size + 1) { out.write(chunk) }
        }

        assertTrue(huge.length() > Backup.MAX_BYTES)
        assertEquals(BackupException.Reason.NOT_A_BACKUP, refusal(huge))
    }

    @Test
    fun aMissingFileFailsAsAnIoErrorNotAsABackupProblem() = runTest {
        try {
            files.read(uri(File(folder.root, "gone.json")))
            fail("should have failed")
        } catch (e: BackupException) {
            fail("a file that can't be opened is not a bad backup: ${e.reason}")
        } catch (_: java.io.IOException) {
            // the caller tells this apart from a file that isn't a backup
        }
    }
}
