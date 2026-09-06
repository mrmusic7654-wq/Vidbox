package com.vidbox.data.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {
    @Query("SELECT * FROM downloads WHERE state NOT IN ('COMPLETED','FAILED','CANCELLED') OR needsCleanup = 1 ORDER BY createdAt ASC")
    fun observeActive(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<DownloadEntity>>

    @Query("""SELECT * FROM downloads
        WHERE state IN ('COMPLETED','FAILED','CANCELLED')
        AND (:filter = 'ALL' OR state = :filter)
        AND (fileName LIKE :query ESCAPE '\' OR title LIKE :query ESCAPE '\')
        ORDER BY
          CASE WHEN :sort = 'NAME' THEN fileName END COLLATE NOCASE ASC,
          CASE WHEN :sort = 'LARGEST' THEN totalBytes END DESC,
          CASE WHEN :sort = 'OLDEST' THEN createdAt END ASC,
          createdAt DESC
        LIMIT :limit""")
    fun observeHistory(query: String, filter: String, sort: String, limit: Int): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id")
    suspend fun get(id: String): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE state NOT IN ('COMPLETED','FAILED','CANCELLED') OR needsCleanup = 1 ORDER BY createdAt ASC")
    suspend fun active(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: DownloadEntity)

    @Update
    suspend fun update(entity: DownloadEntity)

    @Query("DELETE FROM downloads WHERE id = :id AND state IN ('COMPLETED','FAILED','CANCELLED')")
    suspend fun removeTerminal(id: String)

    @Query("DELETE FROM downloads WHERE state IN ('COMPLETED','FAILED','CANCELLED') AND needsCleanup = 0")
    suspend fun clearTerminal()
}
