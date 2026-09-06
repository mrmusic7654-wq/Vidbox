package com.vidbox.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vidbox.data.downloader.*
import com.vidbox.data.extractor.*
import com.vidbox.data.storage.WorkFiles
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.usecase.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class DirectPipelineIntegrationTest {
    @Test fun httpsUrlToRealProbeStreamingStorageAndDatabase() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val bytes = ByteArray(96 * 1024) { (it % 127).toByte() }
        val server = MockWebServer().apply {
            useHttps(serverTls.sslSocketFactory(), false)
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest) = MockResponse().setHeader("Content-Type", "video/mp4")
                    .setHeader("Content-Length", bytes.size).setHeader("ETag", "\"v1\"").setHeader("Accept-Ranges", "bytes")
                    .apply { if (request.method != "HEAD") setBody(Buffer().write(bytes)) }
            }
            start()
        }
        val db = testDatabase()
        val context = ApplicationProvider.getApplicationContext<Context>()
        val published = File(context.cacheDir, "published-integration.mp4")
        try {
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            val json = Json { ignoreUnknownKeys = true }
            val runtime = AndroidMediaRuntime(context)
            val runner = NativeProcessRunner(runtime, TestLogger()) // Never initialized for the direct-file route.
            val media = AnalyzeVideo(CompositeVideoExtractor(client, runner, MetadataParser(json), TestLogger()))(server.url("/film.mp4").toString())
            assertTrue(media.isDirect)
            val selection = FormatPlanner().options(media).single()
            val repo = testRepository(db)
            val id = repo.enqueue(DownloadSpec(media.url, media.title, null, media.source, null, selection, true, null))
            val storage = object : MediaStorage {
                override suspend fun publish(id: String, media: StagedMedia, fileName: String, destinationTree: String?, onPending: suspend (String?) -> Unit): StoredMedia {
                    onPending("content://test/$id")
                    File(media.path).inputStream().use { input -> published.outputStream().use { input.copyTo(it, 65536) } }
                    return StoredMedia("content://test/$id", fileName, media.mimeType, published.length())
                }
                override suspend fun exists(uri: String) = published.exists()
                override suspend fun delete(uri: String) { published.delete() }
            }
            val downloader = HybridDownloader(HttpRangeDownloader(client, json), runner, FfmpegMediaProcessor(runner), WorkFiles(context), TestLogger())
            withTimeout(10000) { DownloadQueue(repo, TestSettings(), TestNetwork(), downloader, storage, TestLogger(), TimeProvider(System::currentTimeMillis)).run() }
            assertEquals(DownloadState.COMPLETED, repo.get(id)!!.state)
            assertArrayEquals(bytes, published.readBytes())
            assertEquals(id, repo.observeHistory(HistoryQuery()).first().single().id)
            assertEquals(bytes.size.toLong(), repo.get(id)!!.downloadedBytes)
        } finally { server.shutdown(); db.close(); published.delete() }
    }
}
