package com.vidbox.domain

import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.usecase.DownloadActions
import com.vidbox.domain.usecase.FormatPlanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class DownloadActionsTest {
    private class FakeScheduler : DownloadScheduler {
        var starts = 0
        override fun start() { starts++ }
    }
    private class FakeRepository : DownloadRepository {
        val specs = mutableMapOf<String, DownloadSpec>()
        override fun observeActive(): Flow<List<DownloadRecord>> = MutableStateFlow(emptyList())
        override fun observeRecent(limit: Int): Flow<List<DownloadRecord>> = MutableStateFlow(emptyList())
        override fun observeHistory(query: HistoryQuery): Flow<List<DownloadRecord>> = MutableStateFlow(emptyList())
        override suspend fun get(id: String): DownloadRecord? = null
        override suspend fun active(): List<DownloadRecord> = emptyList()
        override suspend fun enqueue(spec: DownloadSpec): String { specs["id"] = spec; return "id" }
        override suspend fun transition(id: String, expected: Set<DownloadState>, to: DownloadState,
            pauseReason: PauseReason?, error: DownloadError?): Boolean = true
        override suspend fun progress(id: String, progress: DownloadProgress) = Unit
        override suspend fun pending(id: String, uri: String?) = Unit
        override suspend fun complete(id: String, stored: StoredMedia): Boolean = true
        override suspend fun setMissing(id: String, missing: Boolean) = Unit
        override suspend fun cleaned(id: String) = Unit
        override suspend fun removeHistory(id: String) = Unit
        override suspend fun clearTerminalHistory() = Unit
    }
    private class FakeSettings : SettingsRepository {
        override val settings = MutableStateFlow(AppSettings())
        override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
    }
    private fun actions(repo: FakeRepository, scheduler: FakeScheduler = FakeScheduler()) =
        DownloadActions(repo, FakeSettings(), scheduler, FormatPlanner())

    @Test fun fileDownloadsStoreKindMimeAndExtension() = runBlocking {
        val repo = FakeRepository(); val scheduler = FakeScheduler()
        val id = actions(repo, scheduler).enqueueFile("https://example.com/manual.pdf",
            title = "manual.pdf", extension = "pdf", mimeType = "application/pdf", totalBytes = 4096)
        val spec = repo.specs.getValue(id)
        assertEquals(DownloadKind.FILE, spec.kind)
        assertEquals("application/pdf", spec.mimeType)
        assertEquals("pdf", spec.selection.container)
        assertEquals(4096L, spec.selection.primary.sizeBytes)
        assertTrue(spec.isDirect)
        assertEquals(1, scheduler.starts)
    }
    @Test fun directMediaDownloadsKeepVideoFlagAndExtension() = runBlocking {
        val repo = FakeRepository()
        actions(repo).enqueueDirect("https://example.com/trailer.mp4?sig=1", fileName = "trailer.mp4", mimeType = "video/mp4", totalBytes = 99)
        val spec = repo.specs.getValue("id")
        assertEquals(DownloadKind.MEDIA, spec.kind)
        assertTrue(spec.isDirect)
        assertTrue(spec.selection.primary.hasVideo)
        assertEquals("mp4", spec.selection.primary.extension)
        assertTrue(spec.selection.primary.hasAudio == null)
    }
    @Test fun directAudioUrlsAreNotMarkedAsVideo() = runBlocking {
        val repo = FakeRepository()
        actions(repo).enqueueDirect("https://example.com/audio.m4a", fileName = "audio.m4a", mimeType = "audio/mp4")
        val spec = repo.specs.getValue("id")
        assertFalse(spec.selection.primary.hasVideo)
        assertEquals(true, spec.selection.primary.hasAudio)
    }
    @Test fun insecureUrlsAreRejected() {
        assertThrows(DownloadException::class.java) {
            runBlocking { actions(FakeRepository()).enqueueFile("http://example.com/a.pdf", extension = "pdf") }
        }
    }
    @Test fun unsupportedMediaExtensionIsRejected() {
        assertThrows(DownloadException::class.java) {
            runBlocking { actions(FakeRepository()).enqueueDirect("https://example.com/a", fileName = "a.pdf") }
        }
    }
    @Test fun fileTitleIsSanitised() = runBlocking {
        val repo = FakeRepository()
        actions(repo).enqueueFile("https://example.com/docs/../report.pdf", title = "My report.pdf", extension = "pdf")
        assertEquals("My report", repo.specs.getValue("id").title)
        assertEquals("example.com", repo.specs.getValue("id").source)
    }
}
