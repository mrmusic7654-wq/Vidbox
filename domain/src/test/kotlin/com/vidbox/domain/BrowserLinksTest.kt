package com.vidbox.domain

import com.vidbox.domain.util.BrowserLinks
import org.junit.Assert.*
import org.junit.Test

class BrowserLinksTest {
    @Test fun completeHttpsUrlPassesThrough() {
        val input = "https://example.com/watch?v=abc&list=x#t=30"
        assertEquals(input, BrowserLinks.normalize(input))
    }

    @Test fun bareHostGainsHttpsScheme() {
        assertEquals("https://example.com/video", BrowserLinks.normalize("example.com/video"))
    }

    @Test fun plainHttpInputIsUpgradedToHttps() {
        assertEquals("https://example.com/", BrowserLinks.normalize("http://example.com/"))
    }

    @Test fun proseBecomesAnEncodedSearchRequest() {
        val result = BrowserLinks.normalize("rocket launch 2026")
        assertTrue(result!!.startsWith(BrowserLinks.SEARCH))
        assertTrue(result.contains("rocket+launch+2026"))
    }

    @Test fun blankAndAbsurdInputAreRejected() {
        assertNull(BrowserLinks.normalize(""))
        assertNull(BrowserLinks.normalize("   "))
        assertNull(BrowserLinks.normalize("https://example.com/" + "a".repeat(9000)))
    }

    @Test fun internationalHostIsNormalizedToAscii() {
        val result = BrowserLinks.normalize("https://exämple.com/page")
        assertTrue(result!!.startsWith("https://xn--"))
    }

    @Test fun rfc5987Utf8FilenameWins() {
        val header = "attachment; filename=\"fallback.bin\"; filename*=UTF-8''caf%C3%A9%20report.pdf"
        assertEquals("café report.pdf", BrowserLinks.dispositionFilename(header))
    }

    @Test fun quotedPlainFilenameIsUsed() {
        assertEquals("setup file.zip", BrowserLinks.dispositionFilename("attachment; filename=\"setup file.zip\""))
    }

    @Test fun missingHeaderYieldsNoFilename() {
        assertNull(BrowserLinks.dispositionFilename(null))
        assertNull(BrowserLinks.dispositionFilename("attachment"))
    }

    @Test fun onlyHttpsQualifiesAsHomepage() {
        assertEquals("https://example.com/start", BrowserLinks.homepage("https://example.com/start"))
        assertEquals("https://example.com/start", BrowserLinks.homepage("  https://example.com/start  "))
        assertEquals(BrowserLinks.DEFAULT_HOMEPAGE, BrowserLinks.homepage("http://example.com/"))
        assertEquals(BrowserLinks.DEFAULT_HOMEPAGE, BrowserLinks.homepage("javascript:alert(1)"))
        assertEquals(BrowserLinks.DEFAULT_HOMEPAGE, BrowserLinks.homepage(""))
        assertEquals(BrowserLinks.DEFAULT_HOMEPAGE, BrowserLinks.homepage(null))
    }

    @Test fun desktopIdentityIsADesktopBrowserString() {
        assertTrue(BrowserLinks.DESKTOP_USER_AGENT.contains("Chrome/"))
        assertTrue(BrowserLinks.DESKTOP_USER_AGENT.contains("Safari"))
    }
}
