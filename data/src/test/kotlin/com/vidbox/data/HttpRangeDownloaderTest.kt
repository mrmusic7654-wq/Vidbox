package com.vidbox.data

import com.vidbox.data.downloader.HttpRangeDownloader
import com.vidbox.domain.model.*
import kotlinx.coroutines.*
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.rules.TemporaryFolder
import java.io.File
import java.util.concurrent.TimeUnit

class HttpRangeDownloaderTest {
    @get:Rule val folder = TemporaryFolder()
    private lateinit var server: MockWebServer
    private lateinit var downloader: HttpRangeDownloader
    @Before fun setup() {
        server = MockWebServer().apply { start() }
        downloader = HttpRangeDownloader(OkHttpClient.Builder().readTimeout(3, TimeUnit.SECONDS).build(), Json { ignoreUnknownKeys = true })
    }
    @After fun teardown() { server.shutdown() }
    private fun target() = File(folder.root, "media.mp4")
    private fun seedPartial(target: File, body: String, etag: String = "v1", total: Int = 10) {
        File(target.path + ".part").writeText(body)
        File(target.path + ".resume.json").writeText("""{"etag":"\"$etag\"","total":$total}""")
    }
    @Test fun streamsToTemporaryFileThenPromotes() = runBlocking {
        val bytes = ByteArray(512 * 1024) { (it % 251).toByte() }
        server.enqueue(MockResponse().setBody(Buffer().write(bytes)).setHeader("Accept-Ranges", "bytes").setHeader("ETag", "\"v1\""))
        val progress = mutableListOf<DownloadProgress>()
        val file = downloader.transfer(server.url("/media").toString(), target()) { progress += it }
        assertArrayEquals(bytes, file.readBytes())
        assertFalse(File(file.path + ".part").exists())
        assertEquals(bytes.size.toLong(), progress.last().downloadedBytes)
        assertTrue(progress.last().resumeSupported)
    }
    @Test fun resumesWithRangeAndIfRange() = runBlocking {
        val file = target(); seedPartial(file, "0123")
        server.enqueue(MockResponse().setResponseCode(206).setBody("456789")
            .setHeader("Content-Range", "bytes 4-9/10").setHeader("ETag", "\"v1\""))
        downloader.transfer(server.url("/media").toString(), file) {}
        val request = server.takeRequest()
        assertEquals("bytes=4-", request.getHeader("Range"))
        assertEquals("\"v1\"", request.getHeader("If-Range"))
        assertEquals("0123456789", file.readText())
    }
    @Test fun ignoredRangeRestartsInsteadOfAppending() = runBlocking {
        val file = target(); seedPartial(file, "old!")
        server.enqueue(MockResponse().setBody("NEW CONTENT").setHeader("ETag", "\"v2\""))
        downloader.transfer(server.url("/media").toString(), file) {}
        assertEquals("NEW CONTENT", file.readText())
    }
    @Test fun invalidContentRangeNeverCorruptsOutput() = runBlocking {
        val file = target(); seedPartial(file, "0123")
        server.enqueue(MockResponse().setResponseCode(206).setBody("broken").setHeader("Content-Range", "bytes 3-8/10").setHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setBody("0123456789").setHeader("ETag", "\"v1\""))
        downloader.transfer(server.url("/media").toString(), file) {}
        assertEquals("0123456789", file.readText())
        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Range"))
    }
    @Test fun unknownLengthPartialResponseCannotBeMistakenForACompleteFile() = runBlocking {
        val file = target(); seedPartial(file, "0123")
        server.enqueue(MockResponse().setResponseCode(206).setBody("456789")
            .setHeader("Content-Range", "bytes 4-9/*").setHeader("ETag", "\"v1\""))
        server.enqueue(MockResponse().setBody("0123456789").setHeader("ETag", "\"v1\""))
        downloader.transfer(server.url("/media").toString(), file) {}
        assertEquals("0123456789", file.readText())
        server.takeRequest()
        assertNull(server.takeRequest().getHeader("Range"))
    }
    @Test fun handlesAlreadyComplete416OnlyWithMatchingValidator() = runBlocking {
        val file = target(); seedPartial(file, "0123456789")
        server.enqueue(MockResponse().setResponseCode(416).setHeader("Content-Range", "bytes */10").setHeader("ETag", "\"v1\""))
        downloader.transfer(server.url("/media").toString(), file) {}
        assertEquals("0123456789", file.readText())
    }
    @Test fun unvalidatedPartialsRestartSafely() = runBlocking {
        val file = target(); File(file.path + ".part").writeText("stale")
        server.enqueue(MockResponse().setBody("fresh"))
        downloader.transfer(server.url("/media").toString(), file) {}
        assertNull(server.takeRequest().getHeader("Range"))
        assertEquals("fresh", file.readText())
    }
    @Test fun cancellationClosesCallAndKeepsOnlyPartialFile() = runBlocking {
        val file = target()
        server.enqueue(MockResponse().setBody(Buffer().write(ByteArray(128 * 1024))).setHeader("ETag", "\"v1\"")
            .setHeader("Accept-Ranges", "bytes").throttleBody(1024, 50, TimeUnit.MILLISECONDS))
        val began = CompletableDeferred<Unit>()
        val job = launch(Dispatchers.IO) { downloader.transfer(server.url("/media").toString(), file) { began.complete(Unit) } }
        withTimeout(5000) { began.await() }
        delay(120)
        withTimeout(2000) { job.cancelAndJoin() }
        assertFalse(file.exists())
        assertTrue(File(file.path + ".part").exists())
    }
}
