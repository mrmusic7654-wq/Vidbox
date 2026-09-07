package com.vidbox.data.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [DownloadEntity::class, TabEntity::class, HistoryEntity::class, BookmarkEntity::class, AllowlistEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class VidboxDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
    abstract fun tabs(): TabDao
    abstract fun history(): HistoryDao
    abstract fun bookmarks(): BookmarkDao
    abstract fun allowlist(): AllowlistDao

    companion object {
        /** Adds the browser tables. The downloads table is untouched, so no row is ever lost. */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `tabs` (`id` TEXT NOT NULL, `url` TEXT, `title` TEXT, `position` INTEGER NOT NULL, " +
                    "`isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `lastActiveAt` INTEGER NOT NULL, `faviconPath` TEXT, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_tabs_position` ON `tabs` (`position`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `history` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, " +
                    "`title` TEXT, `visitedAt` INTEGER NOT NULL, `visitCount` INTEGER NOT NULL, `faviconPath` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_history_visitedAt` ON `history` (`visitedAt`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_history_url` ON `history` (`url`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `bookmarks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `url` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, `folder` TEXT, `position` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `faviconPath` TEXT)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_folder` ON `bookmarks` (`folder`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_url` ON `bookmarks` (`url`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_bookmarks_position` ON `bookmarks` (`position`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `blocking_allowlist` (`host` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, PRIMARY KEY(`host`))")
            }
        }
        val MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2)
    }
}
