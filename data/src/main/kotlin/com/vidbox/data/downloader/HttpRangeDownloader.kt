package com.vidbox.data.downloader

import com.vidbox.data.network.requireSuccess
import com.vidbox.data.network.withResponse
import com.vidbox.data.storage.WorkFiles
import com.vidbox.domain.model.*
import com.vidbox.domain.util.ErrorMapper
import kotlinx.coroutines.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HttpRangeDownloader @Inject constructor(private val client: OkHttpClient, private val json: Json) {
    @Serializable
    internal data class ResumeMetadata(val etag: String? = null, val modified: String? = null, val total: Long? = null) {
        val validator: String? get() = etag?.takeUnless { it.startsWith("W/") } ?: modified
    }
    private data class Range(val start: Long, val end: Long, val total: Long?)

    suspend fun transfer(url: String, target: File, onProgress: suspend (DownloadProgress) -> Unit): File = withContext(Dispatchers.IO) {
        val partial = File(target.path + ".part")
        val checkpoint = File(target.path + ".resume.json")
        // Only a successful transfer creates target, so it is safe to reuse after a publication failure.
        if (target.isFile && target.length() > 0) return@withContext target
        repeat(4) { attempt ->
            try {
                downloadOnce(url, partial, checkpoint, onProgress)
                atomicMove(partial, target)
                checkpoint.delete()
                return@withContext target
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                val mapped = ErrorMapper.from(error)
                if (mapped.code == ErrorCode.CORRUPT_PARTIAL) { partial.delete(); checkpoint.delete() }
                if (attempt == 3 || mapped.code !in setOf(ErrorCode.NETWORK, ErrorCode.TIMEOUT, ErrorCode.CORRUPT_PARTIAL)) throw error
                delay(1000L shl attempt)
            }
        }
        error("Retry loop exhausted")
    }

    private suspend fun downloadOnce(url: String, part: File, checkpoint: File,
        onProgress: suspend (DownloadProgress) -> Unit) {
        var saved = runCatching { json.decodeFromString<ResumeMetadata>(checkpoint.readText()) }.getOrNull()
        var offset = part.takeIf(File::isFile)?.length() ?: 0L
        // Resuming unvalidated bytes can splice two different revisions of the same URL. Restart instead.
        if (offset > 0 && saved?.validator == null) { part.delete(); offset = 0; saved = null }
        val builder = Request.Builder().url(url).header("Accept-Encoding", "identity")
        if (offset > 0) builder.header("Range", "bytes=$offset-").header("If-Range", saved!!.validator!!)
        client.newCall(builder.build()).withResponse { response ->
            if (response.code == 416) {
                val remoteTotal = response.header("Content-Range")?.substringAfter("*/", "")?.toLongOrNull()
                if (offset > 0 && remoteTotal == offset && saved?.total == offset && sameValidator(saved, response)) return@withResponse
                throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
            }
            response.requireSuccess()
            if (response.header("Content-Encoding")?.let { it != "identity" } == true) throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
            val body = response.body ?: throw Errors.exception(ErrorCode.NETWORK)
            val append: Boolean
            val total: Long?
            if (response.code == 206) {
                val range = parseRange(response.header("Content-Range")) ?: throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
                // EOF of an unknown-length 206 proves only that range ended, not that the representation is complete.
                if (range.total == null) throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
                if (range.start != offset || (body.contentLength() >= 0 && body.contentLength() != range.end - range.start + 1))
                    throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
                if (offset > 0 && !sameValidator(saved, response)) throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
                append = offset > 0
                total = range.total
            } else {
                // A 200 response to Range means the server ignored it or If-Range changed. Truncate, never append.
                append = false
                offset = 0
                total = body.contentLength().takeIf { it >= 0 }
            }
            WorkFiles.requireSpace(part.parentFile!!, total?.minus(offset) ?: 0)
            val meta = ResumeMetadata(response.header("ETag"), response.header("Last-Modified"), total)
            val canResume = (response.code == 206 || response.header("Accept-Ranges").equals("bytes", true)) && meta.validator != null
            // Truncate before updating validators: death between these writes cannot validate stale partial bytes.
            if (!append) FileOutputStream(part, false).use { it.fd.sync() }
            val tempMeta = File(checkpoint.path + ".tmp")
            tempMeta.writeText(json.encodeToString(meta))
            atomicMove(tempMeta, checkpoint)
            var downloaded = offset
            var previous = downloaded
            var sampleTime = System.nanoTime()
            var nextSpaceCheck = downloaded + 8 * 1024 * 1024
            onProgress(DownloadProgress(DownloadState.DOWNLOADING, downloaded, total, resumeSupported = canResume))
            FileOutputStream(part, append).use { output ->
                body.byteStream().buffered(64 * 1024).use { input ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val count = input.read(buffer)
                        if (count == -1) break
                        output.write(buffer, 0, count)
                        downloaded += count
                        if (total != null && downloaded > total) throw Errors.exception(ErrorCode.CORRUPT_PARTIAL)
                        if (downloaded >= nextSpaceCheck) {
                            WorkFiles.requireSpace(part.parentFile!!)
                            nextSpaceCheck = downloaded + 8 * 1024 * 1024
                        }
                        val now = System.nanoTime()
                        if (now - sampleTime >= 750_000_000L) {
                            val speed = ((downloaded - previous) * 1_000_000_000.0 / (now - sampleTime)).toLong()
                            onProgress(DownloadProgress(DownloadState.DOWNLOADING, downloaded, total, speed,
                                total?.takeIf { speed > 0 }?.let { (it - downloaded).coerceAtLeast(0) / speed }, canResume))
                            previous = downloaded; sampleTime = now
                        }
                    }
                }
                output.fd.sync()
            }
            if (downloaded == 0L) throw Errors.exception(ErrorCode.UNAVAILABLE)
            if (total != null && downloaded != total) throw IOException("Incomplete response body")
            onProgress(DownloadProgress(DownloadState.DOWNLOADING, downloaded, total ?: downloaded, resumeSupported = canResume))
        }
    }

    private fun sameValidator(saved: ResumeMetadata?, response: Response): Boolean {
        val etag = saved?.etag?.takeUnless { it.startsWith("W/") }
        return if (etag != null) response.header("ETag") == etag
        else saved?.modified != null && response.header("Last-Modified") == saved.modified
    }
    private fun parseRange(value: String?): Range? {
        val match = Regex("bytes (\\d+)-(\\d+)/(\\d+|\\*)").matchEntire(value.orEmpty()) ?: return null
        val start = match.groupValues[1].toLongOrNull() ?: return null
        val end = match.groupValues[2].toLongOrNull() ?: return null
        val total = match.groupValues[3].toLongOrNull()
        return if (end >= start && (total == null || total > end)) Range(start, end, total) else null
    }
    companion object {
        fun atomicMove(source: File, target: File) {
            try { Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING) }
            catch (_: java.nio.file.AtomicMoveNotSupportedException) {
                Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
            }
        }
    }
}
