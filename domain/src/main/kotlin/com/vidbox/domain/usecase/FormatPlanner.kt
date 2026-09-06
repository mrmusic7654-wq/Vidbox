package com.vidbox.domain.usecase

import com.vidbox.domain.model.*
import com.vidbox.domain.util.FileNames
import kotlin.math.roundToInt
import javax.inject.Inject

/**
 * Plans download options from the formats a source actually reports.
 *
 * Video is offered only as MP4, with exactly one option per quality the source provides
 * (resolution + frame rate). Stream copy keeps the original codec — nothing is re-encoded,
 * so a source that only ships WebM/VP9 is honestly reported rather than mislabelled as MP4.
 */
class FormatPlanner @Inject constructor() {
    fun options(media: MediaInfo): List<FormatSelection> {
        val formats = media.formats.filter { it.extension in FileNames.extensions }
        if (media.isDirect) {
            // A direct HTTP probe yields one original file. Never invent qualities or containers.
            return formats.map(::FormatSelection).distinctBy { it.key }.sortedWith(
                compareByDescending<FormatSelection> { it.primary.hasVideo }
                    .thenByDescending { it.primary.sizeBytes ?: 0L }
                    .thenBy { it.primary.extension })
        }
        val audio = formats.filter { !it.hasVideo && it.hasAudio != false }
        val qualityGroups = formats.filter { it.hasVideo }
            .groupBy { it.height to fpsKey(it.fps) }
        // If any quality is known, hide raw formats with no reported resolution: their quality is
        // unknown, so offering them would show an option with an unknown label.
        val knownGroups = qualityGroups.filterKeys { it.first != null }
        val video = (knownGroups.ifEmpty { qualityGroups }).values
            .mapNotNull { chooseMp4(it, audio) }
        val audioOnly = audio.map(::FormatSelection)
        return (video + audioOnly).distinctBy { it.key }.sortedWith(
            compareByDescending<FormatSelection> { it.primary.hasVideo }
                .thenByDescending { it.primary.height ?: 0 }
                .thenByDescending { it.primary.fps ?: 0.0 }
                .thenByDescending { it.primary.bitrateKbps ?: 0.0 }
                .thenBy { it.key })
    }

    private fun chooseMp4(quality: List<MediaFormat>, audio: List<MediaFormat>): FormatSelection? {
        // Prefer one stream that already carries audio: it downloads and saves without merging.
        val muxed = quality.filter { it.hasAudio != false }.mapNotNull { format ->
            when {
                format.extension == "mp4" -> FormatSelection(format)
                supportsMp4(format.videoCodec, format.extension, format.protocol) -> FormatSelection(format, container = "mp4")
                else -> null
            }
        }.maxWithOrNull(compareByDescending<FormatSelection> {
            (it.primary.bitrateKbps ?: 0.0) + (it.primary.sizeBytes ?: 0L).toDouble()
        })
        if (muxed != null) return muxed

        val videoOnly = quality.filter { it.hasAudio == false }.maxWithOrNull(
            compareBy<MediaFormat> { it.bitrateKbps ?: 0.0 }.thenBy { it.sizeBytes ?: 0L })
            ?: return null
        if (!supportsMp4(videoOnly.videoCodec, videoOnly.extension, videoOnly.protocol)) return null
        if (audio.isEmpty()) return FormatSelection(videoOnly, container = "mp4")
        val companion = audio.filter { supportsAudioMp4(it.audioCodec, it.extension) }
            .maxWithOrNull(compareBy<MediaFormat> { it.bitrateKbps ?: 0.0 }.thenBy { it.sizeBytes ?: 0L })
            ?: return null
        return FormatSelection(videoOnly, companion, "mp4")
    }

    fun default(options: List<FormatSelection>, settings: AppSettings, video: Boolean = true): FormatSelection? {
        val candidates = options.filter { it.primary.hasVideo == video }
        val belowCap = candidates.filter { (it.primary.height ?: 0) <= settings.defaultQuality || settings.defaultQuality == 0 }
        val quality = belowCap.ifEmpty { candidates }
        val preferred = quality.filter { it.container == settings.defaultContainer }.ifEmpty { quality }
        return preferred.firstOrNull()
    }

    /** Actually reported frame-rate buckets so 29.97/30 and 23.976/24 are never shown twice. */
    private fun fpsKey(fps: Double?): Int = if (fps == null || fps <= 0) 0 else (fps * 2).roundToInt()

    private fun supportsMp4(codec: String?, ext: String, protocol: String?): Boolean {
        val known = codec?.lowercase()
        if (known != null) {
            return listOf("avc", "h264", "hev", "hvc", "h265", "av01", "av1", "mpeg4", "mp4v").any(known::startsWith)
        }
        // No codec report (common for generic and HLS sources). Only claim MP4 when FFmpeg can
        // stream-copy the container without re-encoding: MP4-family files or transport streams.
        return ext in setOf("mp4", "m4v", "mov", "ts") || protocol?.contains("m3u8") == true
    }

    private fun supportsAudioMp4(codec: String?, ext: String): Boolean =
        ext in setOf("m4a", "aac") || codec?.let { it.startsWith("mp4a") || it.startsWith("aac") } == true
}
