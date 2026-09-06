package com.vidbox.domain.util

import com.vidbox.domain.model.DownloadState
import com.vidbox.domain.model.DownloadState.*

object DownloadStateMachine {
    private val transitions = mapOf(
        QUEUED to setOf(EXTRACTING, DOWNLOADING, PAUSED, CANCELLED, FAILED),
        EXTRACTING to setOf(DOWNLOADING, PROCESSING, PAUSED, CANCELLED, FAILED, QUEUED),
        DOWNLOADING to setOf(PROCESSING, PAUSED, CANCELLED, FAILED, QUEUED),
        PROCESSING to setOf(COMPLETED, FAILED, CANCELLED, PAUSED, QUEUED),
        PAUSED to setOf(QUEUED, CANCELLED),
        FAILED to setOf(QUEUED, CANCELLED),
        CANCELLED to setOf(QUEUED),
        COMPLETED to emptySet(),
    )
    fun permits(from: DownloadState, to: DownloadState): Boolean = from == to || to in transitions.getValue(from)
}
