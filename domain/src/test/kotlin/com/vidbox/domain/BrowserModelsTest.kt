package com.vidbox.domain

import com.vidbox.domain.model.*
import com.vidbox.domain.util.BrowserLinks
import com.vidbox.domain.util.ErrorMapper
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException

class BrowserModelsTest {
    @Test fun builtInEnginesAreHttpsAndDistinct() {
        assertEquals(SearchEngines.builtIn.size, SearchEngines.builtIn.map { it.id }.toSet().size)
        SearchEngines.builtIn.forEach { engine ->
            assertTrue(engine.template.startsWith("https://"))
            assertEquals(1, Regex("%s").findAll(engine.template).count())
            assertTrue(engine.searchUrl("a b").startsWith(engine.searchPrefix))
        }
    }

    @Test fun resolveFallsBackToDefaultForUnknownOrInvalidChoices() {
        assertEquals(SearchEngines.GOOGLE, SearchEngines.resolve("google", null))
        assertEquals(SearchEngines.DEFAULT, SearchEngines.resolve("nope", null))
        assertEquals(SearchEngines.DEFAULT, SearchEngines.resolve(null, null))
        assertEquals(SearchEngines.DEFAULT, SearchEngines.resolve(SearchEngines.CUSTOM_ID, "http://insecure.example/?q=%s"))
        assertEquals(SearchEngines.DEFAULT, SearchEngines.resolve(SearchEngines.CUSTOM_ID, "https://two.example/?q=%s&r=%s"))
        val custom = SearchEngines.resolve(SearchEngines.CUSTOM_ID, "https://search.example/find?q=%s&src=app")
        assertEquals(SearchEngines.CUSTOM_ID, custom.id)
        assertEquals("https://search.example/find?q=rocket+launch&src=app", custom.searchUrl("rocket launch"))
        assertEquals("https://search.example/", custom.homepage)
    }

    @Test fun normalizeUsesTheSelectedEngine() {
        val engine = SearchEngines.BRAVE
        assertEquals("https://search.brave.com/search?q=cat+videos", BrowserLinks.normalize("cat videos", engine))
        assertEquals("https://example.com", BrowserLinks.normalize("example.com", engine))
        assertEquals("cat videos", BrowserLinks.searchQuery("https://search.brave.com/search?q=cat+videos", engine))
        assertNull(BrowserLinks.searchQuery("https://example.com/?q=cat", engine))
    }

    @Test fun urlDetectionSeparatesAddressesFromQueries() {
        listOf("example.com", "sub.example.co.uk/path?x=1", "localhost:8080", "192.168.1.10/admin", "https://a.b", "HTTP://EXAMPLE.COM").forEach {
            assertTrue("should be a URL: $it", BrowserLinks.looksLikeUrl(it))
        }
        listOf("how to bake bread", "example", "3.14", "what is example.com", "mailto:someone@example.com", "javascript:alert(1)", "a.1").forEach {
            assertFalse("should be a search: $it", BrowserLinks.looksLikeUrl(it))
        }
        assertTrue(BrowserLinks.normalize("javascript:alert(1)")!!.startsWith("https://"))
    }

    @Test fun displayHostAndWebPageChecks() {
        assertEquals("example.com", BrowserLinks.displayHost("https://www.example.com/watch?v=1"))
        assertNull(BrowserLinks.displayHost("about:blank"))
        assertTrue(BrowserLinks.isWebPage("https://example.com/"))
        assertTrue(BrowserLinks.isWebPage("http://example.com/"))
        assertFalse(BrowserLinks.isWebPage("about:blank"))
        assertFalse(BrowserLinks.isWebPage("javascript:void(0)"))
        assertFalse(BrowserLinks.isWebPage(null))
    }

    @Test fun proxyValidationCatchesStructuralMistakes() {
        assertNull(ProxyConfig.validate(ProxyType.HTTP, "proxy.example.com", 8080))
        assertNull(ProxyConfig.validate(ProxyType.SOCKS5, "10.0.0.5", 1080))
        assertNotNull(ProxyConfig.validate(ProxyType.HTTP, "", 8080))
        assertNotNull(ProxyConfig.validate(ProxyType.HTTP, "http://proxy.example.com", 8080))
        assertNotNull(ProxyConfig.validate(ProxyType.HTTP, "proxy.example.com:8080", 8080))
        assertNotNull(ProxyConfig.validate(ProxyType.HTTP, "proxy.example.com", 0))
        assertNotNull(ProxyConfig.validate(ProxyType.HTTP, "proxy.example.com", 70000))
        assertNotNull(ProxyConfig.validate(ProxyType.HTTPS, "10.0.0.5", 443))
    }

    @Test fun proxyConfigNeverPrintsCredentials() {
        val config = ProxyConfig(true, ProxyType.HTTP, "proxy.example.com", 3128, "alice", "s3cret")
        assertTrue(config.hasCredentials)
        assertFalse(config.toString().contains("alice"))
        assertFalse(config.toString().contains("s3cret"))
        assertEquals("http://proxy.example.com:3128", config.endpoint)
    }

    @Test fun errorMapperDistinguishesProxyFailures() {
        assertEquals(ErrorCode.TIMEOUT, ErrorMapper.from(SocketTimeoutException("read")).code)
        assertEquals(ErrorCode.PROXY_UNAVAILABLE, ErrorMapper.from(SocketTimeoutException("read"), viaProxy = true).code)
        assertEquals(ErrorCode.NETWORK, ErrorMapper.from(ConnectException("refused")).code)
        assertEquals(ErrorCode.PROXY_UNAVAILABLE, ErrorMapper.from(ConnectException("refused"), viaProxy = true).code)
        assertEquals(ErrorCode.PROXY_AUTH, ErrorMapper.from(IOException("Failed to authenticate with proxy")).code)
        assertEquals(ErrorCode.PROXY_AUTH, ErrorMapper.httpStatus(407)!!.code)
        assertNull(ErrorMapper.httpStatus(204))
        assertEquals(ErrorCode.NETWORK, ErrorMapper.from(IOException("unexpected end of stream")).code)
        assertEquals(ErrorCode.PROXY_UNAVAILABLE, ErrorMapper.from(IOException("unexpected end of stream"), viaProxy = true).code)
        assertEquals(ErrorCode.PROXY_AUTH, ErrorMapper.engine("ERROR: HTTP Error 407: Proxy Authentication Required").code)
        assertEquals(ErrorCode.PROXY_UNAVAILABLE, ErrorMapper.from(ProxyUnavailableException(ConnectException("refused"))).code)
        assertFalse(Errors.of(ErrorCode.PROXY_AUTH).retryable)
        assertTrue(Errors.of(ErrorCode.PROXY_UNAVAILABLE).retryable)
    }

    @Test fun lowStorageCarriesRequiredAndAvailable() {
        val error = Errors.lowStorage(150L * 1024 * 1024, 20L * 1024 * 1024)
        assertEquals(ErrorCode.LOW_STORAGE, error.code)
        assertTrue(error.detail!!.startsWith("Required: 150"))
        assertTrue(error.detail!!.contains("Available: 20"))
        assertTrue(error.fullMessage.startsWith(error.message))
        assertTrue(error.fullMessage.endsWith(error.detail!!))
        assertEquals(Errors.of(ErrorCode.NETWORK).message, Errors.of(ErrorCode.NETWORK).fullMessage)
    }
}
