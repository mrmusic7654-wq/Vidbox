package com.vidbox

import com.vidbox.data.di.ExtractorModule
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.VideoExtractor
import com.vidbox.domain.repository.VideoSearcher
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(components = [SingletonComponent::class], replaces = [ExtractorModule::class])
object TestExtractorModule {
    @Provides @Singleton fun extractor(): VideoExtractor = object : VideoExtractor {
        override suspend fun analyze(url: String) = MediaInfo(url, "Open-license test film", durationSeconds = 12.0,
            uploader = "Test studio", source = "example.com", extractor = "test", formats = listOf(
                MediaFormat("137", "mp4", height = 1080, fps = 30.0, videoCodec = "avc1", hasVideo = true, hasAudio = false, sizeBytes = 2048),
                MediaFormat("18", "mp4", height = 720, videoCodec = "avc1", audioCodec = "mp4a", hasVideo = true, hasAudio = true, sizeBytes = 1024),
                MediaFormat("140", "m4a", audioCodec = "mp4a", hasVideo = false, hasAudio = true, sizeBytes = 256),
            ))
    }

    @Provides @Singleton fun searcher(): VideoSearcher = object : VideoSearcher {
        override suspend fun search(query: String, limit: Int) = (1..limit.coerceAtMost(3)).map { index ->
            VideoSearchResult(id = "test$index", title = "$query sample $index",
                url = "https://example.com/watch?v=test$index", thumbnailUrl = null,
                channel = "Test studio", durationSeconds = 60.0 * index, viewCount = 1000L * index)
        }
    }
}
