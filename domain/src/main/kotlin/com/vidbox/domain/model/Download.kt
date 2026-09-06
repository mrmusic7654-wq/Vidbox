package com.vidbox.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class DownloadState {
    QUEUED, EXTRACTING, DOWNLOADING, PROCESSING, COMPLETED, FAILED, CANCELLED, PAUSED;
    val isRunning: Boolean get() = this in setOf(EXTRACTING, DOWNLOADING, PROCESSING)
    val isTerminal: Boolean get() = this in setOf(COMPLETED, FAILED, CANCELLED)
}

@Serializable
enum class PauseReason { USER, NETWORK, WIFI, SYSTEM }

@Serializable
data class DownloadSpec(
    val url: String,
    val title: String,
    val thumbnailUrl: String?,
    val source: String,
    val durationSeconds: Double?,
    val selection: FormatSelection,
    val isDirect: Boolean,
    /** Snapshot the destination at enqueue time; changing settings never moves an in-flight file. */
    val destinationTree: String?,
)

data class DownloadRecord(
    val id: String,
    val spec: DownloadSpec,
    val fileName: String,
    val state: DownloadState = DownloadState.QUEUED,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = spec.selection.estimatedBytes,
    val speedBytesPerSecond: Long = 0,
    val etaSeconds: Long? = null,
    val createdAt: Long,
    val startedAt: Long? = null,
    val completedAt: Long? = null,
    val error: DownloadError? = null,
    val outputUri: String? = null,
    val pendingUri: String? = null,
    val mimeType: String? = null,
    val fileMissing: Boolean = false,
    val resumeSupported: Boolean = !spec.isDirect,
    val pauseReason: PauseReason? = null,
    val attempt: Int = 0,
    val needsCleanup: Boolean = false,
) {
    val percent: Float? get() = totalBytes?.takeIf { it > 0 }?.let {
        (downloadedBytes.toDouble() / it * 100).toFloat().coerceIn(0f, 100f)
    }
    val canPause: Boolean get() = state == DownloadState.QUEUED ||
        (state in setOf(DownloadState.DOWNLOADING, DownloadState.EXTRACTING) && resumeSupported)
}

data class DownloadProgress(
    val state: DownloadState,
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val speedBytesPerSecond: Long = 0,
    val etaSeconds: Long? = null,
    val resumeSupported: Boolean = false,
)

data class StagedMedia(val path: String, val mimeType: String, val sizeBytes: Long)
data class StoredMedia(val uri: String, val fileName: String, val mimeType: String, val sizeBytes: Long)

enum class HistoryFilter { ALL, COMPLETED, FAILED, CANCELLED }
enum class HistorySort { NEWEST, OLDEST, NAME, LARGEST }
data class HistoryQuery(
    val text: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val sort: HistorySort = HistorySort.NEWEST,
    val limit: Int = 100,
)
