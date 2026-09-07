package com.vidbox.domain.repository

import com.vidbox.domain.model.*
import kotlinx.coroutines.flow.Flow

interface TabRepository {
    fun observe(): Flow<List<BrowserTab>>
    suspend fun all(): List<BrowserTab>
    /** Persists the whole normal-tab set atomically (order + active flag). */
    suspend fun replaceAll(tabs: List<BrowserTab>)
    suspend fun clear()
}

interface BrowsingHistoryRepository {
    fun observeRecent(limit: Int = 200): Flow<List<HistoryEntry>>
    fun search(text: String, limit: Int = 200): Flow<List<HistoryEntry>>
    suspend fun suggest(text: String, limit: Int = 6): List<HistoryEntry>
    suspend fun record(url: String, title: String?)
    suspend fun retitle(url: String, title: String)
    suspend fun delete(id: Long)
    /** Removes entries newer than [sinceMillis]; 0 clears everything. */
    suspend fun clear(sinceMillis: Long = 0)
}

interface BookmarkRepository {
    fun observeAll(): Flow<List<Bookmark>>
    fun search(text: String, limit: Int = 200): Flow<List<Bookmark>>
    fun observeFolders(): Flow<List<String>>
    fun observeByUrl(url: String): Flow<Bookmark?>
    suspend fun suggest(text: String, limit: Int = 6): List<Bookmark>
    suspend fun byUrl(url: String): Bookmark?
    /** Returns the bookmark id; an existing bookmark for the same URL is updated instead of duplicated. */
    suspend fun add(url: String, title: String, folder: String? = null): Long
    suspend fun update(id: Long, title: String, url: String, folder: String?)
    suspend fun remove(id: Long)
    suspend fun removeByUrl(url: String)
}

interface AllowlistRepository {
    fun observe(): Flow<List<String>>
    suspend fun hosts(): List<String>
    suspend fun add(host: String)
    suspend fun remove(host: String)
}
