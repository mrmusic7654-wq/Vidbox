package com.vidbox.data.downloader

import com.vidbox.data.extractor.AndroidMediaRuntime
import com.vidbox.data.extractor.NativeProcessRunner
import com.vidbox.data.storage.WorkFiles
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.util.FileNames
import com.vidbox.domain.util.ProgressParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HybridDownloader @Inject constructor(
    private val direct: HttpRangeDownloader,
    private val runner: NativeProcessRunner,
    private val processor: MediaProcessor,
    private val files: WorkFiles,
    private val logger: EventLogger,
) : Downloader {
    override suspend fun download(record: DownloadRecord, onProgress: suspend (DownloadProgress) -> Unit): StagedMedia =
        withContext(Dispatchers.IO) {
            val spec = record.spec
            val selection = spec.selection
            val dir = files.directory(record.id)
            WorkFiles.requireSpace(dir)
            if (spec.isDirect) {
                val file = direct.transfer(spec.url, File(dir, "original.${selection.primary.extension}"), onProgress)
                return@withContext StagedMedia(file.path, FileNames.mime(selection.container, selection.primary.hasVideo), file.length())
            }
            val formats = listOfNotNull(selection.primary, selection.audio)
            val totals = formats.map { it.sizeBytes }.toMutableList()
            var finishedBytes = 0L
            val downloaded = formats.mapIndexed { index, format ->
                currentCoroutineContext().ensureActive()
                require(format.id.matches(Regex("[A-Za-z0-9_.-]{1,128}")))
                require(format.extension in FileNames.extensions)
                val file = File(dir, "stream-$index.${format.extension}")
                if (!file.isFile || file.length() == 0L) {
                    logger.event("format.selected", record.id, mapOf("format" to format.id, "container" to selection.container))
                    runner.run(AndroidMediaRuntime.Tool.YT_DLP, listOf(
                        "--no-warnings", "--newline", "--progress", "--progress-delta", "1",
                        "--progress-template", ProgressParser.TEMPLATE,
                        "--no-simulate", "--continue", "--part", "--no-mtime", "--fixup", "never",
                        "--hls-prefer-native", "--abort-on-unavailable-fragment",
                        "--retries", "4", "--fragment-retries", "4", "--extractor-retries", "2",
                        "--retry-sleep", "exp=1:16", "--concurrent-fragments", "1",
                        "-f", format.id, "-o", file.absolutePath, "--", spec.url), record.id) { line ->
                        ProgressParser.parse(line)?.let { progress ->
                            WorkFiles.requireSpace(dir)
                            if (progress.total != null) totals[index] = progress.total
                            val total = if (totals.all { it != null }) totals.sumOf { it!! } else null
                            val done = finishedBytes + progress.downloaded
                            onProgress(DownloadProgress(DownloadState.DOWNLOADING, done, total, progress.speed,
                                total?.takeIf { progress.speed > 0 }?.let { (it - done).coerceAtLeast(0) / progress.speed }, true))
                        }
                    }
                }
                if (!file.isFile || file.length() == 0L) throw Errors.exception(ErrorCode.ENGINE)
                totals[index] = file.length()
                finishedBytes += file.length()
                file
            }
            if (selection.requiresMerging) {
                onProgress(DownloadProgress(DownloadState.PROCESSING, finishedBytes, finishedBytes))
                logger.event("processing.started", record.id)
                processor.merge(downloaded[0].path, downloaded[1].path, File(dir, "media.${selection.container}").path, selection.container)
                    .also { logger.event("processing.completed", record.id) }
            } else {
                val file = downloaded.single()
                StagedMedia(file.path, FileNames.mime(selection.container, selection.primary.hasVideo), file.length())
            }
        }
    override suspend fun discard(id: String) = files.discard(id)
}
