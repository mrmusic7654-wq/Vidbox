package com.vidbox.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TabDao {
    @Query("SELECT * FROM tabs ORDER BY position ASC")
    fun observeAll(): Flow<List<TabEntity>>

    @Query("SELECT * FROM tabs ORDER BY position ASC")
    suspend fun all(): List<TabEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(tab: TabEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(tabs: List<TabEntity>)

    @Query("DELETE FROM tabs WHERE id = :id")
    suspend fun delete(id: String)

    @Query("DELETE FROM tabs")
    suspend fun clear()

    @Query("DELETE FROM tabs WHERE id NOT IN (:ids)")
    suspend fun deleteOthers(ids: List<String>)

    /** Full replacement, so ordering and the active flag stay consistent in one transaction. */
    @Transaction
    suspend fun replaceAll(tabs: List<TabEntity>) {
        if (tabs.isEmpty()) clear() else deleteOthers(tabs.map { it.id })
        if (tabs.isNotEmpty()) upsertAll(tabs)
    }
}

@Dao
interface HistoryDao {
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("""SELECT * FROM history
        WHERE url LIKE :query ESCAPE '\' OR title LIKE :query ESCAPE '\'
        ORDER BY visitedAt DESC LIMIT :limit""")
    fun search(query: String, limit: Int): Flow<List<HistoryEntity>>

    @Query("""SELECT * FROM history
        WHERE url LIKE :query ESCAPE '\' OR title LIKE :query ESCAPE '\'
        ORDER BY visitCount DESC, visitedAt DESC LIMIT :limit""")
    suspend fun suggest(query: String, limit: Int): List<HistoryEntity>

    @Query("SELECT * FROM history WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): HistoryEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entry: HistoryEntity): Long

    @Update
    suspend fun update(entry: HistoryEntity)

    /** A revisit bumps the timestamp and the counter instead of adding a duplicate row. */
    @Transaction
    suspend fun record(url: String, title: String?, visitedAt: Long, faviconPath: String?) {
        val existing = byUrl(url)
        if (existing == null) insert(HistoryEntity(url = url, title = title, visitedAt = visitedAt, visitCount = 1, faviconPath = faviconPath))
        else update(existing.copy(title = title ?: existing.title, visitedAt = visitedAt,
            visitCount = existing.visitCount + 1, faviconPath = faviconPath ?: existing.faviconPath))
    }

    @Query("UPDATE history SET title = :title WHERE url = :url")
    suspend fun retitle(url: String, title: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM history WHERE visitedAt >= :since")
    suspend fun deleteSince(since: Long)

    @Query("DELETE FROM history")
    suspend fun clear()

    @Query("SELECT COUNT(*) FROM history")
    suspend fun count(): Int

    /** Bounded growth: keep the newest [keep] entries. */
    @Query("DELETE FROM history WHERE id NOT IN (SELECT id FROM history ORDER BY visitedAt DESC LIMIT :keep)")
    suspend fun trim(keep: Int)
}

@Dao
interface BookmarkDao {
    @Query("SELECT * FROM bookmarks ORDER BY folder IS NOT NULL, folder COLLATE NOCASE ASC, position ASC, createdAt DESC")
    fun observeAll(): Flow<List<BookmarkEntity>>

    @Query("""SELECT * FROM bookmarks
        WHERE url LIKE :query ESCAPE '\' OR title LIKE :query ESCAPE '\' OR folder LIKE :query ESCAPE '\'
        ORDER BY createdAt DESC LIMIT :limit""")
    fun search(query: String, limit: Int): Flow<List<BookmarkEntity>>

    @Query("""SELECT * FROM bookmarks
        WHERE url LIKE :query ESCAPE '\' OR title LIKE :query ESCAPE '\'
        ORDER BY createdAt DESC LIMIT :limit""")
    suspend fun suggest(query: String, limit: Int): List<BookmarkEntity>

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): BookmarkEntity?

    @Query("SELECT * FROM bookmarks WHERE url = :url LIMIT 1")
    fun observeByUrl(url: String): Flow<BookmarkEntity?>

    @Query("SELECT * FROM bookmarks WHERE id = :id")
    suspend fun get(id: Long): BookmarkEntity?

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM bookmarks WHERE (folder IS :folder)")
    suspend fun nextPosition(folder: String?): Int

    @Query("SELECT DISTINCT folder FROM bookmarks WHERE folder IS NOT NULL ORDER BY folder COLLATE NOCASE ASC")
    fun observeFolders(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(bookmark: BookmarkEntity): Long

    @Update
    suspend fun update(bookmark: BookmarkEntity)

    @Query("DELETE FROM bookmarks WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM bookmarks WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("UPDATE bookmarks SET folder = :to WHERE folder = :from")
    suspend fun renameFolder(from: String, to: String?)
}

@Dao
interface AllowlistDao {
    @Query("SELECT * FROM blocking_allowlist ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<AllowlistEntity>>

    @Query("SELECT host FROM blocking_allowlist")
    suspend fun hosts(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entry: AllowlistEntity)

    @Query("DELETE FROM blocking_allowlist WHERE host = :host")
    suspend fun delete(host: String)

    @Query("DELETE FROM blocking_allowlist")
    suspend fun clear()
}
