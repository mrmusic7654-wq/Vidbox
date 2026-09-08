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
         * with a scheme separator (including bad ones like `javascript:`) is analyzed as a
         * link so the user gets validation feedback instead of nonsense search results.
         */
        fun looksLikeLink(raw: String): Boolean {
            val value = raw.trim()
            return value.startsWith("http://", ignoreCase = true) ||
                value.startsWith("https://", ignoreCase = true) ||
                value.contains("://") ||
                value.startsWith("www.", ignoreCase = true)
        }
    }
}
