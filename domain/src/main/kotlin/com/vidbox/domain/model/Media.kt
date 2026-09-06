package com.vidbox.domain.model

import kotlinx.serialization.Serializable

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
    val qualityLabel: String get() = if (hasVideo) height?.let { "${it}p" } ?: "Original video"
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
