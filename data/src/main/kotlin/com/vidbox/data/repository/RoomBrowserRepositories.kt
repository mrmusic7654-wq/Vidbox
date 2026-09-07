package com.vidbox.data.repository

import androidx.room.withTransaction
import com.vidbox.data.database.*
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.util.BrowserLinks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

private fun likePattern(text: String): String {
    val escaped = text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
    return "%$escaped%"
}

@Singleton
class RoomTabRepository @Inject constructor(private val database: VidboxDatabase) : TabRepository {
    private val dao get() = database.tabs()
    override fun observe(): Flow<List<BrowserTab>> = dao.observeAll().map { rows -> rows.map(::tab) }.flowOn(Dispatchers.IO)
    override suspend fun all(): List<BrowserTab> = withContext(Dispatchers.IO) { dao.all().map(::tab) }
    override suspend fun replaceAll(tabs: List<BrowserTab>) = withContext(Dispatchers.IO) {
        dao.replaceAll(tabs.mapIndexed { index, tab ->
            TabEntity(tab.id, tab.url?.takeIf(BrowserLinks::isWebPage), tab.title?.take(240), index, tab.isActive,
                tab.createdAt, tab.lastActiveAt, tab.faviconPath)
        })
    }
    override suspend fun clear() = withContext(Dispatchers.IO) { dao.clear() }
    private fun tab(e: TabEntity) = BrowserTab(e.id, e.url, e.title, e.position, e.isActive, e.createdAt, e.lastActiveAt, e.faviconPath)
}

