package com.danielzuniga.player.data.db

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Opens a database exactly as version 1 left it and checks the upgrade keeps the user's data. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34])
class MigrationTest {

    @Test
    fun version1UpgradesKeepingFavoritesAndPlaylists() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "migration-test.db"
        context.deleteDatabase(name)

        // Schema and identity of version 1, as Room generated them.
        SQLiteDatabase.openOrCreateDatabase(context.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { v1 ->
            listOf(
                "CREATE TABLE IF NOT EXISTS `favorites` (`songId` INTEGER NOT NULL, `addedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))",
                "CREATE TABLE IF NOT EXISTS `playlists` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL, `createdAt` INTEGER NOT NULL)",
                "CREATE TABLE IF NOT EXISTS `playlist_songs` (`playlistId` INTEGER NOT NULL, `songId` INTEGER NOT NULL, `position` INTEGER NOT NULL, PRIMARY KEY(`playlistId`, `songId`), FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE )",
                "CREATE INDEX IF NOT EXISTS `index_playlist_songs_playlistId` ON `playlist_songs` (`playlistId`)",
                "CREATE TABLE IF NOT EXISTS `play_stats` (`songId` INTEGER NOT NULL, `playCount` INTEGER NOT NULL, `lastPlayedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))",
                "CREATE TABLE IF NOT EXISTS room_master_table (id INTEGER PRIMARY KEY,identity_hash TEXT)",
                "INSERT OR REPLACE INTO room_master_table (id,identity_hash) VALUES(42, '466f7a4fcf3df3b5cbbf0d294f4857ff')",
                "INSERT INTO favorites VALUES (7, 100)",
                "INSERT INTO playlists VALUES (1, 'Viaje', 100)",
                "INSERT INTO playlist_songs VALUES (1, 7, 0)",
            ).forEach(v1::execSQL)
            v1.version = 1
        }

        val db = AppDatabase.create(context, name)
        try {
            assertEquals(listOf(7L), db.favoriteDao().observeIds().first())
            assertEquals(listOf(7L), db.playlistDao().songIds(1))
            db.tagFixDao().upsert(TagFixEntity(7, TagFixEntity.FIXED, "24K", "T3R Elemento", 1))
            assertEquals("T3R Elemento", db.tagFixDao().observeAll().first().single().artist)
        } finally {
            db.close()
            context.deleteDatabase(name)
        }
    }
}
