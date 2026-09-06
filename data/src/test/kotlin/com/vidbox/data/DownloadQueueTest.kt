package com.vidbox.data

import com.vidbox.data.database.VidboxDatabase
import com.vidbox.data.downloader.DownloadQueue
import com.vidbox.data.repository.RoomDownloadRepository
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.concurrent.atomic.AtomicInteger

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DownloadQueueTest {
    private lateinit var db: VidboxDatabase
    private lateinit var repo: RoomDownloadRepository
    private val network = TestNetwork()
    private val prefs = TestSettings()
    private val storage = object : MediaStorage {
        override suspend fun publish(id: String, media: StagedMedia, fileName: String, destinationTree: String?, onPending: suspend (String?) -> Unit): StoredMedia {
            onPending("content://test/$id")
            return StoredMedia("content://test/$id", fileName, "video/mp4", media.sizeBytes)
        }
        override suspend fun exists(uri: String) = true
        override suspend fun delete(uri: String) = Unit
    }
    @Before fun setup() { db = testDatabase(); repo = testRepository(db) }
    @After fun teardown() { db.close() }
    private fun queue(engine: Downloader) = DownloadQueue(repo, prefs, network, engine, storage, TestLogger(), TimeProvider(System::currentTimeMillis))

    @Test fun enforcesConcurrencyAndCommitsAllCompletions() = runBlocking {
        repeat(4) { repo.enqueue(testSpec(title = "Video $it")) }
        val gate = CompletableDeferred<Unit>()
        val count = AtomicInteger(0)
        val peak = AtomicInteger(0)
        val started = MutableStateFlow(0)
        val engine = object : Downloader {
            override suspend fun download(record: DownloadRecord, onProgress: suspend (DownloadProgress) -> Unit): StagedMedia {
                val active = count.incrementAndGet(); peak.updateAndGet { maxOf(it, active) }
                started.update { it + 1 }
                try {
                    onProgress(DownloadProgress(DownloadState.DOWNLOADING, 2, 8, resumeSupported = true))
                    gate.await()
                    return StagedMedia("/test/${record.id}", "video/mp4", 8)
                } finally { count.decrementAndGet() }
            }
            override suspend fun discard(id: String) = Unit
        }
        val job = launch(Dispatchers.Default) { queue(engine).run() }
        withTimeout(8000) { started.first { it >= 2 } }
        assertEquals(2, peak.get())
        assertEquals(2, repo.active().count { it.state == DownloadState.QUEUED })
        gate.complete(Unit)
        withTimeout(8000) { job.join() }
        assertEquals(4, repo.observeHistory(HistoryQuery()).first().size)
        assertTrue(repo.observeHistory(HistoryQuery()).first().all { it.state == DownloadState.COMPLETED })
        assertEquals(2, peak.get())
    }

    @Test fun networkLossPreservesTaskAndAutomaticallyResumes() = runBlocking {
        val id = repo.enqueue(testSpec())
        val calls = MutableStateFlow(0)
        val engine = object : Downloader {
            override suspend fun download(record: DownloadRecord, onProgress: suspend (DownloadProgress) -> Unit): StagedMedia {
                calls.update { it + 1 }
                onProgress(DownloadProgress(DownloadState.DOWNLOADING, 4, 8, resumeSupported = true))
                if (calls.value == 1) awaitCancellation()
                return StagedMedia("/test/$id", "video/mp4", 8)
            }
            override suspend fun discard(id: String) = Unit
        }
        val owner = queue(engine)
        val job = launch(Dispatchers.Default) { owner.run() }
        withTimeout(8000) { calls.first { it == 1 } }
        network.status.value = NetworkStatus()
        withTimeout(8000) { repo.observeActive().first { it.singleOrNull()?.pauseReason == PauseReason.NETWORK } }
        assertTrue(owner.isRunning)
        assertEquals(4L, repo.get(id)!!.downloadedBytes)
        network.status.value = NetworkStatus(connected = true, wifi = true, metered = false)
        withTimeout(8000) { job.join() }
        assertEquals(2, calls.value)
        assertEquals(DownloadState.COMPLETED, repo.get(id)!!.state)
    }

    @Test fun cancelWaitsForWriterThenCleansTemporaryFiles() = runBlocking {
        val id = repo.enqueue(testSpec())
        val started = CompletableDeferred<Unit>()
        var writerClosed = false
        var cleaned = false
        val engine = object : Downloader {
            override suspend fun download(record: DownloadRecord, onProgress: suspend (DownloadProgress) -> Unit): StagedMedia {
                try {
                    onProgress(DownloadProgress(DownloadState.DOWNLOADING, 4, 8, resumeSupported = true))
                    started.complete(Unit)
                    awaitCancellation()
                } finally { writerClosed = true }
            }
            override suspend fun discard(id: String) { assertTrue(writerClosed); cleaned = true }
        }
        val job = launch(Dispatchers.Default) { queue(engine).run() }
        withTimeout(8000) { started.await() }
        repo.transition(id, setOf(DownloadState.DOWNLOADING), DownloadState.CANCELLED)
        withTimeout(8000) { job.join() }
        assertTrue(cleaned)
        assertEquals(DownloadState.CANCELLED, repo.get(id)!!.state)
        assertFalse(repo.get(id)!!.needsCleanup)
    }
}
