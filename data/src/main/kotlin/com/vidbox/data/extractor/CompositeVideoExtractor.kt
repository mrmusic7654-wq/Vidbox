package com.vidbox.data.extractor

import com.vidbox.data.network.withResponse
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.util.FileNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CompositeVideoExtractor @Inject constructor(
    http: OkHttpClient,
    private val runner: NativeProcessRunner,
    private val parser: MetadataParser,
    private val logger: EventLogger,
) : VideoExtractor {
    private val probe = http.newBuilder().callTimeout(10, TimeUnit.SECONDS).build()
    override suspend fun analyze(url: String): MediaInfo = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        logger.event("extraction.started", id)
        val direct = try { probeDirect(url) } catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null } // A server can reject HEAD while still supporting its extractor.
        val result = direct ?: parser.parse(url, runner.run(AndroidMediaRuntime.Tool.YT_DLP,
            listOf("--dump-single-json", "--skip-download", "--no-warnings", "--retries", "2",
                "--extractor-retries", "2", "--", url), id, captureJson = true))
        logger.event("extraction.completed", id, mapOf("formats" to result.formats.size.toString()))
        result
    }

    private suspend fun probeDirect(url: String): MediaInfo? {
        val request = Request.Builder().url(url).head().build()
        return probe.newCall(request).withResponse { response ->
            if (response.code in setOf(405, 501)) {
                probe.newCall(request.newBuilder().get().header("Range", "bytes=0-0").build())
                    .withResponse { parseDirect(url, it) }
            } else parseDirect(url, response)
        }
    }

    private fun parseDirect(url: String, response: Response): MediaInfo? {
        if (!response.isSuccessful) return null
        val type = response.header("Content-Type")?.substringBefore(';')?.lowercase().orEmpty()
        val httpUrl = url.toHttpUrl()
        val remoteName = response.header("Content-Disposition")?.let { disposition ->
            Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)?.groupValues?.get(1)
                ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
                ?: Regex("filename=\"([^\"]+)\"|filename=([^;]+)", RegexOption.IGNORE_CASE)
                    .find(disposition)?.groupValues?.drop(1)?.firstOrNull { it.isNotBlank() }
        } ?: httpUrl.pathSegments.lastOrNull().orEmpty()
        val extFromPath = remoteName.substringAfterLast('.', "").lowercase()
        val ext = when (type) {
            "video/mp4" -> "mp4"; "video/webm" -> "webm"; "video/quicktime" -> "mov"
            "video/x-matroska" -> "mkv"; "audio/mp4", "audio/x-m4a" -> "m4a"
            "audio/mpeg" -> "mp3"; "audio/ogg" -> "ogg"; "audio/aac" -> "aac"
            "audio/flac" -> "flac"; "audio/wav", "audio/x-wav" -> "wav"
            "audio/webm" -> "weba"
            else -> extFromPath.takeIf { it in FileNames.extensions && type == "application/octet-stream" } ?: return null
        }
        val hasVideo = if (type.startsWith("audio/")) false else ext !in setOf("mp3", "m4a", "aac", "wav", "flac", "ogg", "opus", "weba")
        val size = response.header("Content-Range")?.substringAfterLast('/')?.toLongOrNull()
            ?: response.header("Content-Length")?.toLongOrNull()?.takeIf { it > 0 }
        return MediaInfo(url = url, title = FileNames.sanitize(remoteName.substringBeforeLast('.', remoteName)).ifBlank { "Media" },
            source = httpUrl.host, extractor = "direct", isDirect = true,
            formats = listOf(MediaFormat(id = "direct", extension = ext, hasVideo = hasVideo,
                hasAudio = if (hasVideo) null else true, sizeBytes = size, protocol = "https",
                note = "Original file · codec information not supplied by the server")))
    }
}
