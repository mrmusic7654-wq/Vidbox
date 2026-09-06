package com.vidbox.domain

import com.vidbox.domain.model.DownloadException
import com.vidbox.domain.util.UrlValidator
import org.junit.Assert.*
import org.junit.Test

class UrlValidatorTest {
    @Test fun acceptsAndTrimsHttps() {
        assertEquals("https://example.com/watch?v=abc&list=1", UrlValidator.validate(" https://example.com/watch?v=abc&list=1 "))
    }
    @Test fun rejectsUnsafeOrIncompleteUrls() {
        listOf("", "example.com", "http://example.com/video.mp4", "file:///etc/passwd", "javascript:alert(1)",
            "https://u:password@example.com/a", "https://example.com/a\nb", "https://example.com/a b",
            "https://example.com:0/", "https://example.com:65536/", "--exec rm -rf /", "https:///a").forEach {
            assertThrows("Should reject $it", DownloadException::class.java) { UrlValidator.validate(it) }
        }
    }
    @Test fun preservesSignedUrlBytesAndEscapes() {
        val url = "https://cdn.example.com/a%20b.mp4?sig=abc%2Fdef%3D&expires=123"
        assertEquals(url, UrlValidator.validate(url))
    }
    @Test fun extractsSharedLinkWithoutExecutingText() {
        assertEquals("https://example.com/watch?v=1", UrlValidator.findInSharedText("Watch this: https://example.com/watch?v=1."))
    }
}
