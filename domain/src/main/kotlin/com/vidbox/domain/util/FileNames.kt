package com.vidbox.domain.util

import java.nio.charset.StandardCharsets
import java.text.Normalizer

object FileNames {
    private val invalid = Regex("[\\p{Cc}\\p{Cf}\\\\/:*?\"<>|]")
    private val reserved = Regex("(?i)^(con|prn|aux|nul|com[0-9]|lpt[0-9])$")
    val extensions = setOf("mp4", "m4v", "webm", "mkv", "mov", "avi", "flv", "3gp", "ts",
        "m4a", "mp3", "aac", "opus", "ogg", "oga", "wav", "flac", "weba")

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
        require(extension in extensions)
        return "${sanitize(title)}-${id.take(8)}.$extension"
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
