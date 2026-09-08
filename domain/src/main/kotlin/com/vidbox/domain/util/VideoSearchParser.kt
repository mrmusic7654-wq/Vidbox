package com.vidbox.domain.util

import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.model.Errors
import com.vidbox.domain.model.VideoSearchResult
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import javax.inject.Inject

/**
 * Parses `yt-dlp --flat-playlist --dump-single-json` output for a `ytsearch…` query into
 * plain result rows. Only whitelisted, length-bounded fields are read; nothing from the
 * engine output is executed or stored verbatim beyond the returned model.
 */
class VideoSearchParser @Inject constructor(private val json: Json) {
    fun parse(output: String): List<VideoSearchResult> {
        val root = runCatching { json.parseToJsonElement(output).jsonObject }
            .getOrElse { throw Errors.exception(ErrorCode.ENGINE, it) }
        val entries = (root["entries"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?: listOf(root)
        return entries.mapNotNull(::row).distinctBy { it.id }.take(MAX_RESULTS)
    }

    private fun row(entry: JsonObject): VideoSearchResult? {
        if (entry["_type"]?.let { (it as? JsonPrimitive)?.contentOrNull } == "playlist") return null
        if (entry.boolean("is_live") == true) return null
        val id = entry.string("id")?.take(64) ?: return null
        val title = entry.string("title")?.take(300)?.takeIf { it.isNotBlank() } ?: return null
        val url = entry.string("url")?.takeIf { it.startsWith("http") }
            ?: entry.string("webpage_url")?.takeIf { it.startsWith("http") }
            ?: watchUrl(id) ?: return null
        return VideoSearchResult(
            id = id,
            title = title,
            url = url,
            thumbnailUrl = entry.thumbnail(),
            channel = (entry.string("channel") ?: entry.string("uploader")
                ?: entry.string("uploader_id")?.removePrefix("@"))?.take(120),
            durationSeconds = entry.number("duration")?.takeIf { it >= 0 },
            viewCount = entry.number("view_count")?.toLong()?.takeIf { it >= 0 },
        )
    }

    private fun watchUrl(id: String): String? =
        if (Regex("[A-Za-z0-9_-]{6,24}").matches(id)) "https://www.youtube.com/watch?v=$id" else null

    /** Flat entries ship a `thumbnails` array; the widest https variant previews best. */
    private fun JsonObject.thumbnail(): String? {
        val candidates = (this["thumbnails"] as? JsonArray)
            ?.mapNotNull { it as? JsonObject }
            ?.mapNotNull { item ->
                val url = item.string("url")?.takeIf { it.startsWith("https://") } ?: return@mapNotNull null
                (item.number("width") ?: item.number("preference") ?: 0.0) to url
            }
            ?.sortedByDescending { it.first }
            ?.map { it.second }
            .orEmpty()
        return (candidates.firstOrNull() ?: string("thumbnail")?.takeIf { it.startsWith("https://") })?.take(2048)
    }

    private fun JsonObject.string(key: String) =
        (this[key] as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }

    private fun JsonObject.number(key: String) =
        (this[key] as? JsonPrimitive)?.doubleOrNull?.takeIf { it.isFinite() }

    private fun JsonObject.boolean(key: String) =
        (this[key] as? JsonPrimitive)?.booleanOrNull

    private companion object { const val MAX_RESULTS = 40 }
}
