package com.vidbox.data.downloader

import com.vidbox.data.extractor.AndroidMediaRuntime
import com.vidbox.data.extractor.NativeProcessRunner
import com.vidbox.data.storage.WorkFiles
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.MediaProcessor
import com.vidbox.domain.util.FileNames
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FfmpegMediaProcessor @Inject constructor(private val runner: NativeProcessRunner) : MediaProcessor {
    override suspend fun merge(videoPath: String, audioPath: String, outputPath: String, container: String): StagedMedia =
        withContext(Dispatchers.IO) {
            require(container in setOf("mp4", "webm", "mkv"))
            val video = File(videoPath).canonicalFile
            val audio = File(audioPath).canonicalFile
            val output = File(outputPath).canonicalFile
            require(video.parentFile == output.parentFile && audio.parentFile == output.parentFile)
            if (!video.isFile || !audio.isFile) throw Errors.exception(ErrorCode.PROCESSING)
            if (output.isFile && output.length() > 0) return@withContext StagedMedia(output.path, FileNames.mime(container, true), output.length())
            WorkFiles.requireSpace(output.parentFile!!, video.length() + audio.length())
            val partial = File(output.parentFile, "merged.part.$container")
            val args = mutableListOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-i", video.path, "-i", audio.path,
                "-map", "0:v:0", "-map", "1:a:0", "-c", "copy")
            if (container == "mp4") args += listOf("-movflags", "+faststart")
            args += listOf("-progress", "pipe:1", "-nostats", partial.path)
            try {
                runner.run(AndroidMediaRuntime.Tool.FFMPEG, args, output.parentFile!!.name) {
                    WorkFiles.requireSpace(output.parentFile!!)
                }
                if (!partial.isFile || partial.length() == 0L) throw Errors.exception(ErrorCode.PROCESSING)
                HttpRangeDownloader.atomicMove(partial, output)
                StagedMedia(output.path, FileNames.mime(container, true), output.length())
            } finally {
                // Keep the original streams for retry, never a half-written muxed file.
                partial.delete()
            }
        }
}