@Singleton
class RoomBrowsingHistoryRepository @Inject constructor(
    private val database: VidboxDatabase,
    private val clock: TimeProvider,
) : BrowsingHistoryRepository {
    private val dao get() = database.history()
    override fun observeRecent(limit: Int): Flow<List<HistoryEntry>> =
        dao.observeRecent(limit.coerceIn(1, 5000)).map { rows -> rows.map(::entry) }.flowOn(Dispatchers.IO)
    override fun search(text: String, limit: Int): Flow<List<HistoryEntry>> =
        dao.search(likePattern(text), limit.coerceIn(1, 5000)).map { rows -> rows.map(::entry) }.flowOn(Dispatchers.IO)
    override suspend fun suggest(text: String, limit: Int): List<HistoryEntry> = withContext(Dispatchers.IO) {
        if (text.isBlank()) emptyList() else dao.suggest(likePattern(text), limit.coerceIn(1, 50)).map(::entry)
    }
    override suspend fun record(url: String, title: String?) = withContext(Dispatchers.IO) {
        if (!BrowserLinks.isWebPage(url)) return@withContext
        database.withTransaction {
            dao.record(url.take(8192), title?.take(240)?.takeIf(String::isNotBlank), clock.nowMillis(), null)
            if (dao.count() > MAX_ENTRIES + TRIM_SLACK) dao.trim(MAX_ENTRIES)
        }
    }
    override suspend fun retitle(url: String, title: String) = withContext(Dispatchers.IO) {
        if (title.isNotBlank()) dao.retitle(url, title.take(240))
    }
    override suspend fun delete(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
    override suspend fun clear(sinceMillis: Long) = withContext(Dispatchers.IO) {
        if (sinceMillis <= 0) dao.clear() else dao.deleteSince(sinceMillis)
    }
    private fun entry(e: HistoryEntity) = HistoryEntry(e.id, e.url, e.title, e.visitedAt, e.visitCount, e.faviconPath)

    private companion object {
        const val MAX_ENTRIES = 5000
        const val TRIM_SLACK = 200
    }
}

@Singleton
class RoomBookmarkRepository @Inject constructor(
    private val database: VidboxDatabase,
    private val clock: TimeProvider,
) : BookmarkRepository {
    private val dao get() = database.bookmarks()
    override fun observeAll(): Flow<List<Bookmark>> = dao.observeAll().map { rows -> rows.map(::bookmark) }.flowOn(Dispatchers.IO)
    override fun search(text: String, limit: Int): Flow<List<Bookmark>> =
        dao.search(likePattern(text), limit.coerceIn(1, 5000)).map { rows -> rows.map(::bookmark) }.flowOn(Dispatchers.IO)
    override fun observeFolders(): Flow<List<String>> = dao.observeFolders().flowOn(Dispatchers.IO)
    override fun observeByUrl(url: String): Flow<Bookmark?> = dao.observeByUrl(url).map { it?.let(::bookmark) }.flowOn(Dispatchers.IO)
    override suspend fun suggest(text: String, limit: Int): List<Bookmark> = withContext(Dispatchers.IO) {
        if (text.isBlank()) emptyList() else dao.suggest(likePattern(text), limit.coerceIn(1, 50)).map(::bookmark)
    }
    override suspend fun byUrl(url: String): Bookmark? = withContext(Dispatchers.IO) { dao.byUrl(url)?.let(::bookmark) }
    override suspend fun add(url: String, title: String, folder: String?): Long = withContext(Dispatchers.IO) {
        require(BrowserLinks.isWebPage(url)) { "Only web pages can be bookmarked" }
        val cleanTitle = title.trim().take(240).ifBlank { BrowserLinks.displayHost(url) ?: url }
        val cleanFolder = folder?.trim()?.take(80)?.takeIf(String::isNotBlank)
        database.withTransaction {
            val existing = dao.byUrl(url)
            if (existing != null) {
                dao.update(existing.copy(title = cleanTitle, folder = cleanFolder))
                existing.id
            } else dao.insert(BookmarkEntity(url = url, title = cleanTitle, folder = cleanFolder,
                position = dao.nextPosition(cleanFolder), createdAt = clock.nowMillis(), faviconPath = null))
        }
    }
    override suspend fun update(id: Long, title: String, url: String, folder: String?) = withContext(Dispatchers.IO) {
        require(BrowserLinks.isWebPage(url)) { "Only web pages can be bookmarked" }
        database.withTransaction {
            val current = dao.get(id) ?: return@withTransaction
            val clash = dao.byUrl(url)
            if (clash != null && clash.id != id) dao.delete(clash.id)
            val cleanFolder = folder?.trim()?.take(80)?.takeIf(String::isNotBlank)
            dao.update(current.copy(title = title.trim().take(240).ifBlank { current.title }, url = url,
                folder = cleanFolder, position = if (cleanFolder == current.folder) current.position else dao.nextPosition(cleanFolder)))
        }
    }
    override suspend fun remove(id: Long) = withContext(Dispatchers.IO) { dao.delete(id) }
    override suspend fun removeByUrl(url: String) = withContext(Dispatchers.IO) { dao.deleteByUrl(url) }
    private fun bookmark(e: BookmarkEntity) = Bookmark(e.id, e.url, e.title, e.folder, e.position, e.createdAt, e.faviconPath)
}

@Singleton
class RoomAllowlistRepository @Inject constructor(
    private val database: VidboxDatabase,
    private val clock: TimeProvider,
) : AllowlistRepository {
    private val dao get() = database.allowlist()
    override fun observe(): Flow<List<String>> = dao.observeAll().map { rows -> rows.map { it.host } }.flowOn(Dispatchers.IO)
    override suspend fun hosts(): List<String> = withContext(Dispatchers.IO) { dao.hosts() }
    override suspend fun add(host: String) = withContext(Dispatchers.IO) {
        val normalized = com.vidbox.domain.blocking.Allowlist.normalize(host)
        require(normalized.isNotEmpty() && normalized.length <= 253 && normalized.none { it.isWhitespace() || it == '/' })
        dao.upsert(AllowlistEntity(normalized, clock.nowMillis()))
    }
    override suspend fun remove(host: String) = withContext(Dispatchers.IO) { dao.delete(com.vidbox.domain.blocking.Allowlist.normalize(host)) }
}
