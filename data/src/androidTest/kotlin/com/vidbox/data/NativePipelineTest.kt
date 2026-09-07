package com.vidbox.data

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vidbox.data.database.*
import com.vidbox.data.downloader.*
import com.vidbox.data.extractor.*
import com.vidbox.data.repository.RoomDownloadRepository
import com.vidbox.data.storage.*
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.usecase.FormatPlanner
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.UUID

/** Real binaries, real DASH extraction/transfers, real stream-copy merge, MediaStore and Room; no public-site dependency. */
@RunWith(AndroidJUnit4::class)
class NativePipelineTest {
    private lateinit var context: Context
    private lateinit var fixtures: File
    private lateinit var server: MockWebServer
    private lateinit var runner: NativeProcessRunner
    private lateinit var storage: AndroidMediaStorage
    private val published = mutableListOf<String>()
    private val events = Collections.synchronizedList(mutableListOf<String>())
    private val logger = object : EventLogger {
        override fun event(name: String, id: String?, fields: Map<String, String>) { events += "$name:$fields" }
    }
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    @Before fun setup() = runBlocking {
        context = ApplicationProvider.getApplicationContext()
        fixtures = File(context.cacheDir, "licensed-test-${UUID.randomUUID()}").apply { mkdirs() }
        runner = NativeProcessRunner(AndroidMediaRuntime(context), logger)
        storage = AndroidMediaStorage(context, object : SettingsRepository {
            override val settings = MutableStateFlow(AppSettings())
            override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
        })
        try { generateFixtures() } catch (error: Exception) {
            // Fixed version-only invocation: diagnostics contain library/bootstrap details, never a user URL.
            val env = AndroidMediaRuntime(context).environment(AndroidMediaRuntime.Tool.FFMPEG)
            val builder = ProcessBuilder(env.executable.path, "-version").redirectErrorStream(true)
            builder.environment().putAll(env.variables)
            val process = builder.start()
            val diagnostic = process.inputStream.bufferedReader().use { reader ->
                val chars = CharArray(3000)
                val count = reader.read(chars)
                if (count > 0) String(chars, 0, count) else "No version output"
            }
            process.destroy()
            throw AssertionError("FFmpeg bootstrap: $diagnostic", error)
        }
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val path = request.path.orEmpty().substringBefore('?').removePrefix("/")
                    val file = File(fixtures, path).canonicalFile
                    if (!file.path.startsWith(fixtures.canonicalPath + File.separator) || !file.isFile) return MockResponse().setResponseCode(404)
                    val mime = when (file.extension) { "mpd" -> "application/dash+xml"; "m3u8" -> "application/vnd.apple.mpegurl"; "mp4" -> "video/mp4"; "m4a" -> "audio/mp4"; else -> "application/octet-stream" }
                    val bytes = file.readBytes() // Small, generated instrumentation fixtures, never used by the production downloader.
                    val response = MockResponse().setHeader("Content-Type", mime).setHeader("Accept-Ranges", "bytes").setHeader("ETag", "\"fixture-v1\"")
                    if (request.method == "HEAD") return response.setHeader("Content-Length", bytes.size)
                    val start = request.getHeader("Range")?.substringAfter("bytes=")?.substringBefore('-')?.toIntOrNull()
                    if (start != null) {
                        if (start >= bytes.size) return response.setResponseCode(416).setHeader("Content-Range", "bytes */${bytes.size}")
                        return response.setResponseCode(206).setHeader("Content-Range", "bytes $start-${bytes.lastIndex}/${bytes.size}")
                            .setBody(Buffer().write(bytes, start, bytes.size - start))
                    }
                    return response.setBody(Buffer().write(bytes))
                }
            }
            start()
        }
    }
    @After fun cleanup() = runBlocking {
        published.forEach { runCatching { storage.delete(it) } }
        if (::server.isInitialized) server.shutdown()
        if (::fixtures.isInitialized) fixtures.deleteRecursively()
        Unit
    }
    private suspend fun generateFixtures() {
        val id = UUID.randomUUID().toString()
        runner.run(AndroidMediaRuntime.Tool.FFMPEG, listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "color=c=teal:s=160x90:r=10", "-t", "1", "-c:v", "mpeg4", "-an", File(fixtures, "video.mp4").path), id)
        runner.run(AndroidMediaRuntime.Tool.FFMPEG, listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100", "-t", "1", "-c:a", "aac", "-vn", File(fixtures, "audio.m4a").path), id)
        runner.run(AndroidMediaRuntime.Tool.FFMPEG, listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-i", File(fixtures, "video.mp4").path, "-i", File(fixtures, "audio.m4a").path,
            "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-f", "dash", File(fixtures, "manifest.mpd").path), id)
        runner.run(AndroidMediaRuntime.Tool.FFMPEG, listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y",
            "-i", File(fixtures, "video.mp4").path, "-i", File(fixtures, "audio.m4a").path,
            "-map", "0:v:0", "-map", "1:a:0", "-c", "copy", "-bsf:v", "dump_extra", "-hls_time", "1", "-hls_list_size", "0", "-f", "hls",
            File(fixtures, "playlist.m3u8").path), id)
    }

    @Test fun bundledYtDlpActuallyExecutes() = runBlocking {
        val version = runner.run(AndroidMediaRuntime.Tool.YT_DLP, listOf("--version"), UUID.randomUUID().toString(), captureJson = true).trim()
        assertEquals("The pinned engine must override the runtime AAR’s older zipapp", BuildConfig.MEDIA_ENGINE_VERSION, version)
    }

    @Test fun dashUrlToExtractionFormatsDownloadMergeMediaStoreAndRoom() = runBlocking {
        withTimeout(120_000) {
            val http = OkHttpClient.Builder().build()
            val extractor = CompositeVideoExtractor(http, runner, MetadataParser(json), logger)
            // This is a loopback fixture, intentionally below the app's HTTPS-only URL-validation boundary.
            val media = extractor.analyze(server.url("/manifest.mpd").toString())
            assertFalse(media.isDirect)
            assertTrue(media.formats.any { it.hasVideo })
            assertTrue(media.formats.any { !it.hasVideo })
            val selection = FormatPlanner().options(media).first { it.requiresMerging && it.container == "mp4" }
            val database = Room.inMemoryDatabaseBuilder(context, VidboxDatabase::class.java).build()
            try {
                val repository = RoomDownloadRepository(database, KeystoreSecretCipher(), TimeProvider(System::currentTimeMillis), json)
                val spec = DownloadSpec(media.url, "Vidbox licensed integration fixture", null, media.source, media.durationSeconds, selection, false, null)
                val id = repository.enqueue(spec)
                val downloader = HybridDownloader(HttpRangeDownloader(http, json), runner, FfmpegMediaProcessor(runner), WorkFiles(context), logger)
                val settings = object : SettingsRepository {
                    override val settings = MutableStateFlow(AppSettings())
                    override suspend fun update(transform: (AppSettings) -> AppSettings) { settings.value = transform(settings.value) }
                }
                val network = object : NetworkMonitor { override val status = MutableStateFlow(NetworkStatus(true, wifi = true, metered = false)) }
                val queue = DownloadQueue(repository, settings, network, downloader, storage, logger, TimeProvider(System::currentTimeMillis))
                queue.run { it.outputUri?.let(published::add) }
                val finished = repository.get(id)!!
                assertEquals("${finished.error?.message}\n$events", DownloadState.COMPLETED, finished.state)
                assertNotNull(finished.outputUri); assertNull(finished.pendingUri)
                assertTrue(storage.exists(finished.outputUri!!))
                assertTrue(finished.downloadedBytes > 0)
                assertTrue(events.any { it.startsWith("download.progress:") })
                assertTrue(events.any { it.startsWith("processing.completed:") })
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(context, Uri.parse(finished.outputUri))
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                }
                // The database stores only encrypted URL-bearing specifications.
                assertFalse(database.downloads().get(id)!!.specCiphertext.contains(media.url))
            } finally { database.close() }
        }
    }

    @Test fun muxedHlsIsRemuxedIntoItsAdvertisedMp4Container() = runBlocking {
        withTimeout(120_000) {
            val http = OkHttpClient()
            val media = CompositeVideoExtractor(http, runner, MetadataParser(json), logger)
                .analyze(server.url("/playlist.m3u8").toString())
            val selection = FormatPlanner().options(media).first { it.container == "mp4" && !it.requiresMerging }
            val id = UUID.randomUUID().toString()
            val files = WorkFiles(context)
            try {
                val spec = DownloadSpec(media.url, "Licensed HLS fixture", null, media.source, null, selection, false, null)
                val downloader = HybridDownloader(HttpRangeDownloader(http, json), runner, FfmpegMediaProcessor(runner), files, logger)
                val stages = mutableListOf<DownloadState>()
                val result = try {
                    downloader.download(DownloadRecord(id, spec, "fixture.mp4", state = DownloadState.EXTRACTING, createdAt = 0)) { stages += it.state }
                } catch (error: Exception) { throw AssertionError("HLS fixture events: $events", error) }
                assertTrue(stages.contains(DownloadState.PROCESSING))
                val header = File(result.path).inputStream().use { input -> ByteArray(12).also { input.read(it) } }
                assertEquals("ftyp", String(header, 4, 4, Charsets.US_ASCII))
                MediaMetadataRetriever().use { retriever ->
                    retriever.setDataSource(result.path)
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO))
                    assertEquals("yes", retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_VIDEO))
                }
            } finally { files.discard(id) }
        }
    }

    @Test fun nativeCancellationStopsTheProcessPromptly() = runBlocking {
        val task = launch(Dispatchers.IO) {
            runner.run(AndroidMediaRuntime.Tool.FFMPEG, listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                "-stream_loop", "-1", "-re", "-i", File(fixtures, "video.mp4").path, "-t", "60", "-c", "copy",
                File(fixtures, "cancelled.mp4").path), UUID.randomUUID().toString())
        }
        delay(1200)
        withTimeout(3000) { task.cancelAndJoin() }
        assertTrue(task.isCancelled)
    }
}
