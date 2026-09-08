package com.vidbox.data.di

import com.vidbox.data.downloader.*
import com.vidbox.data.extractor.CompositeVideoExtractor
import com.vidbox.data.extractor.YtDlpVideoSearcher
import com.vidbox.data.storage.AndroidMediaStorage
import com.vidbox.domain.repository.*
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ExtractorModule {
    @Binds @Singleton abstract fun extractor(impl: CompositeVideoExtractor): VideoExtractor
    @Binds @Singleton abstract fun searcher(impl: YtDlpVideoSearcher): VideoSearcher
}

@Module
@InstallIn(SingletonComponent::class)
abstract class TransferModule {
    @Binds @Singleton abstract fun downloader(impl: HybridDownloader): Downloader
    @Binds @Singleton abstract fun processor(impl: FfmpegMediaProcessor): MediaProcessor
    @Binds @Singleton abstract fun storage(impl: AndroidMediaStorage): MediaStorage
}
