package com.vidbox.data.extractor

import com.vidbox.domain.model.VideoSearchResult
import com.vidbox.domain.repository.EventLogger
import com.vidbox.domain.repository.VideoSearcher
import com.vidbox.domain.usecase.SearchVideos
import com.vidbox.domain.util.VideoSearchParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * YouTube search through the same bundled, checksum-pinned yt-dlp engine used for
 * analysis: `--flat-playlist` keeps it cheap (metadata only, no stream resolution),
 * results stay on-device, and the query is passed as a separate argv entry.
 */
@Singleton
class YtDlpVideoSearcher @Inject constructor(
    private val runner: NativeProcessRunner,
    private val parser: VideoSearchParser,
    private val logger: EventLogger,
) : VideoSearcher {
    override suspend fun search(query: String, limit: Int): List<VideoSearchResult> = withContext(Dispatchers.IO) {
        val id = UUID.randomUUID().toString()
        val boundedQuery = query.take(SearchVideos.MAX_QUERY)
        val boundedLimit = limit.coerceIn(1, MAX_RESULTS)
        logger.event("search.started", id)
        val output = try {
            runner.run(
                AndroidMediaRuntime.Tool.YT_DLP,
                listOf(
                    "--flat-playlist", "--dump-single-json", "--no-warnings",
                    "--extractor-retries", "1", "--",
                    "ytsearch$boundedLimit:$boundedQuery",
                ),
                id, captureJson = true,
            )
        } catch (cancel: CancellationException) { throw cancel }
        catch (error: Exception) {
            logger.event("search.failed", id)
            throw error
        }
        val results = parser.parse(output)
        logger.event("search.completed", id, mapOf("results" to results.size.toString()))
        results
    }

    private companion object { const val MAX_RESULTS = 30 }
}
