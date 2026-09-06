package com.vidbox.domain

import com.vidbox.domain.model.*
import com.vidbox.domain.usecase.FormatPlanner
import org.junit.Assert.*
import org.junit.Test

class FormatPlannerTest {
    private val video = MediaFormat("137", "mp4", height = 1080, fps = 30.0, videoCodec = "avc1.640028", hasVideo = true, hasAudio = false, sizeBytes = 2000)
    private val aac = MediaFormat("140", "m4a", audioCodec = "mp4a.40.2", hasVideo = false, hasAudio = true, sizeBytes = 100, bitrateKbps = 128.0)
    private val opus = MediaFormat("251", "webm", audioCodec = "opus", hasVideo = false, hasAudio = true, bitrateKbps = 160.0)
    private fun info(formats: List<MediaFormat>) = MediaInfo("https://example.com/a", "A", source = "example.com", extractor = "generic", formats = formats)
    @Test fun selectsCompatibleAudioWithoutReencoding() {
        val options = FormatPlanner().options(info(listOf(video, aac, opus)))
        val mp4 = options.single { it.primary.id == "137" && it.container == "mp4" }
        assertEquals("140", mp4.audio!!.id)
        assertEquals(2100L, mp4.estimatedBytes)
        assertTrue(mp4.requiresMerging)
        assertFalse(options.any { it.primary.id == "137" && it.container == "webm" })
    }
    @Test fun doesNotSilentlyOfferAnUnmergeableVideo() {
        assertTrue(FormatPlanner().options(info(listOf(video))).isEmpty())
    }
    @Test fun missingDirectCodecMetadataIsNotInvented() {
        val direct = info(listOf(video.copy(id = "direct", hasAudio = null, videoCodec = null))).copy(isDirect = true)
        val option = FormatPlanner().options(direct).single()
        assertFalse(option.requiresMerging)
        assertNull(option.primary.hasAudio)
    }
    @Test fun respectsQualityCapAndContainerPreference() {
        val high = video.copy(id = "high", height = 2160, hasAudio = true)
        val low = video.copy(id = "low", height = 720, hasAudio = true)
        val planner = FormatPlanner()
        val options = planner.options(info(listOf(high, low)))
        assertEquals("low", planner.default(options, AppSettings(defaultQuality = 1080))!!.primary.id)
        assertEquals("high", planner.default(options, AppSettings(defaultQuality = 0))!!.primary.id)
    }
    @Test fun includesActualAudioFormats() {
        val options = FormatPlanner().options(info(listOf(video, aac, opus)))
        assertEquals(2, options.count { !it.primary.hasVideo })
    }
}
