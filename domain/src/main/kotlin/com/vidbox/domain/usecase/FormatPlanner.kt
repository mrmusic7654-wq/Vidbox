package com.vidbox.domain.usecase

import com.vidbox.domain.model.*
import com.vidbox.domain.util.FileNames
import javax.inject.Inject

class FormatPlanner @Inject constructor() {
    fun options(media: MediaInfo): List<FormatSelection> {
        val formats = media.formats.filter { it.extension in FileNames.extensions }
        val audio = formats.filter { !it.hasVideo && it.hasAudio != false }
        return formats.flatMap { primary ->
            if (!primary.hasVideo || primary.hasAudio != false || media.isDirect) {
                listOf(FormatSelection(primary))
            } else if (audio.isEmpty()) {
                // Some authorized sources genuinely contain no audio. Offer the original video,
                // retaining hasAudio=false so the UI explicitly labels it rather than inventing a track.
                listOf(FormatSelection(primary))
            } else {
                // When source audio exists, pair it instead of unexpectedly producing a silent video.
                listOf("mp4", "webm", "mkv").mapNotNull { container ->
                    if (!supportsVideo(container, primary.videoCodec)) return@mapNotNull null
                    val companion = audio.filter { supportsAudio(container, it.audioCodec, it.extension) }
                        .maxWithOrNull(compareBy<MediaFormat> { it.bitrateKbps ?: 0.0 }.thenBy { it.sizeBytes ?: 0 })
                    companion?.let { FormatSelection(primary, it, container) }
                }
            }
        }.distinctBy { it.key }.sortedWith(
            compareByDescending<FormatSelection> { it.primary.hasVideo }
                .thenByDescending { it.primary.height ?: 0 }
                .thenByDescending { it.primary.fps ?: 0.0 }
                .thenByDescending { it.primary.bitrateKbps ?: 0.0 }
                .thenBy { it.key },
        )
    }

    fun default(options: List<FormatSelection>, settings: AppSettings, video: Boolean = true): FormatSelection? {
        val candidates = options.filter { it.primary.hasVideo == video }
        val belowCap = candidates.filter { (it.primary.height ?: 0) <= settings.defaultQuality || settings.defaultQuality == 0 }
        val quality = belowCap.ifEmpty { candidates }
        val preferred = quality.filter { it.container == settings.defaultContainer }.ifEmpty { quality }
        return preferred.firstOrNull()
    }

    private fun supportsVideo(container: String, codec: String?): Boolean = when (container) {
        "mp4" -> codec?.lowercase()?.let { c -> listOf("avc", "h264", "hev", "hvc", "h265", "av01", "av1", "mpeg4", "mp4v").any(c::startsWith) } == true
        "webm" -> codec?.lowercase()?.let { c -> listOf("vp8", "vp9", "vp0", "av01", "av1").any(c::startsWith) } == true
        "mkv" -> true
        else -> false
    }

    private fun supportsAudio(container: String, codec: String?, ext: String): Boolean = when (container) {
        "mp4" -> ext in setOf("m4a", "aac") || codec?.let { it.startsWith("mp4a") || it.startsWith("aac") } == true
        "webm" -> codec?.let { it.startsWith("opus") || it.startsWith("vorbis") } == true
        "mkv" -> true
        else -> false
    }
}
