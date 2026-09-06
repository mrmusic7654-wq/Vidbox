package com.vidbox.domain.usecase

import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import javax.inject.Inject
import kotlinx.coroutines.flow.first

class HistoryActions @Inject constructor(
    private val repository: DownloadRepository,
    private val storage: MediaStorage,
    private val downloader: Downloader,
) {
    suspend fun delete(id: String, deleteFile: Boolean) {
        val record = repository.get(id) ?: return
        check(record.state.isTerminal) { "Stop the download before removing it" }
        if (deleteFile) record.outputUri?.let { storage.delete(it) }
        downloader.discard(id)
        repository.removeHistory(id)
    }

    suspend fun verify(record: DownloadRecord): Boolean {
        val exists = record.outputUri?.let { storage.exists(it) } ?: false
        if (record.state == DownloadState.COMPLETED) repository.setMissing(record.id, !exists)
        return exists
    }

    suspend fun clearHistory() {
        // Removing metadata is deliberately separate from deleting the user's media files.
        while (true) {
            val batch = repository.observeHistory(HistoryQuery(limit = 100)).first()
            if (batch.isEmpty()) break
            batch.forEach { delete(it.id, deleteFile = false) }
        }
    }
}
