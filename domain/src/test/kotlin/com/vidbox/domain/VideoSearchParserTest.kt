package com.vidbox.domain

import com.vidbox.domain.model.DownloadException
import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.util.VideoSearchParser
import kotlinx.serialization.json.Json
import org.junit.Assert.*
import org.junit.Test

class VideoSearchParserTest {
    private val parser = VideoSearchParser(Json { ignoreUnknownKeys = true })

    @Test
    fun flatPlaylistEntriesBecomeResults() {
        val output = """
            {"_type":"playlist","id":"ytsearch20:test","title":"test","entries":[
              {"_type":"url","id":"dQw4w9WgXcQ","title":"A first video","url":"https://www.youtube.com/watch?v=dQw4w9WgXcQ",
               "duration":212.0,"view_count":1400000000.0,"channel":"A channel",
               "thumbnails":[{"url":"http://insecure.example/i.jpg","width":40},{"url":"https://i.example/320.jpg","width":320},
                             {"url":"https://i.example/1280.jpg","width":1280}]},
              {"id":"no-thumb","title":"Second, id-only entry","channel":"@handle","duration":61.0},
              {"_type":"playlist","id":"mixed-list","title":"ignored nested playlist"},
              {"id":"live-one","title":"Live now","is_live":true},
              {"id":"Untitled","title":""}
            ]}
        """.trimIndent()
        val results = parser.parse(output)
        assertEquals(2, results.size)
        val first = results.first()
        assertEquals("dQw4w9WgXcQ", first.id)
        assertEquals("A first video", first.title)
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", first.url)
        assertEquals("A channel", first.channel)
        assertEquals(212.0, first.durationSeconds!!, 0.0)
        assertEquals(1_400_000_000L, first.viewCount!!)
        assertEquals("https://i.example/1280.jpg", first.thumbnailUrl)
        val second = results[1]
        assertEquals("https://www.youtube.com/watch?v=no-thumb", second.url)
        assertEquals("handle", second.channel)
        assertNotNull(second.viewsLabel)
    }

    @Test
    fun singleVideoOutputIsAccepted() {
        val output = """
            {"id":"abc123Def45","title":"One video","webpage_url":"https://www.youtube.com/watch?v=abc123Def45",
             "duration":42.0,"thumbnail":"https://i.example/t.jpg"}
        """.trimIndent()
        val results = parser.parse(output)
        assertEquals(1, results.size)
        assertEquals("One video", results.single().title)
        assertEquals("https://i.example/t.jpg", results.single().thumbnailUrl)
    }

    @Test
    fun malformedEngineOutputFailsCleanly() {
        try {
            parser.parse("this is not json")
            fail("Expected a DownloadException")
        } catch (error: DownloadException) {
            assertEquals(ErrorCode.ENGINE, error.error.code)
        }
    }

    @Test
    fun idsWithoutTitlesAreDropped() {
        val output = """{"entries":[{"id":"x1"},{"id":"x2","title":"ok"}]}"""
        val results = parser.parse(output)
        assertEquals(listOf("x2"), results.map { it.id })
    }
}
