package com.vidbox.data.database

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** Restorable browser tab. Private tabs are never inserted here. */
@Entity(tableName = "tabs", indices = [Index("position")])
data class TabEntity(
    @PrimaryKey val id: String,
    val url: String?,
    val title: String?,
    val position: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val lastActiveAt: Long,
    val faviconPath: String?,
)

/** One visit of a page in a normal (non-private) tab. */
@Entity(tableName = "history", indices = [Index("visitedAt"), Index(value = ["url"], unique = true)])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String?,
    val visitedAt: Long,
    val visitCount: Int,
    val faviconPath: String?,
)

@Entity(tableName = "bookmarks", indices = [Index("folder"), Index(value = ["url"], unique = true), Index("position")])
data class BookmarkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val folder: String?,
    val position: Int,
    val createdAt: Long,
    val faviconPath: String?,
)

/** A site the user excluded from content blocking. */
@Entity(tableName = "blocking_allowlist")
data class AllowlistEntity(
    @PrimaryKey val host: String,
    val createdAt: Long,
)
