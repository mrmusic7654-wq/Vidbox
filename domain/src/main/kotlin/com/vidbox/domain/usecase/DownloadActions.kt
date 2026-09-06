package com.vidbox.domain.usecase

import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.util.UrlValidator
import kotlinx.coroutines.flow.first
import javax.inject.Inject

class DownloadActions @Inject constructor(
    private val repository: DownloadRepository,
    private val settings: SettingsRepository,
    private val scheduler: DownloadScheduler,
    private val planner: FormatPlanner,
) {
    suspend fun enqueue(media: MediaInfo, selection: FormatSelection): String {
        require(planner.options(media).any { it.key == selection.key }) { "Selection is not offered by this source" }
        val spec = DownloadSpec(UrlValidator.validate(media.url), media.title, media.thumbnailUrl,
            media.source, media.durationSeconds, selection, media.isDirect, settings.settings.first().destinationTree)
        val id = repository.enqueue(spec)
        try { scheduler.start() } catch (error: Exception) {
            repository.transition(id, setOf(DownloadState.QUEUED), DownloadState.PAUSED,
                PauseReason.SYSTEM, Errors.of(ErrorCode.INTERRUPTED))
            throw Errors.exception(ErrorCode.INTERRUPTED, error)
        }
        return id
    }

    suspend fun pause(id: String) {
        val record = repository.get(id) ?: return
        if (record.canPause) repository.transition(id, setOf(record.state), DownloadState.PAUSED, PauseReason.USER)
    }

    suspend fun resume(id: String) {
        if (repository.transition(id, setOf(DownloadState.PAUSED, DownloadState.FAILED, DownloadState.CANCELLED), DownloadState.QUEUED)) {
            try { scheduler.start() } catch (error: Exception) {
                repository.transition(id, setOf(DownloadState.QUEUED), DownloadState.PAUSED,
                    PauseReason.SYSTEM, Errors.of(ErrorCode.INTERRUPTED))
                throw Errors.exception(ErrorCode.INTERRUPTED, error)
            }
        }
    }

    suspend fun cancel(id: String) {
        repository.transition(id, DownloadState.entries.filterNot { it == DownloadState.COMPLETED }.toSet(), DownloadState.CANCELLED)
        // Start the owner to clean up partial files even when the queue was previously idle.
        runCatching { scheduler.start() }
    }

    fun wake() = scheduler.start()
}
