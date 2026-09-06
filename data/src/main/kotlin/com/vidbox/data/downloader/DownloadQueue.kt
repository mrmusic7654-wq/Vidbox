package com.vidbox.data.downloader

import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.util.ErrorMapper
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/** One foreground-service session owns all jobs. Room is the durable queue, never an Activity. */
@Singleton
class DownloadQueue @Inject constructor(
    private val repository: DownloadRepository,
    private val settings: SettingsRepository,
    private val network: NetworkMonitor,
    private val downloader: Downloader,
    private val storage: MediaStorage,
    private val logger: EventLogger,
    private val clock: TimeProvider,
) {
    private val ownership = Mutex()
    @Volatile var isRunning = false
        private set
    private data class Snapshot(val records: List<DownloadRecord>, val settings: AppSettings, val network: NetworkStatus)

    suspend fun run(onTerminal: (DownloadRecord) -> Unit = {}): Unit = ownership.withLock {
        isRunning = true
        try {
            // A sticky-service restart adopts interrupted work. Force-stop still requires opening the app.
            repository.active().filter { it.state.isRunning }.forEach {
                repository.transition(it.id, setOf(it.state), DownloadState.QUEUED)
            }
            supervisorScope {
                val jobs = mutableMapOf<String, Job>()
                val completedSignal = MutableStateFlow(0L)
                val cleanupAttempted = mutableSetOf<String>()
                combine(repository.observeActive(), settings.settings, network.status, completedSignal) { records, prefs, net, _ ->
                    Snapshot(records, prefs, net)
                }.first { snapshot ->
                    jobs.entries.removeAll { it.value.isCompleted }
                    // A paused/cancelled DB row takes precedence over all late progress callbacks.
                    jobs.forEach { (id, job) ->
                        // Room invalidations can deliver a snapshot taken before this session claimed a job.
                        // Re-read the row before stopping a writer; a stale QUEUED snapshot must not cancel it.
                        val record = repository.get(id)
                        if (record == null || !record.state.isRunning) job.cancel()
                        else if (record.state != DownloadState.PROCESSING && !snapshot.network.permits(snapshot.settings)) {
                            repository.transition(id, setOf(record.state), DownloadState.PAUSED, waitReason(snapshot.network, snapshot.settings))
                            job.cancel()
                        }
                    }
                    for (record in snapshot.records) {
                        if (record.needsCleanup && record.id !in jobs && cleanupAttempted.add(record.id)) {
                            try {
                                downloader.discard(record.id)
                                record.pendingUri?.takeIf { it != record.outputUri }?.let { storage.delete(it) }
                                repository.cleaned(record.id)
                            } catch (error: Exception) {
                                if (error is CancellationException) throw error
                                logger.event("cleanup.failed", record.id, mapOf("code" to ErrorMapper.from(error).code.name))
                            }
                        }
                        if (record.state == DownloadState.PAUSED && record.pauseReason in setOf(PauseReason.NETWORK, PauseReason.WIFI)
                            && snapshot.network.permits(snapshot.settings) && snapshot.settings.autoResume) {
                            repository.transition(record.id, setOf(DownloadState.PAUSED), DownloadState.QUEUED)
                        }
                        if (record.state == DownloadState.QUEUED && record.id !in jobs) {
                            if (!snapshot.network.permits(snapshot.settings)) {
                                repository.transition(record.id, setOf(DownloadState.QUEUED), DownloadState.PAUSED, waitReason(snapshot.network, snapshot.settings))
                            } else if (jobs.size < snapshot.settings.maxConcurrent &&
                                repository.transition(record.id, setOf(DownloadState.QUEUED), DownloadState.EXTRACTING)) {
                                val claimed = repository.get(record.id) ?: continue
                                val job = launch(start = CoroutineStart.LAZY) { execute(claimed, onTerminal) }
                                jobs[record.id] = job
                                job.invokeOnCompletion { completedSignal.update { it + 1 } }
                                job.start()
                            }
                        }
                    }
                    val pending = snapshot.records.any {
                        it.state == DownloadState.QUEUED || it.state.isRunning ||
                            (it.state == DownloadState.PAUSED && it.pauseReason in setOf(PauseReason.NETWORK, PauseReason.WIFI) &&
                                snapshot.settings.autoResume)
                    }
                    jobs.isEmpty() && !pending
                }
            }
            Unit
        } finally { isRunning = false }
    }

    private suspend fun execute(record: DownloadRecord, onTerminal: (DownloadRecord) -> Unit) {
        var lastLog = 0L
        try {
            record.pendingUri?.let { storage.delete(it); repository.pending(record.id, null) }
            logger.event("download.started", record.id, mapOf("attempt" to record.attempt.toString()))
            val staged = downloader.download(record) { progress ->
                repository.progress(record.id, progress)
                if (clock.nowMillis() - lastLog >= 5_000) {
                    logger.event("download.progress", record.id, mapOf("bytes" to progress.downloadedBytes.toString(), "state" to progress.state.name))
                    lastLog = clock.nowMillis()
                }
            }
            currentCoroutineContext().ensureActive()
            repository.progress(record.id, DownloadProgress(DownloadState.PROCESSING, staged.sizeBytes, staged.sizeBytes))
            // A user may cancel in the brief interval between the last byte and the storage copy.
            if (repository.get(record.id)?.state != DownloadState.PROCESSING) return
            val stored = storage.publish(record.id, staged, record.fileName, record.spec.destinationTree) { uri ->
                repository.pending(record.id, uri)
            }
            withContext(NonCancellable) {
                if (repository.complete(record.id, stored)) {
                    logger.event("download.completed", record.id, mapOf("bytes" to stored.sizeBytes.toString()))
                    repository.get(record.id)?.let(onTerminal)
                } else {
                    storage.delete(stored.uri)
                    repository.pending(record.id, null)
                }
            }
        } catch (cancel: CancellationException) {
            withContext(NonCancellable) {
                // Preserve manual/network pauses; only an unaccounted process/service interruption becomes queued.
                repository.transition(record.id, RUNNING, DownloadState.QUEUED, error = Errors.of(ErrorCode.INTERRUPTED))
            }
            throw cancel
        } catch (error: Exception) {
            val mapped = ErrorMapper.from(error)
            val prefs = settings.settings.first()
            if (mapped.code in setOf(ErrorCode.NETWORK, ErrorCode.TIMEOUT) && !network.status.value.permits(prefs)) {
                repository.transition(record.id, RUNNING, DownloadState.PAUSED, waitReason(network.status.value, prefs))
            } else {
                if (repository.transition(record.id, RUNNING, DownloadState.FAILED, error = mapped))
                    repository.get(record.id)?.let(onTerminal)
            }
            logger.event("download.failed", record.id, mapOf("code" to mapped.code.name, "type" to error.javaClass.simpleName))
        }
    }

    /** A short WorkManager recovery pass never starts a dataSync service from a restricted background context. */
    suspend fun prepareRecovery(): Boolean {
        if (!ownership.tryLock()) return false
        try {
            val rows = repository.active()
            rows.filter { it.state.isRunning }.forEach {
                repository.transition(it.id, setOf(it.state), DownloadState.QUEUED, error = Errors.of(ErrorCode.INTERRUPTED))
            }
            for (row in rows.filter { it.needsCleanup }) {
                try {
                    downloader.discard(row.id)
                    row.pendingUri?.takeIf { it != row.outputUri }?.let { storage.delete(it) }
                    repository.cleaned(row.id)
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    logger.event("cleanup.failed", row.id, mapOf("code" to ErrorMapper.from(error).code.name))
                }
            }
            return repository.active().any { it.state == DownloadState.QUEUED || it.pauseReason == PauseReason.SYSTEM ||
                it.pauseReason == PauseReason.NETWORK || it.pauseReason == PauseReason.WIFI }
        } finally { ownership.unlock() }
    }

    suspend fun pauseForSystemLimit() {
        repository.active().filter { it.state.isRunning || it.state == DownloadState.QUEUED }.forEach {
            repository.transition(it.id, setOf(it.state), DownloadState.PAUSED, PauseReason.SYSTEM, Errors.of(ErrorCode.INTERRUPTED))
        }
    }

    private fun waitReason(status: NetworkStatus, settings: AppSettings) =
        if (status.connected && settings.wifiOnly) PauseReason.WIFI else PauseReason.NETWORK

    companion object { val RUNNING = setOf(DownloadState.EXTRACTING, DownloadState.DOWNLOADING, DownloadState.PROCESSING) }
}
