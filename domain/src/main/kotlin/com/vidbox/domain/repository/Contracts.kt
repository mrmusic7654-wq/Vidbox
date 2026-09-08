package com.vidbox.domain.repository

import com.vidbox.domain.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface VideoExtractor {
    suspend fun analyze(url: String): MediaInfo
}

interface VideoSearcher {
    /** Free-text search over a public video site (YouTube through the bundled engine). */
    suspend fun search(query: String, limit: Int): List<VideoSearchResult>
}

interface Downloader {
    /** A return value is a verified, complete staging file; cancellation must close processes/streams. */
    suspend fun download(record: DownloadRecord, onProgress: suspend (DownloadProgress) -> Unit): StagedMedia
    suspend fun discard(id: String)
}

interface MediaProcessor {
    suspend fun remux(inputPath: String, outputPath: String, container: String, hasVideo: Boolean): StagedMedia
    suspend fun merge(videoPath: String, audioPath: String, outputPath: String, container: String): StagedMedia
}

interface MediaStorage {
    suspend fun publish(id: String, media: StagedMedia, fileName: String, destinationTree: String?,
        onPending: suspend (String?) -> Unit): StoredMedia
    suspend fun exists(uri: String): Boolean
    suspend fun delete(uri: String)
}

interface DownloadRepository {
    fun observeActive(): Flow<List<DownloadRecord>>
    fun observeRecent(limit: Int = 4): Flow<List<DownloadRecord>>
    fun observeHistory(query: HistoryQuery): Flow<List<DownloadRecord>>
    suspend fun get(id: String): DownloadRecord?
    suspend fun active(): List<DownloadRecord>
    suspend fun enqueue(spec: DownloadSpec): String
    /** Compare-and-set with a validated state transition, not an unconditional write. */
    suspend fun transition(id: String, expected: Set<DownloadState>, to: DownloadState,
        pauseReason: PauseReason? = null, error: DownloadError? = null): Boolean
    suspend fun progress(id: String, progress: DownloadProgress)
    suspend fun pending(id: String, uri: String?)
    suspend fun complete(id: String, stored: StoredMedia): Boolean
    suspend fun setMissing(id: String, missing: Boolean)
    suspend fun cleaned(id: String)
    suspend fun removeHistory(id: String)
    suspend fun clearTerminalHistory()
}

interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}

interface NetworkMonitor { val status: StateFlow<NetworkStatus> }

interface DownloadScheduler {
    /** Called by user actions while foreground; platform restrictions are surfaced rather than hidden. */
    fun start()
}

interface EventLogger {
    /** No source URLs, titles, headers, cookies, or raw engine output are permitted here. */
    fun event(name: String, id: String? = null, fields: Map<String, String> = emptyMap())
}

fun interface TimeProvider { fun nowMillis(): Long }
