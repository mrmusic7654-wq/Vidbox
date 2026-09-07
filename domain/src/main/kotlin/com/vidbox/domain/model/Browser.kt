package com.vidbox.domain.model

import java.net.URI
import java.net.URLEncoder

/**
 * A web search provider. Nothing in the browser refers to a concrete provider; the engine in
 * use is resolved from settings through [SearchEngines.resolve].
 */
data class SearchEngine(
    val id: String,
    val name: String,
    /** HTTPS template containing exactly one `%s` placeholder for the encoded query. */
    val template: String,
    val homepage: String,
) {
    fun searchUrl(query: String): String = template.replace("%s", URLEncoder.encode(query, "UTF-8"))

    /** The part of a results URL before the query; used to recognize our own search pages. */
    val searchPrefix: String get() = template.substringBefore("%s")
}

object SearchEngines {
    const val CUSTOM_ID = "custom"

    val DUCKDUCKGO = SearchEngine("duckduckgo", "DuckDuckGo", "https://duckduckgo.com/?q=%s", "https://duckduckgo.com/")
    val GOOGLE = SearchEngine("google", "Google", "https://www.google.com/search?q=%s", "https://www.google.com/")
    val BING = SearchEngine("bing", "Bing", "https://www.bing.com/search?q=%s", "https://www.bing.com/")
    val BRAVE = SearchEngine("brave", "Brave Search", "https://search.brave.com/search?q=%s", "https://search.brave.com/")
    val STARTPAGE = SearchEngine("startpage", "Startpage", "https://www.startpage.com/do/search?q=%s", "https://www.startpage.com/")
    val ECOSIA = SearchEngine("ecosia", "Ecosia", "https://www.ecosia.org/search?q=%s", "https://www.ecosia.org/")

    val DEFAULT = DUCKDUCKGO
    val builtIn: List<SearchEngine> = listOf(DUCKDUCKGO, GOOGLE, BING, BRAVE, STARTPAGE, ECOSIA)

    /** Resolves the configured engine; unknown ids and invalid custom templates fall back to the default. */
    fun resolve(id: String?, customTemplate: String?): SearchEngine {
        if (id == CUSTOM_ID) return custom(customTemplate) ?: DEFAULT
        return builtIn.firstOrNull { it.id == id } ?: DEFAULT
    }

    /** A user template must be HTTPS, carry a single `%s`, and parse as a URL once the placeholder is filled. */
    fun custom(template: String?): SearchEngine? {
        val value = template?.trim().orEmpty()
        if (value.length !in 12..2048 || value.count { it == '%' } < 1) return null
        if (value.indexOf("%s") < 0 || value.indexOf("%s") != value.lastIndexOf("%s")) return null
        if (!value.startsWith("https://", ignoreCase = true)) return null
        if (value.any { it.isWhitespace() || it.code < 0x20 }) return null
        val probe = runCatching { URI(value.replace("%s", "probe")) }.getOrNull() ?: return null
        if (probe.host.isNullOrBlank()) return null
        val homepage = "https://${probe.host}${if (probe.port > 0) ":${probe.port}" else ""}/"
        return SearchEngine(CUSTOM_ID, probe.host, value, homepage)
    }
}

/** A persisted browser tab. Private tabs are never written to storage. */
data class BrowserTab(
    val id: String,
    val url: String?,
    val title: String?,
    val position: Int,
    val isActive: Boolean,
    val createdAt: Long,
    val lastActiveAt: Long,
    val faviconPath: String? = null,
)

data class HistoryEntry(
    val id: Long,
    val url: String,
    val title: String?,
    val visitedAt: Long,
    val visitCount: Int,
    val faviconPath: String? = null,
)

data class Bookmark(
    val id: Long,
    val url: String,
    val title: String,
    val folder: String?,
    val position: Int,
    val createdAt: Long,
    val faviconPath: String? = null,
)

/** What the address bar offers while the user types. */
data class AddressSuggestion(val kind: Kind, val url: String, val title: String?) {
    enum class Kind { HISTORY, BOOKMARK, SEARCH, URL }
}
