package com.vidbox.data.downloader

import android.media.MediaMetadataRetriever
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
    override suspend fun merge(videoPath: String, audioPath: String, outputPath: String, container: String): StagedMedia {
        require(container in setOf("mp4", "webm", "mkv"))
        return process(listOf(videoPath, audioPath), outputPath, container, true, listOf("-map", "0:v:0", "-map", "1:a:0"))
    }

    override suspend fun remux(inputPath: String, outputPath: String, container: String, hasVideo: Boolean): StagedMedia =
        process(listOf(inputPath), outputPath, container, hasVideo, listOf("-map", "0:v:0?", "-map", "0:a:0?", "-dn", "-sn"))

    private suspend fun process(inputPaths: List<String>, outputPath: String, container: String, hasVideo: Boolean,
        maps: List<String>): StagedMedia = withContext(Dispatchers.IO) {
        require(container in FileNames.extensions)
        val inputs = inputPaths.map { File(it).canonicalFile }
        val output = File(outputPath).canonicalFile
        require(inputs.all { it.parentFile == output.parentFile })
        if (inputs.any { !it.isFile || it.length() == 0L }) throw Errors.exception(ErrorCode.PROCESSING)
        if (output.isFile && output.length() > 0) return@withContext staged(output, container, hasVideo)
        WorkFiles.requireSpace(output.parentFile!!, inputs.sumOf { it.length() })
        val partial = File(output.parentFile, "processed.part.$container")
        val args = mutableListOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y")
        inputs.forEach { args += listOf("-protocol_whitelist", "file,pipe",
            "-format_whitelist", "mov,matroska,webm,mpegts,mp3,aac,ogg,wav,flac,avi,flv", "-i", it.path) }
        args += maps
        args += listOf("-c", "copy")
        if (container in setOf("mp4", "m4a", "mov", "m4v")) args += listOf("-movflags", "+faststart")
        args += listOf("-progress", "pipe:1", "-nostats", partial.path)
        try {
            runner.run(AndroidMediaRuntime.Tool.FFMPEG, args, output.parentFile!!.name) {
                WorkFiles.requireSpace(output.parentFile!!)
            }
            if (!partial.isFile || partial.length() == 0L) throw Errors.exception(ErrorCode.PROCESSING)
            HttpRangeDownloader.atomicMove(partial, output)
            staged(output, container, hasVideo)
        } finally {
            // Keep the original streams for retry, never a half-written muxed file.
            partial.delete()
        }
    }

    private fun staged(file: File, container: String, expectedVideo: Boolean): StagedMedia {
        // Correct the media category when an HLS source did not report stream presence during analysis.
        val video = runCatching {
            MediaMetadataRetriever().use {
                it.setDataSource(file.path)
                when (it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO)) {
                    "yes" -> true
                    "no" -> false
                    else -> if (it.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO) == "yes") false else expectedVideo
                }
            }
        }.getOrDefault(expectedVideo)
        return StagedMedia(file.path, FileNames.mime(container, video), file.length())
    }
}
