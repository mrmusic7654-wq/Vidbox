package com.vidbox.domain.usecase

import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.util.FileNames
import com.vidbox.domain.util.UrlValidator
import kotlinx.coroutines.flow.first
import java.net.URI
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
        return start(spec)
    }

    /** Saves a direct media link found in the browser with the original quality (no picker needed). */
    suspend fun enqueueDirect(url: String, fileName: String? = null,
        mimeType: String? = null, totalBytes: Long? = null): String {
        val validated = UrlValidator.validate(url)
        val extension = FileNames.extensionOf(validated, fileName)
            ?: throw Errors.exception(ErrorCode.FORMAT_UNAVAILABLE)
        if (extension !in FileNames.extensions) throw Errors.exception(ErrorCode.FORMAT_UNAVAILABLE)
        val video = !(mimeType?.startsWith("audio/") == true) && FileNames.hasVideo(extension)
        val name = fileName?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }?.let { FileNames.sanitize(it) }
            ?: FileNames.nameFromUrl(validated) ?: "Media"
        val format = MediaFormat(id = "direct", extension = extension, hasVideo = video,
            hasAudio = if (video) null else true, sizeBytes = totalBytes,
            note = "Original file downloaded directly from a web page")
        val media = MediaInfo(url = validated, title = name,
            source = runCatching { URI(validated).host }.getOrNull() ?: validated,
            extractor = "browser", formats = listOf(format), isDirect = true)
        return enqueue(media, FormatSelection(format))
    }

    /** Saves a generic web file (PDF, archive, image, …) under Downloads/Vidbox. */
    suspend fun enqueueFile(url: String, title: String? = null, extension: String,
        mimeType: String? = null, totalBytes: Long? = null): String {
        val validated = UrlValidator.validate(url)
        val ext = extension.lowercase().filter { it.isLetterOrDigit() }.take(16)
        if (ext.isEmpty()) throw Errors.exception(ErrorCode.FORMAT_UNAVAILABLE)
        val name = title?.substringBeforeLast('.')?.takeIf { it.isNotBlank() }?.let { FileNames.sanitize(it) }
            ?: FileNames.nameFromUrl(validated) ?: "Download"
        val format = MediaFormat(id = "file", extension = ext, hasVideo = false, hasAudio = null,
            sizeBytes = totalBytes)
        val spec = DownloadSpec(validated, name, null,
            runCatching { URI(validated).host }.getOrNull() ?: validated, null,
            FormatSelection(format), true, settings.settings.first().destinationTree,
            DownloadKind.FILE, mimeType)
        return start(spec)
    }

    private suspend fun start(spec: DownloadSpec): String {
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
