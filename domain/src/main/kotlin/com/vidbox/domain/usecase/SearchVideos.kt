package com.vidbox.domain.usecase

import com.vidbox.domain.model.VideoSearchResult
import com.vidbox.domain.repository.VideoSearcher
import kotlinx.coroutines.withTimeout
import javax.inject.Inject

/** Free-text YouTube search backed by the bundled extraction engine (no telemetry, no API key). */
class SearchVideos @Inject constructor(private val searcher: VideoSearcher) {
    suspend operator fun invoke(query: String, limit: Int = DEFAULT_LIMIT): List<VideoSearchResult> =
        withTimeout(TIMEOUT_MS) { searcher.search(query.trim(), limit) }

    companion object {
        const val DEFAULT_LIMIT = 20
        const val MAX_QUERY = 256
        private const val TIMEOUT_MS = 60_000L

        /**
         * Whether typed input is an attempt at a link rather than a search phrase. Anything
         * with an authority separator or a URI-style scheme prefix (including bad ones like
         * `javascript:alert(1)`) is analyzed as a link so the user gets validation feedback
         * instead of nonsense search results. A space after the colon keeps prose a search:
         * "NASA: moon landing" searches, "javascript:alert(1)" validates and fails honestly.
         */
        fun looksLikeLink(raw: String): Boolean {
            val value = raw.trim()
            return value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) ||
                value.contains("://") ||
                value.startsWith("www.", ignoreCase = true) ||
                SCHEME_PREFIX.containsMatchIn(value)
        }

        /** RFC scheme syntax (`letter[letter|digit|+|-|.]*:`) with no space after the separator. */
        private val SCHEME_PREFIX = Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:\\S")
    }
}
