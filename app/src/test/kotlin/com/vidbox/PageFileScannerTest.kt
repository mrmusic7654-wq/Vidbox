package com.vidbox

import com.vidbox.presentation.browser.parsePageFiles
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The browser's quick-download scanner receives `url<TAB>name` lines from the page scan
 * script; parsing keeps only real download targets (media/file extensions), de-duplicates,
 * and falls back to the URL file name when the page offers no label.
 */
class PageFileScannerTest {
    @Test
    fun keepsMediaAndFileLinksAndDropsPageLinks() {
        val payload = listOf(
            "https://cdn.example.com/clips/clip.mp4\tsunny clip",
            "https://cdn.example.com/audio/track.mp3\t",
            "https://files.example.com/pack.zip\tpack",
            "https://seeds.example.com/linux.iso\tlinux",
            "https://www.example.com/home\thome",
            "https://www.example.com/about\tsome page",
            "javascript:void(0)\tbad",
            "/relative/movie.webm\trelative",
        ).joinToString("\n")
        val files = parsePageFiles(payload)
        assertEquals(listOf("https://cdn.example.com/clips/clip.mp4",
            "https://cdn.example.com/audio/track.mp3",
            "https://files.example.com/pack.zip",
            "https://seeds.example.com/linux.iso"), files.map { it.url })
        assertEquals("sunny clip", files[0].name)
        assertEquals("track.mp3", files[1].name)
    }

    @Test
    fun duplicatesAndQueryVariantsAreCollapsedOnce() {
        val payload = listOf(
            "https://cdn.example.com/a.mp4\tfirst",
            "https://cdn.example.com/a.mp4?token=x\tsecond",
            "https://cdn.example.com/a.mp4#t=10\tthird",
        ).joinToString("\n")
        val files = parsePageFiles(payload)
        assertEquals(1, files.size)
        assertEquals("first", files.single().name)
    }

    @Test
    fun unknownExtensionsAreRejectedRegardlessOfLength() {
        val payload = listOf(
            "https://cdn.example.com/movie.superextension\tlong",
            "https://cdn.example.com/torrent.torrent\tbundle",
        ).joinToString("\n")
        val files = parsePageFiles(payload)
        assertEquals(listOf("https://cdn.example.com/torrent.torrent"), files.map { it.url })
    }

    @Test
    fun resultsAreCappedAtForty() {
        val payload = (1..60).joinToString("\n") { n -> "https://cdn.example.com/f$n.mp4\tfile $n" }
        assertEquals(40, parsePageFiles(payload).size)
    }

    @Test
    fun malformedLinesNeverThrow() {
        assertTrue(parsePageFiles("").isEmpty())
        assertTrue(parsePageFiles("\n\t\n   ").isEmpty())
        assertTrue(parsePageFiles("not a url\tx").isEmpty())
        assertTrue(parsePageFiles("https://cdn.example.com/file.mkv").isNotEmpty())
    }
}
