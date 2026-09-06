package com.vidbox.data

import com.vidbox.data.extractor.MetadataParser
import com.vidbox.domain.model.*
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class MetadataParserTest {
    private val parser = MetadataParser(Json { ignoreUnknownKeys = true })
    @Test fun handlesMissingMetadataAndSkipsStoryboards() {
        val media = parser.parse("https://example.com/a", """{"formats":[
            {"format_id":"137","ext":"mp4","height":1080,"vcodec":"avc1","acodec":"none","filesize_approx":1234},
            {"format_id":"140","ext":"m4a","vcodec":"none","acodec":"mp4a"},
            {"format_id":"sb","ext":"mhtml","vcodec":"none","acodec":"none"}]}""")
        assertEquals("Untitled media", media.title)
        assertNull(media.durationSeconds); assertNull(media.thumbnailUrl)
        assertEquals(2, media.formats.size)
        assertFalse(media.formats[0].hasAudio!!)
        assertTrue(media.formats[0].approximateSize)
    }
    @Test fun rejectsDrmAndLiveWithoutAttemptingBypass() {
        assertEquals(ErrorCode.DRM, assertThrows(DownloadException::class.java) {
            parser.parse("https://example.com", """{"has_drm":true}""")
        }.error.code)
        assertEquals(ErrorCode.LIVE, assertThrows(DownloadException::class.java) {
            parser.parse("https://example.com", """{"is_live":true}""")
        }.error.code)
    }
    @Test fun genericHlsWithMissingCodecsStillOffersItsOriginalFormat() {
        val media = parser.parse("https://example.com/stream.m3u8", """{"formats":[{"format_id":"0","ext":"mp4","protocol":"m3u8_native"}]}""")
        val format = media.formats.single()
        assertNull(format.videoCodec); assertNull(format.audioCodec); assertNull(format.height); assertNull(format.hasAudio)
        assertEquals("Original media", format.qualityLabel)
    }
    @Test fun rejectsSelectorInjection() {
        assertThrows(DownloadException::class.java) {
            parser.parse("https://example.com", """{"formats":[{"format_id":"best+exec","ext":"mp4","vcodec":"avc1","acodec":"aac"}]}""")
        }
    }
}
