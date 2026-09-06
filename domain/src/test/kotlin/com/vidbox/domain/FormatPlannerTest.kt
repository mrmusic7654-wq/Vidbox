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
    @Test fun genuinelySilentMediaIsOfferedWithAnExplicitNoAudioFlag() {
        val selected = FormatPlanner().options(info(listOf(video))).single()
        assertFalse(selected.requiresMerging)
        assertEquals(false, selected.primary.hasAudio)
        assertEquals("mp4", selected.container)
        assertEquals("137", selected.primary.id)
    }
    @Test fun missingDirectCodecMetadataIsNotInvented() {
        val direct = info(listOf(video.copy(id = "direct", hasAudio = null, videoCodec = null))).copy(isDirect = true)
        val option = FormatPlanner().options(direct).single()
        assertFalse(option.requiresMerging)
        assertNull(option.primary.hasAudio)
        assertEquals("mp4", option.container)
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
    @Test fun videoIsMp4OnlyWithOneOptionPerAvailableQuality() {
        val muxed1080 = video.copy(id = "137", hasAudio = true, audioCodec = "mp4a.40.2", sizeBytes = 2100)
        val videoOnly1080 = video.copy(id = "137b", bitrateKbps = 9000.0)
        val v720 = video.copy(id = "136", height = 720)
        val v480 = video.copy(id = "135", height = 480)
        val vUnknown = video.copy(id = "999", height = null, width = null, videoCodec = null)
        val options = FormatPlanner().options(info(listOf(muxed1080, videoOnly1080, v720, v480, vUnknown, aac)))
        val videoOptions = options.filter { it.primary.hasVideo }
        assertEquals(listOf(1080, 720, 480), videoOptions.map { it.primary.height })
        assertTrue(videoOptions.all { it.container == "mp4" })
        assertFalse(videoOptions.any { it.primary.id == "999" })
        // A matching muxed stream wins over a video-only stream of the same quality.
        assertEquals("137", videoOptions.first { it.primary.height == 1080 }.primary.id)
        assertFalse(videoOptions.first { it.primary.height == 1080 }.requiresMerging)
    }
    @Test fun differentFrameRatesAreDistinctQualities() {
        val p30 = video.copy(id = "p30")
        val p60 = video.copy(id = "p60", fps = 60.0)
        val options = FormatPlanner().options(info(listOf(p30, p60, aac)))
        assertEquals(2, options.count { it.primary.hasVideo })
    }
    @Test fun muxedNonMp4FormatIsRemuxedOnlyWhenTheCodecAllows() {
        val webmAvc = video.copy(id = "640", extension = "webm", hasAudio = true, audioCodec = "opus")
        val option = FormatPlanner().options(info(listOf(webmAvc))).single { it.primary.hasVideo }
        assertEquals("mp4", option.container)
        assertFalse(option.requiresMerging)
    }
    @Test fun unsupportedCodecIsNotMislabelledAsMp4() {
        val vp9 = video.copy(id = "248", extension = "webm", videoCodec = "vp09.00.50.08", hasAudio = true, audioCodec = "opus")
        val options = FormatPlanner().options(info(listOf(vp9, aac)))
        assertTrue(options.none { it.primary.hasVideo })
    }
    @Test fun transportStreamsWithUnknownCodecStillProduceMp4Merge() {
        val hls = video.copy(id = "1080p", extension = "ts", videoCodec = null, protocol = "m3u8_native")
        val option = FormatPlanner().options(info(listOf(hls, aac))).single { it.primary.hasVideo }
        assertEquals("mp4", option.container)
        assertEquals("140", option.audio?.id)
        assertTrue(option.requiresMerging)
    }
    @Test fun directMediaKeepsItsActualContainer() {
        val direct = info(listOf(video.copy(id = "direct", extension = "webm", videoCodec = null, hasAudio = null))).copy(isDirect = true)
        val option = FormatPlanner().options(direct).single()
        assertEquals("webm", option.container)
        assertEquals("direct", option.primary.id)
    }
}
