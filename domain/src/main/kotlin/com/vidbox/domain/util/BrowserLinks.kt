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
        val uri = runCatching { URI(withScheme) }.getOrNull() ?: return SEARCH + URLEncoder.encode(value, "UTF-8")
        val host = uri.host
        if (host.isNullOrBlank()) return SEARCH + URLEncoder.encode(value, "UTF-8")
        // java.net.URI percent-escapes instead of punyencoding non-ASCII hosts, which no
        // server resolves; international names are converted to their IDN ASCII form.
        if (host.any { it.code > 127 }) {
            val ascii = runCatching { IDN.toASCII(host) }.getOrNull() ?: return SEARCH + URLEncoder.encode(value, "UTF-8")
            return "https://" + ascii +
                (if (uri.port in 1..65535) ":${uri.port}" else "") +
                (uri.rawPath ?: "") +
                (uri.rawQuery?.let { "?$it" } ?: "") +
                (uri.rawFragment?.let { "#$it" } ?: "")
        }
        return uri.toASCIIString()
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
