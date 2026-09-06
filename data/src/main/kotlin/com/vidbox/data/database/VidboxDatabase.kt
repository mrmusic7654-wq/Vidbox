package com.vidbox.data.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [DownloadEntity::class], version = 1, exportSchema = true)
abstract class VidboxDatabase : RoomDatabase() {
    abstract fun downloads(): DownloadDao
}
