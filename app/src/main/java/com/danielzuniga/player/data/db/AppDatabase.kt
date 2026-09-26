package com.danielzuniga.player.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        FavoriteEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
        PlayStatEntity::class,
        TagFixEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun favoriteDao(): FavoriteDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun playStatDao(): PlayStatDao
    abstract fun tagFixDao(): TagFixDao

    companion object {
        /** 2: tag_fixes, artist/title found for files without tags. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `tag_fixes` (`songId` INTEGER NOT NULL, `status` TEXT NOT NULL, " +
                        "`title` TEXT, `artist` TEXT, `checkedAt` INTEGER NOT NULL, PRIMARY KEY(`songId`))",
                )
            }
        }

        fun create(context: Context, name: String = "player.db"): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, name)
                .addMigrations(MIGRATION_1_2)
                .build()
    }
}
