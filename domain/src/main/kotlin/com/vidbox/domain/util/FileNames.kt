package com.vidbox.domain.util

import java.nio.charset.StandardCharsets
import java.text.Normalizer

object FileNames {
    private val invalid = Regex("[\\p{Cc}\\p{Cf}\\\\/:*?\"<>|]")
    private val reserved = Regex("(?i)^(con|prn|aux|nul|com[0-9]|lpt[0-9])$")
    val extensions = setOf("mp4", "m4v", "webm", "mkv", "mov", "avi", "flv", "3gp", "ts",
        "m4a", "mp3", "aac", "opus", "ogg", "oga", "wav", "flac", "weba")
    val audioExtensions = setOf("m4a", "mp3", "aac", "opus", "ogg", "oga", "wav", "flac", "weba")
    /** Non-media formats the browser offers as plain file downloads (never page formats). */
    val fileExtensions = setOf("pdf", "zip", "rar", "7z", "tar", "gz", "bz2", "xz", "apk", "exe",
        "doc", "docx", "xls", "xlsx", "ppt", "pptx", "odt", "ods", "csv", "epub", "mobi",
        "iso", "img", "bin", "torrent")

    fun sanitize(input: String, maxBytes: Int = 160): String {
        require(maxBytes >= 8)
        val normalized = Normalizer.normalize(input, Normalizer.Form.NFKC)
        var name = invalid.replace(normalized, "_").replace(Regex("\\s+"), " ")
            .replace(Regex("\\.{2,}"), "_").trim(' ', '.', '_')
        if (name.isBlank()) name = "Media"
        if (reserved.matches(name.substringBefore('.'))) name = "Media_$name"
        val result = StringBuilder()
        var bytes = 0
        val codePoints = name.codePoints().iterator()
        while (codePoints.hasNext()) {
            val part = String(Character.toChars(codePoints.nextInt()))
            val count = part.toByteArray(StandardCharsets.UTF_8).size
            if (bytes + count > maxBytes) break
            result.append(part)
            bytes += count
        }
        return result.toString().trimEnd('.', ' ').ifBlank { "Media" }
    }

    fun output(title: String, id: String, extension: String): String {
        require(id.matches(Regex("[a-f0-9-]{36}")))
        // Media containers and generic file extensions (pdf, zip, …) share the same safe rule.
        require(extension.matches(Regex("[A-Za-z0-9]{1,16}")))
        return "${sanitize(title)}-${id.take(8)}.$extension"
    }

    fun hasVideo(extension: String): Boolean = extension.lowercase() !in audioExtensions

    fun isDownloadable(extension: String): Boolean =
        extension.lowercase() in extensions || extension.lowercase() in fileExtensions

    /** Lowercase extension from a URL path or a Content-Disposition-style filename, if any. */
    fun extensionOf(url: String, fileName: String? = null): String? {
        val raw = fileName?.trim()?.takeIf { it.isNotBlank() } ?: run {
            val path = url.substringBefore('?').substringBefore('#')
            val last = path.substringAfterLast('/')
            if (last.isBlank()) return null
            last
        }
        val dot = raw.lastIndexOf('.')
        if (dot <= 0 || dot == raw.length - 1) return null
        return raw.substring(dot + 1).lowercase().filter { it.isLetterOrDigit() }.take(16)
            .takeIf { it.isNotBlank() }
    }

    /** A safe title stem derived from the URL, or null when the URL has no usable filename. */
    fun nameFromUrl(url: String): String? {
        val path = url.substringBefore('?').substringBefore('#')
        val last = path.substringAfterLast('/')
        if (last.isBlank() || !last.contains('.')) return null
        return sanitize(last.substringBeforeLast('.')).takeIf { it != "Media" }
    }

    fun mime(extension: String, hasVideo: Boolean): String = when (extension.lowercase()) {
        "mp4", "m4v" -> if (hasVideo) "video/mp4" else "audio/mp4"
        "m4a" -> "audio/mp4"
        "webm", "weba" -> if (hasVideo) "video/webm" else "audio/webm"
        "mkv" -> "video/x-matroska"
        "mp3" -> "audio/mpeg"
        "aac" -> "audio/aac"
        "opus", "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "mov" -> "video/quicktime"
        "avi" -> "video/x-msvideo"
        "flv" -> "video/x-flv"
        "3gp" -> "video/3gpp"
        "ts" -> "video/mp2t"
        else -> "application/octet-stream"
    }
}
