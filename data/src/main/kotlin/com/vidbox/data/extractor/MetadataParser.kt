package com.vidbox.data.extractor

import com.vidbox.domain.model.*
import com.vidbox.domain.util.FileNames
import kotlinx.serialization.json.*
import java.net.URI
import javax.inject.Inject

class MetadataParser @Inject constructor(private val json: Json) {
    fun parse(url: String, output: String): MediaInfo {
        val root = runCatching { json.parseToJsonElement(output).jsonObject }
            .getOrElse { throw Errors.exception(ErrorCode.ENGINE, it) }
        if (root.string("_type") in setOf("playlist", "multi_video")) throw Errors.exception(ErrorCode.UNSUPPORTED_URL)
        if (root["is_live"]?.jsonPrimitive?.booleanOrNull == true || root.string("live_status") in setOf("is_live", "is_upcoming"))
            throw Errors.exception(ErrorCode.LIVE)
        if (root["has_drm"]?.jsonPrimitive?.booleanOrNull == true) throw Errors.exception(ErrorCode.DRM)
        val rawFormats = (root["formats"] as? JsonArray)?.mapNotNull { it as? JsonObject }
            ?: listOf(root)
        val nonDrm = rawFormats.filter { it["has_drm"]?.jsonPrimitive?.booleanOrNull != true }
        if (nonDrm.isEmpty() && rawFormats.isNotEmpty()) throw Errors.exception(ErrorCode.DRM)
        val formats = nonDrm.mapNotNull { parseFormat(it, if (it === root) "best" else null) }
            .distinctBy { it.id }
        if (formats.isEmpty()) throw Errors.exception(ErrorCode.FORMAT_UNAVAILABLE)
        val thumbs = (root["thumbnails"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
        val thumbnail = thumbs.filter { (it.number("width") ?: 600.0) <= 960 }
            .maxByOrNull { it.number("width") ?: 0.0 }?.string("url") ?: root.string("thumbnail")
        return MediaInfo(url = url, title = root.string("title")?.take(1000) ?: "Untitled media",
            thumbnailUrl = thumbnail?.takeIf { it.startsWith("https://") },
            durationSeconds = root.number("duration")?.takeIf { it >= 0 },
            uploader = (root.string("uploader") ?: root.string("channel"))?.take(240),
            source = URI(url).host, extractor = root.string("extractor") ?: "generic", formats = formats)
    }

    private fun parseFormat(obj: JsonObject, fallbackId: String?): MediaFormat? {
        val id = obj.string("format_id") ?: fallbackId ?: return null
        val ext = obj.string("ext")?.lowercase()?.takeIf { it in FileNames.extensions } ?: return null
        if (!id.matches(Regex("[A-Za-z0-9_.-]{1,128}"))) return null
        val videoCodec = obj.string("vcodec")
        val audioCodec = obj.string("acodec")
        val hasVideo = videoCodec?.let { it != "none" } ?: (obj.number("height")?.let { it > 0 } ?: false)
        val hasAudio = audioCodec?.let { it != "none" }
        if (!hasVideo && hasAudio != true) return null
        val exact = obj.number("filesize")?.toLong()?.takeIf { it > 0 }
        val approximate = obj.number("filesize_approx")?.toLong()?.takeIf { it > 0 }
        return MediaFormat(id = id, extension = ext,
            width = obj.number("width")?.toInt()?.takeIf { it > 0 },
            height = obj.number("height")?.toInt()?.takeIf { it > 0 },
            fps = obj.number("fps")?.takeIf { it > 0 },
            videoCodec = videoCodec?.takeUnless { it == "none" },
            audioCodec = audioCodec?.takeUnless { it == "none" }, hasVideo = hasVideo, hasAudio = hasAudio,
            sizeBytes = exact ?: approximate, approximateSize = exact == null && approximate != null,
            bitrateKbps = obj.number(if (hasVideo) "tbr" else "abr") ?: obj.number("tbr"),
            protocol = obj.string("protocol"), note = obj.string("format_note")?.take(100))
    }
    private fun JsonObject.string(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    private fun JsonObject.number(key: String) = (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }
}
