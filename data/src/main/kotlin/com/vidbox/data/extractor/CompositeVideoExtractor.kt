package com.vidbox.data.extractor

import com.vidbox.data.network.withResponse
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.util.FileNames
import com.vidbox.domain.util.UrlValidator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.net.URI
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Analyzes a media URL with three layers, from cheapest to most capable:
 *  1. A fast HTTP probe, which instantly recognizes direct media files.
 *  2. A lightweight scan of the page's HTML (Open Graph video tags, <video>/<source>
 *     elements), so a normal web page with an embedded direct file never waits for the
 *     engine. Each candidate is itself probed, so only verified direct media is offered.
 *  3. The bundled yt-dlp engine for real extractor support (YouTube and thousands of
 *     other sites).
 * Each layer has a strict time budget: slow or unresponsive servers cannot stall the
 * layer after them.
 */
@Singleton
class CompositeVideoExtractor @Inject constructor(
    http: OkHttpClient,
    private val runner: NativeProcessRunner,
    private val parser: MetadataParser,
    private val logger: EventLogger,
) : VideoExtractor {
    private val client = http.newBuilder()
        .callTimeout(PROBE_TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .build()

    override suspend fun analyze(url: String): MediaInfo = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        logger.event("extraction.started", id)
        val direct = try { withTimeoutOrNull(DIRECT_BUDGET_MS) { probeDirect(url) } }
            catch (cancel: CancellationException) { throw cancel }
            catch (_: Exception) { null } // A server can reject HEAD while still supporting its extractor.
        val result = direct ?: run {
            val scanned = try { pageDirect(url) }
                catch (cancel: CancellationException) { throw cancel }
                catch (_: Exception) { null }
            scanned ?: parser.parse(url, runner.run(AndroidMediaRuntime.Tool.YT_DLP,
                listOf("--dump-single-json", "--skip-download", "--no-warnings", "--retries", "2",
                    "--extractor-retries", "2", "--", url), id, captureJson = true))
        }
        logger.event("extraction.completed", id, mapOf("formats" to result.formats.size.toString()))
        result
    }

    private suspend fun probeDirect(url: String): MediaInfo? {
        val request = Request.Builder().url(url).head().build()
        return client.newCall(request).withResponse { response ->
            if (response.code in setOf(405, 501)) {
                client.newCall(request.newBuilder().get().header("Range", "bytes=0-0").build())
                    .withResponse { parseDirect(url, it) }
            } else parseDirect(url, response)
        }
    }

    /**
     * Quick HTML scan: pages that embed a direct media file are answered with a verified
     * direct result in one round trip instead of a full extractor run. When the page has
     * no directly downloadable media, or the scan budget runs out, the caller falls back
     * to the bundled engine, so nothing supported is ever lost.
     */
    private suspend fun pageDirect(url: String): MediaInfo? = withTimeoutOrNull(PAGE_BUDGET_MS) {
        val request = Request.Builder().url(url)
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,video/*;q=0.8,audio/*;q=0.8,*/*;q=0.1")
            .build()
        val scanned = client.newCall(request).withResponse { response ->
            if (!response.isSuccessful) return@withResponse null
            val type = response.header("Content-Type")?.substringBefore(';')?.lowercase().orEmpty()
            // Some URLs only serve media over GET (HEAD rejected). Recognize it the same way a probe would.
            if (type.startsWith("video/") || type.startsWith("audio/")) return@withResponse parseDirect(url, response)
            if (!type.contains("html") && !type.contains("xml") && !type.contains("text/")) return@withResponse null
            val body = response.body ?: return@withResponse null
            val html = StringBuilder(PAGE_READ_LIMIT)
            body.byteStream().use { input ->
                val buffer = ByteArray(32 * 1024)
                while (html.length < PAGE_READ_LIMIT) {
                    val count = input.read(buffer)
                    if (count < 0) break
                    html.append(String(buffer, 0, count))
                }
            }
            body.close()
            if (html.isEmpty()) return@withResponse null
            val candidates = scanCandidates(html).mapNotNull { normalizeCandidate(url, it) }
                .distinct()
                .take(MAX_SCAN_CANDIDATES)
            for (candidate in candidates) {
                val found = runCatching { probeDirect(candidate) }.getOrNull()
                if (found != null) return@withResponse found
            }
            null
        }
        if (scanned != null) logger.event("extraction.page_scan", fields = mapOf("hit" to "1"))
        scanned
    }

    /** Media hints in document order: Open Graph, Twitter player, then real <video>/<source> elements. */
    private fun scanCandidates(html: CharSequence): List<String> {
        val links = linkedSetOf<String>()
        val meta = Regex("<meta\\b[^>]*>", RegexOption.IGNORE_CASE)
        meta.findAll(html).forEach { match ->
            val tag = match.value
            val key = Regex("(?:property|name|itemprop)\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)?.lowercase().orEmpty()
            if (key in META_MEDIA_KEYS) {
                Regex("content\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                    .find(tag)?.groupValues?.get(1)?.let { links.add(htmlUnescape(it)) }
            }
        }
        Regex("<video\\b[^>]*\\bsrc\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
            .findAll(html).forEach { links.add(htmlUnescape(it.groupValues[1])) }
        Regex("<source\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(html).forEach { match ->
            val tag = match.value
            // HLS manifests are handled by the extractor engine, not as direct downloads.
            if (Regex("type\\s*=\\s*[\"'][^\"']*(?:mpegurl|x-mpegurl|hls)[^\"']*[\"']", RegexOption.IGNORE_CASE)
                    .containsMatchIn(tag)) return@forEach
            Regex("src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .find(tag)?.groupValues?.get(1)?.let { links.add(htmlUnescape(it)) }
        }
        // Last resort: ordinary links to a media file (download buttons, "direct download").
        if (links.size < MAX_SCAN_CANDIDATES) {
            Regex("<a\\b[^>]*\\bhref\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)
                .findAll(html).forEach { match ->
                    val href = htmlUnescape(match.groupValues[1])
                    val extension = FileNames.extensionOf(href)
                    if (extension != null && extension in FileNames.extensions && FileNames.hasVideo(extension)) {
                        links.add(href)
                    }
                }
        }
        return links.toList()
    }

    private fun normalizeCandidate(base: String, raw: String): String? {
        val value = raw.trim().trim('"')
        if (value.isEmpty()) return null
        val absolute = if (value.startsWith("http://") || value.startsWith("https://")) value
            else runCatching { URI(base).resolve(value).toString() }.getOrNull() ?: return null
        val secure = if (absolute.startsWith("http://")) "https://" + absolute.substringAfter("://") else absolute
        return runCatching { UrlValidator.validate(secure) }.getOrNull()
    }

    private fun htmlUnescape(value: String): String = value
        .replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">")
        .replace("&quot;", "\"").replace("&#39;", "'").replace("&apos;", "'")

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

    private companion object {
        const val PROBE_TIMEOUT_MS = 6_000L
        const val DIRECT_BUDGET_MS = 4_000L
        const val PAGE_BUDGET_MS = 7_000L
        const val PAGE_READ_LIMIT = 2 * 1024 * 1024
        const val MAX_SCAN_CANDIDATES = 4
        val META_MEDIA_KEYS = setOf("og:video", "og:video:url", "og:video:secure_url",
            "twitter:player:stream", "twitter:player:src")
    }
}
