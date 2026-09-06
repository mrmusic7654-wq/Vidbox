package com.vidbox.data.repository

import androidx.room.withTransaction
import com.vidbox.data.database.*
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.util.DownloadStateMachine
import com.vidbox.domain.util.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomDownloadRepository @Inject constructor(
    private val database: VidboxDatabase,
    private val cipher: SecretCipher,
    private val clock: TimeProvider,
    private val json: Json,
) : DownloadRepository {
    private val dao get() = database.downloads()

    override fun observeActive() = dao.observeActive().map { rows -> rows.map(::record) }.flowOn(Dispatchers.IO)
    override fun observeRecent(limit: Int) = dao.observeRecent(limit.coerceIn(1, 100))
        .map { rows -> rows.map(::record) }.flowOn(Dispatchers.IO)
    override fun observeHistory(query: HistoryQuery): Flow<List<DownloadRecord>> {
        val escaped = query.text.trim().replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_")
        return dao.observeHistory("%$escaped%", query.filter.name, query.sort.name, query.limit.coerceIn(1, 10000))
            .map { rows -> rows.map(::record) }.flowOn(Dispatchers.IO)
    }
    override suspend fun get(id: String) = withContext(Dispatchers.IO) { dao.get(id)?.let(::record) }
    override suspend fun active() = withContext(Dispatchers.IO) { dao.active().map(::record) }

    override suspend fun enqueue(spec: DownloadSpec): String = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        dao.insert(DownloadEntity(id, cipher.encrypt(json.encodeToString(spec)), spec.title,
            FileNames.output(spec.title, id, spec.selection.container), DownloadState.QUEUED.name,
            0, spec.selection.estimatedBytes, 0, null, clock.nowMillis(), null, null, null,
            null, null, null, false, !spec.isDirect, null, 0))
        id
    }

    override suspend fun transition(id: String, expected: Set<DownloadState>, to: DownloadState,
        pauseReason: PauseReason?, error: DownloadError?): Boolean = database.withTransaction {
        val row = dao.get(id) ?: return@withTransaction false
        val from = DownloadState.valueOf(row.state)
        if (from !in expected || !DownloadStateMachine.permits(from, to)) return@withTransaction false
        dao.update(row.copy(state = to.name, pauseReason = pauseReason?.name, errorCode = error?.code?.name,
            speedBytesPerSecond = 0, etaSeconds = null,
            startedAt = if (to.isRunning) row.startedAt ?: clock.nowMillis() else row.startedAt,
            completedAt = if (to.isTerminal) clock.nowMillis() else null,
            attempt = if (to == DownloadState.EXTRACTING && from == DownloadState.QUEUED) row.attempt + 1 else row.attempt,
            needsCleanup = to == DownloadState.CANCELLED || row.needsCleanup))
        true
    }

    override suspend fun progress(id: String, progress: DownloadProgress) = database.withTransaction {
        val row = dao.get(id) ?: return@withTransaction
        val state = DownloadState.valueOf(row.state)
        // A late network callback must never resurrect a cancelled/paused/completed task.
        if (!state.isRunning || !progress.state.isRunning || !DownloadStateMachine.permits(state, progress.state)) return@withTransaction
        dao.update(row.copy(state = progress.state.name,
            downloadedBytes = progress.downloadedBytes.coerceAtLeast(0),
            totalBytes = progress.totalBytes?.takeIf { it > 0 },
            speedBytesPerSecond = progress.speedBytesPerSecond.coerceAtLeast(0),
            etaSeconds = progress.etaSeconds?.takeIf { it >= 0 }, resumeSupported = progress.resumeSupported))
    }
    override suspend fun pending(id: String, uri: String?) = database.withTransaction {
        dao.get(id)?.let { dao.update(it.copy(pendingUri = uri)) }
    }
    override suspend fun complete(id: String, stored: StoredMedia): Boolean = database.withTransaction {
        val row = dao.get(id) ?: return@withTransaction false
        if (row.state != DownloadState.PROCESSING.name) return@withTransaction false
        dao.update(row.copy(state = DownloadState.COMPLETED.name, outputUri = stored.uri, pendingUri = null,
            fileName = stored.fileName, mimeType = stored.mimeType, totalBytes = stored.sizeBytes,
            downloadedBytes = stored.sizeBytes, speedBytesPerSecond = 0, etaSeconds = null,
            completedAt = clock.nowMillis(), errorCode = null, fileMissing = false, needsCleanup = true))
        true
    }
    override suspend fun setMissing(id: String, missing: Boolean) = database.withTransaction {
        dao.get(id)?.takeIf { it.fileMissing != missing }?.let { dao.update(it.copy(fileMissing = missing)) }
    }
    override suspend fun cleaned(id: String) = database.withTransaction {
        dao.get(id)?.let { dao.update(it.copy(needsCleanup = false, pendingUri = null)) }
    }
    override suspend fun removeHistory(id: String) = dao.removeTerminal(id)
    override suspend fun clearTerminalHistory() = dao.clearTerminal()

    private fun record(e: DownloadEntity) = DownloadRecord(
        id = e.id, spec = json.decodeFromString<DownloadSpec>(cipher.decrypt(e.specCiphertext)),
        fileName = e.fileName, state = DownloadState.valueOf(e.state), downloadedBytes = e.downloadedBytes,
        totalBytes = e.totalBytes, speedBytesPerSecond = e.speedBytesPerSecond, etaSeconds = e.etaSeconds,
        createdAt = e.createdAt, startedAt = e.startedAt, completedAt = e.completedAt,
        error = e.errorCode?.let { Errors.of(ErrorCode.valueOf(it)) }, outputUri = e.outputUri,
        pendingUri = e.pendingUri, mimeType = e.mimeType, fileMissing = e.fileMissing,
        resumeSupported = e.resumeSupported, pauseReason = e.pauseReason?.let(PauseReason::valueOf),
        attempt = e.attempt, needsCleanup = e.needsCleanup,
    )
}
