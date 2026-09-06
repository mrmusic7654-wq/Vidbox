package com.vidbox.data

import com.vidbox.data.database.VidboxDatabase
import com.vidbox.data.repository.RoomDownloadRepository
import com.vidbox.domain.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class RoomDownloadRepositoryTest {
    private lateinit var db: VidboxDatabase
    private lateinit var repository: RoomDownloadRepository
    @Before fun setup() { db = testDatabase(); repository = testRepository(db) }
    @After fun teardown() { db.close() }

    @Test fun enqueuesDurablyWithoutPlaintextSourceUrls() = runBlocking {
        val url = "https://example.com/video.mp4?token=private"
        val id = repository.enqueue(testSpec(url))
        assertEquals(url, repository.get(id)!!.spec.url)
        assertFalse(db.downloads().get(id)!!.specCiphertext.contains("token=private"))
        assertEquals(DownloadState.QUEUED, repository.observeActive().first().single().state)
    }
    @Test fun compareAndSetRejectsStaleStateAndLateProgress() = runBlocking {
        val id = repository.enqueue(testSpec())
        assertTrue(repository.transition(id, setOf(DownloadState.QUEUED), DownloadState.EXTRACTING))
        assertFalse(repository.transition(id, setOf(DownloadState.QUEUED), DownloadState.EXTRACTING))
        repository.progress(id, DownloadProgress(DownloadState.DOWNLOADING, 100, 500, resumeSupported = true))
        assertTrue(repository.transition(id, setOf(DownloadState.DOWNLOADING), DownloadState.PAUSED, PauseReason.USER))
        repository.progress(id, DownloadProgress(DownloadState.DOWNLOADING, 400, 500))
        assertEquals(DownloadState.PAUSED, repository.get(id)!!.state)
        assertEquals(100L, repository.get(id)!!.downloadedBytes)
    }
    @Test fun cancellationCannotBeOverwrittenByCompletion() = runBlocking {
        val id = repository.enqueue(testSpec())
        repository.transition(id, setOf(DownloadState.QUEUED), DownloadState.EXTRACTING)
        repository.progress(id, DownloadProgress(DownloadState.PROCESSING, 100, 100))
        repository.transition(id, setOf(DownloadState.PROCESSING), DownloadState.CANCELLED)
        assertFalse(repository.complete(id, StoredMedia("content://media/test", "x.mp4", "video/mp4", 100)))
        assertTrue(repository.get(id)!!.needsCleanup)
    }
    @Test fun completionPersistsMetadataAndSearchEscapesWildcards() = runBlocking {
        val id = repository.enqueue(testSpec(title = "100% original"))
        repository.transition(id, setOf(DownloadState.QUEUED), DownloadState.EXTRACTING)
        repository.progress(id, DownloadProgress(DownloadState.PROCESSING, 400, 400))
        repository.pending(id, "content://media/pending")
        assertTrue(repository.complete(id, StoredMedia("content://media/video/1", "100% original.mp4", "video/mp4", 400)))
        repository.cleaned(id)
        val results = repository.observeHistory(HistoryQuery(text = "100%", filter = HistoryFilter.COMPLETED)).first()
        assertEquals(id, results.single().id)
        assertNull(results.single().pendingUri)
        assertEquals(100f, results.single().percent)
        assertTrue(repository.active().isEmpty())
    }
    @Test fun clearingTerminalHistoryKeepsActiveQueue() = runBlocking {
        val active = repository.enqueue(testSpec(title = "Active"))
        val failed = repository.enqueue(testSpec(title = "Failed"))
        repository.transition(failed, setOf(DownloadState.QUEUED), DownloadState.FAILED, error = Errors.of(ErrorCode.NETWORK))
        repository.clearTerminalHistory()
        assertNull(repository.get(failed)); assertNotNull(repository.get(active))
    }
}
