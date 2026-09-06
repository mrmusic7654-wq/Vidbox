package com.vidbox.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vidbox.data.extractor.AndroidMediaRuntime
import com.vidbox.data.extractor.CompositeVideoExtractor
import com.vidbox.data.extractor.MetadataParser
import com.vidbox.data.extractor.NativeProcessRunner
import com.vidbox.domain.model.*
import com.vidbox.domain.usecase.AnalyzeVideo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.tls.HandshakeCertificates
import okhttp3.tls.HeldCertificate
import okio.Buffer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * HTML pages that embed a direct media file (Open Graph video, <video>/<source>) must resolve
 * through the fast page scan without ever initializing the bundled engine.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class PageScanTest {
    @Test fun pageWithOpenGraphVideoResolvesDirectlyWithoutTheEngine() = runBlocking {
        val certificate = HeldCertificate.Builder().addSubjectAlternativeName("localhost").build()
        val serverTls = HandshakeCertificates.Builder().heldCertificate(certificate).build()
        val clientTls = HandshakeCertificates.Builder().addTrustedCertificate(certificate.certificate).build()
        val bytes = ByteArray(48 * 1024) { (it % 131).toByte() }
        val server = MockWebServer()
        lateinit var mediaUrl: String
        val dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val path = request.path.orEmpty().substringBefore('?')
                return when {
                    path == "/page.html" -> MockResponse().setHeader("Content-Type", "text/html; charset=utf-8").apply {
                        if (request.method == "GET") {
                            setBody("<html><head><meta property=\"og:title\" content=\"Demo\"/>" +
                                "<meta property=\"og:video\" content=\"$mediaUrl?token=abc&amp;exp=2\"/>" +
                                "<meta property=\"og:video:width\" content=\"640\"/></head><body>" +
                                "<video controls><source src=\"/missing.webm\" type=\"video/webm\"/></video></body></html>")
                        }
                    }
                    path == "/clip.mp4" -> MockResponse().setHeader("Content-Type", "video/mp4")
                        .setHeader("Content-Disposition", "attachment; filename=\"clip-final.mp4\"")
                        .setHeader("Content-Length", bytes.size).setHeader("ETag", "\"v1\"")
                        .setHeader("Accept-Ranges", "bytes").apply {
                            if (request.method != "HEAD") setBody(Buffer().write(bytes))
                        }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        try {
            server.useHttps(serverTls.sslSocketFactory(), false)
            server.dispatcher = dispatcher
            server.start()
            mediaUrl = "https://localhost:${server.port}/clip.mp4"
            val client = OkHttpClient.Builder().sslSocketFactory(clientTls.sslSocketFactory(), clientTls.trustManager).build()
            val json = Json { ignoreUnknownKeys = true }
            val context = ApplicationProvider.getApplicationContext<Context>()
            val runtime = AndroidMediaRuntime(context)
            val runner = NativeProcessRunner(runtime, TestLogger()) // Never initialized on the fast route.
            val media = AnalyzeVideo(CompositeVideoExtractor(client, runner, MetadataParser(json), TestLogger()))(
                "https://localhost:${server.port}/page.html")
            assertTrue("Page-embedded media must be marked direct", media.isDirect)
            assertEquals("clip-final", media.title)
            assertEquals(mediaUrl + "?token=abc&exp=2", media.url)
            assertEquals(1, media.formats.size)
            val format = media.formats.single()
            assertEquals("direct", format.id)
            assertEquals("mp4", format.extension)
            assertTrue(format.hasVideo)
            assertEquals(bytes.size.toLong(), format.sizeBytes)
        } finally { server.shutdown() }
    }
}
