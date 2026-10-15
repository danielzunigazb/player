package com.danielzuniga.player.data.backup

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class BackupTest {

    private val ligera = SongRef("De Música Ligera", "Soda Stereo", "Canción Animal", 210_000)
    private val persiana = SongRef("Persiana Americana", "Soda Stereo", "Signos", 280_000)

    private val backup = Backup(
        exportedAt = 1_790_000_000_000,
        app = "1.6.1",
        favorites = listOf(Backup.Favorite(ligera, 5)),
        playlists = listOf(
            Backup.Playlist("Rock", 7, listOf(persiana, ligera)),
            Backup.Playlist("Vacía", 8, emptyList()),
        ),
        stats = listOf(Backup.Stat(persiana, 12, 99)),
    )

    private fun refusal(text: String): BackupException.Reason {
        try {
            Backup.parse(text)
        } catch (e: BackupException) {
            return e.reason
        }
        fail("should not be a backup: $text")
        error("unreachable")
    }

    @Test
    fun whatIsWrittenIsReadBackAsItWas() {
        assertEquals(backup, Backup.parse(backup.toJson()))
    }

    @Test
    fun titlesWithQuotesNewlinesAndEmojiSurvive() {
        val odd = SongRef("\"Quoted\" \\ back\nslash 🎵 ñandú", "A, B & C", "", 0)
        val read = Backup.parse(backup.copy(favorites = listOf(Backup.Favorite(odd, 1))).toJson())
        assertEquals(odd, read.favorites.single().song)
    }

    @Test
    fun aFileWithOnlyTheHeaderIsAnEmptyBackup() {
        val read = Backup.parse("""{"format":"player-backup","version":1}""")
        assertTrue(read.isEmpty)
        assertFalse(backup.isEmpty)
    }

    @Test
    fun fieldsItDoesNotKnowAreIgnored() {
        val json = JSONObject(backup.toJson()).put("note", "from a later Player").put("tags", listOf("x"))
        json.getJSONArray("favorites").getJSONObject(0).put("color", "red")
        assertEquals(backup, Backup.parse(json.toString()))
    }

    @Test
    fun aMissingArtistOrAnExplicitNullIsBlankNotTheWordNull() {
        val read = Backup.parse(
            """{"format":"player-backup","version":1,"favorites":[
                {"song":{"title":"Solo título","artist":null,"durationMs":1000},"addedAt":3}]}"""
        )
        assertEquals(SongRef("Solo título", "", "", 1000), read.favorites.single().song)
    }

    @Test
    fun whatIsNotABackupIsRefused() {
        assertEquals(BackupException.Reason.NOT_A_BACKUP, refusal(""))
        assertEquals(BackupException.Reason.NOT_A_BACKUP, refusal("not json at all"))
        assertEquals(BackupException.Reason.NOT_A_BACKUP, refusal("[1, 2, 3]"))
        assertEquals(BackupException.Reason.NOT_A_BACKUP, refusal("""{"playlists":[]}"""))
        assertEquals(BackupException.Reason.NOT_A_BACKUP, refusal("""{"format":"other-app","version":1}"""))
    }

    @Test
    fun aLaterVersionIsRefusedRatherThanHalfRead() {
        assertEquals(BackupException.Reason.TOO_NEW, refusal("""{"format":"player-backup","version":2}"""))
    }

    @Test
    fun aBackupMissingSomethingItNeedsIsBrokenNotSilentlyShorter() {
        val head = """{"format":"player-backup","version":1,"""
        assertEquals(BackupException.Reason.BROKEN, refusal(head + """"favorites":[{"song":{"artist":"x"},"addedAt":1}]}"""))
        assertEquals(BackupException.Reason.BROKEN, refusal(head + """"favorites":[{"addedAt":1}]}"""))
        assertEquals(BackupException.Reason.BROKEN, refusal(head + """"playlists":[{"songs":[]}]}"""))
        assertEquals(BackupException.Reason.BROKEN, refusal(head + """"stats":["nope"]}"""))
    }
}
