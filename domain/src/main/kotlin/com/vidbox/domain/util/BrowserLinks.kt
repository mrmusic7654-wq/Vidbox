package com.vidbox.domain.util

import com.vidbox.domain.model.SearchEngine
import com.vidbox.domain.model.SearchEngines
import java.net.IDN
import java.net.URI
import java.net.URLDecoder

/**
 * Pure address-bar rules for the in-app browser: typed input becomes an HTTPS page or a
 * search request, never a custom scheme, and download headers are parsed without
 * interpolating site text into anything executable.
 *
 * The search provider is a parameter, never a constant baked into the rules; the
 * companion constants only describe the built-in default engine.
 */
object BrowserLinks {
    val DEFAULT_HOMEPAGE: String = SearchEngines.DEFAULT.homepage
    val SEARCH: String = SearchEngines.DEFAULT.searchPrefix

    /** A conventional desktop Chrome identity for the desktop-mode option only. */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36"

    private const val MAX_INPUT = 8192

    /** Hosts a user can type without a dot and still mean a URL. */
    private val schemelessHosts = setOf("localhost")

    /**
     * Maps address-bar input to a loadable HTTPS URL: full URLs pass through, bare
     * hosts gain a scheme, http is upgraded to https, anything else searches with
     * [engine]. Returns null only for input that is empty or too long to be sensible.
     */
    fun normalize(raw: String, engine: SearchEngine = SearchEngines.DEFAULT): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > MAX_INPUT) return null
        if (!looksLikeUrl(value)) return engine.searchUrl(value)
        return toHttpsUrl(value) ?: engine.searchUrl(value)
    }

    /** Whether [value] should be treated as an address rather than a search phrase. */
    fun looksLikeUrl(raw: String): Boolean {
        val value = raw.trim()
        if (value.isEmpty() || value.length > MAX_INPUT) return false
        if (value.any { it.isWhitespace() } || value.any { it in "\"<>'" }) return false
        if (value.startsWith("http://", ignoreCase = true) || value.startsWith("https://", ignoreCase = true)) return true
        // Other schemes (mailto:, javascript:, ftp:) are never opened as pages; they search instead.
        if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(value)) {
            val host = value.substringBefore('/').substringBefore('?').substringBefore(':')
            return host in schemelessHosts || (host.contains('.') && value.substringAfter(':').substringBefore('/').all { it.isDigit() })
        }
        val authority = value.substringBefore('/').substringBefore('?').substringBefore('#')
        val host = authority.substringAfterLast('@').substringBefore(':')
        if (host.isEmpty() || host.startsWith(".") || host.endsWith(".")) return false
        if (host in schemelessHosts) return true
        if (host.startsWith("[") && host.endsWith("]")) return true
        if (Regex("^\\d{1,3}(\\.\\d{1,3}){3}$").matches(host)) return true
        if (!host.contains('.')) return false
        val tld = host.substringAfterLast('.')
        return tld.length >= 2 && tld.all { it.isLetterOrDigit() || it == '-' || it.code > 127 } && !tld.all { it.isDigit() }
    }

    /** The https form of URL-like input, or null when it cannot be parsed as a URL with a host. */
    private fun toHttpsUrl(value: String): String? {
        val withScheme = when {
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) -> "https://" + value.substringAfter("://")
            else -> "https://$value"
        }
        // java.net.URI rejects non-ASCII hostnames outright, so international names are
        // converted to their IDN ASCII (punycode) form before parsing.
        val candidate = if (withScheme.any { it.code > 127 }) punycodeHost(withScheme) ?: return null else withScheme
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return null
        if (uri.host.isNullOrBlank()) return null
        return uri.toASCIIString()
    }

    /** Replaces only an international host with punycode; path/query stay percent-encodable. */
    private fun punycodeHost(input: String): String? {
        val schemeEnd = input.indexOf("://")
        if (schemeEnd < 0) return null
        val rest = input.substring(schemeEnd + 3)
        val authority = rest.substringBefore('/').substringBefore('?').substringBefore('#')
        val hostPort = authority.substringAfterLast('@')
        val host = hostPort.substringBefore(':')
        if (!host.any { it.code > 127 }) return input
        val ascii = runCatching { IDN.toASCII(host) }.getOrNull()?.takeIf(String::isNotBlank) ?: return null
        return input.substring(0, schemeEnd + 3) + (ascii + hostPort.removePrefix(host)) + rest.removePrefix(authority)
    }

    /** RFC 6266/5987 Content-Disposition filename, or null when the header carries none. */
    fun dispositionFilename(disposition: String?): String? {
        val value = disposition ?: return null
        Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)?.let {
            return runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull()?.takeIf(String::isNotBlank)
        }
        return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.isNotBlank() }
    }

    /** Only an HTTPS page qualifies as a homepage; anything else falls back to [fallback]. */
    fun homepage(value: String?, fallback: String = DEFAULT_HOMEPAGE): String = value?.trim()?.takeIf { input ->
        input.length <= 2048 && runCatching {
            val uri = URI(input)
            uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    } ?: fallback

    /** Registrable host shown in compact address bars and used for per-site settings ("example.com"). */
    fun displayHost(url: String?): String? {
        val host = runCatching { URI(url ?: return null).host }.getOrNull()?.lowercase() ?: return null
        return host.removePrefix("www.").takeIf { it.isNotBlank() }
    }

    /** The user's own query when [url] is a results page of [engine]; otherwise null. */
    fun searchQuery(url: String?, engine: SearchEngine): String? {
        if (url == null || !url.startsWith(engine.searchPrefix)) return null
        val rest = url.removePrefix(engine.searchPrefix)
        val suffix = engine.template.substringAfter("%s")
        val raw = if (suffix.isEmpty()) rest.substringBefore('&').substringBefore('#') else rest.substringBefore(suffix)
        return runCatching { URLDecoder.decode(raw, "UTF-8") }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    /** Only http(s) pages are stored in history, bookmarks, or restored tabs. */
    fun isWebPage(url: String?): Boolean {
        val value = url ?: return false
        return (value.startsWith("https://") || value.startsWith("http://")) && value.length <= MAX_INPUT &&
            runCatching { !URI(value).host.isNullOrBlank() }.getOrDefault(false)
    }
}
