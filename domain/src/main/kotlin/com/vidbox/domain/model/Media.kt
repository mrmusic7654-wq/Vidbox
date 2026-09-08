package com.vidbox.domain.model

import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

@Serializable
data class MediaFormat(
    val id: String,
    val extension: String,
    val width: Int? = null,
    val height: Int? = null,
    val fps: Double? = null,
    val videoCodec: String? = null,
    val audioCodec: String? = null,
    val hasVideo: Boolean,
    val hasAudio: Boolean? = null,
    val sizeBytes: Long? = null,
    val approximateSize: Boolean = false,
    val bitrateKbps: Double? = null,
    val protocol: String? = null,
    val note: String? = null,
) {
    val qualityLabel: String get() = if (height == null && videoCodec == null && audioCodec == null && bitrateKbps == null) "Original media"
        else if (hasVideo) height?.let { "${it}p" } ?: "Original video"
        else bitrateKbps?.takeIf { it > 0 }?.let { "${it.toInt()} kbps" } ?: "Original audio"
}

@Serializable
data class MediaInfo(
    val url: String,
    val title: String,
    val thumbnailUrl: String? = null,
    val durationSeconds: Double? = null,
    val uploader: String? = null,
    val source: String,
    val extractor: String,
    val formats: List<MediaFormat>,
    val isDirect: Boolean = false,
)

@Serializable
data class FormatSelection(
    val primary: MediaFormat,
    val audio: MediaFormat? = null,
    val container: String = primary.extension,
) {
    val requiresMerging: Boolean get() = audio != null
    val estimatedBytes: Long? get() = if (audio == null) primary.sizeBytes
        else primary.sizeBytes?.let { videoSize -> audio.sizeBytes?.let { videoSize + it } }
    val approximateSize: Boolean get() = primary.approximateSize || audio?.approximateSize == true
    val key: String get() = "${primary.id}:${audio?.id.orEmpty()}:$container"
}

/** One row of a public video-site (YouTube) search result, before any analysis. */
@Serializable
data class VideoSearchResult(
    val id: String,
    val title: String,
    val url: String,
    val thumbnailUrl: String? = null,
    val channel: String? = null,
    val durationSeconds: Double? = null,
    val viewCount: Long? = null,
) {
    /** Compact views line like "1.2M views"; null when the source reports nothing. */
    val viewsLabel: String? get() {
        val views = viewCount?.takeIf { it > 0 } ?: return null
        val rounded = when {
            views >= 1_000_000_000 -> "${(views / 100_000_000.0).let { if (it >= 9.95) it.toInt() else (it * 10).roundToInt() / 10.0 }}B"
            views >= 1_000_000 -> "${(views / 100_000.0).let { if (it >= 9.95) it.toInt() else (it * 10).roundToInt() / 10.0 }}M"
            views >= 1_000 -> "${(views / 100.0).let { if (it >= 9.95) it.toInt() else (it * 10).roundToInt() / 10.0 }}K"
            else -> views.toString()
        }
        return "$rounded views"
    }
}
