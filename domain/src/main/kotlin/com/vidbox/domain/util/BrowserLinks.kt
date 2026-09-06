package com.vidbox.domain.util

import java.net.IDN
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Pure address-bar rules for the in-app browser: typed input becomes an HTTPS page or a
 * search request, never a custom scheme, and download headers are parsed without
 * interpolating site text into anything executable.
 */
object BrowserLinks {
    const val DEFAULT_HOMEPAGE = "https://duckduckgo.com/"
    const val SEARCH = "https://duckduckgo.com/?q="

    /** A conventional desktop Chrome identity for the desktop-mode option only. */
    const val DESKTOP_USER_AGENT =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) " +
            "Chrome/128.0.0.0 Safari/537.36"

    /**
     * Maps address-bar input to a loadable HTTPS URL: full URLs pass through, bare
     * hosts gain a scheme, http is upgraded to https, anything else searches. Returns
     * null only for input that is too long to be sensible.
     */
    fun normalize(raw: String): String? {
        val value = raw.trim()
        if (value.isEmpty() || value.length > 8192) return null
        val looksLikeUrl = !value.contains(' ') && (value.contains('.') || value.contains(':')) &&
            !value.contains('"') && !value.contains('<') && !value.contains('>') && !value.contains('\'')
        if (!looksLikeUrl) return SEARCH + URLEncoder.encode(value, "UTF-8")
        val withScheme = when {
            value.startsWith("https://", ignoreCase = true) -> value
            value.startsWith("http://", ignoreCase = true) -> "https://" + value.substringAfter("://")
            else -> "https://$value"
        }
        // java.net.URI rejects non-ASCII hostnames outright, so international names are
        // converted to their IDN ASCII (punycode) form before parsing.
        val candidate = if (withScheme.any { it.code > 127 }) punycodeHost(withScheme)
            ?: return SEARCH + URLEncoder.encode(value, "UTF-8") else withScheme
        val uri = runCatching { URI(candidate) }.getOrNull() ?: return SEARCH + URLEncoder.encode(value, "UTF-8")
        if (uri.host.isNullOrBlank()) return SEARCH + URLEncoder.encode(value, "UTF-8")
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

    /** Only an HTTPS page qualifies as a homepage; anything else falls back to the default. */
    fun homepage(value: String?): String = value?.trim()?.takeIf { input ->
        input.length <= 2048 && runCatching {
            val uri = URI(input)
            uri.scheme.equals("https", true) && !uri.host.isNullOrBlank()
        }.getOrDefault(false)
    } ?: DEFAULT_HOMEPAGE
}
