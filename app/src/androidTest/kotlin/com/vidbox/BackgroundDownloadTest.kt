package com.vidbox

import android.Manifest
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ApplicationProvider
import androidx.test.rule.GrantPermissionRule
import com.vidbox.data.extractor.AndroidMediaRuntime
import com.vidbox.data.extractor.NativeProcessRunner
import com.vidbox.data.storage.WorkFiles
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.usecase.DownloadActions
import com.vidbox.service.DownloadNotifications
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import okio.Buffer
import org.junit.*
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/** Real foreground service, real HTTP bytes, real notification PendingIntents, and real MediaStore. */
@HiltAndroidTest
class BackgroundDownloadTest {
    @get:Rule(order = 0) val hilt = HiltAndroidRule(this)
    @get:Rule(order = 1) val permission: GrantPermissionRule = GrantPermissionRule.grant(
        *if (Build.VERSION.SDK_INT >= 33) arrayOf(Manifest.permission.POST_NOTIFICATIONS) else emptyArray())
    @get:Rule(order = 2) val compose = createAndroidComposeRule<MainActivity>()
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var actions: DownloadActions
    @Inject lateinit var runner: NativeProcessRunner
    @Inject lateinit var storage: MediaStorage
    private lateinit var server: MockWebServer
    private lateinit var fixture: File
    private var downloadId: String? = null

    @Before fun setup() {
        hilt.inject()
        runBlocking { settings.update { AppSettings() } }
    }
    @After fun cleanup() = runBlocking {
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        downloadId?.let { id ->
            val record = repository.get(id)
            if (record?.state?.isTerminal == false) actions.cancel(id)
            record?.outputUri?.let { storage.delete(it) }
            if (record?.state?.isTerminal == true) repository.removeHistory(id)
        }
        if (::server.isInitialized) server.shutdown()
        if (::fixture.isInitialized) fixture.delete()
        Unit
    }

    @Test fun transferSurvivesBackgroundAndPauseResumeNotificationActions() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = context.getSystemService(NotificationManager::class.java)
        fixture = File(context.cacheDir, "service-fixture.mp4")
        runBlocking {
            withTimeout(60_000) {
                runner.run(AndroidMediaRuntime.Tool.FFMPEG, listOf("-nostdin", "-hide_banner", "-loglevel", "error", "-y",
                    "-f", "lavfi", "-i", "color=c=teal:s=160x90:r=10", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
                    "-t", "8", "-c:v", "mpeg4", "-c:a", "aac", fixture.path), UUID.randomUUID().toString())
            }
        }
        val bytes = fixture.readBytes()
        val ranges = java.util.Collections.synchronizedList(mutableListOf<String>())
        server = MockWebServer().apply {
            dispatcher = object : Dispatcher() {
                override fun dispatch(request: RecordedRequest): MockResponse {
                    val response = MockResponse().setHeader("Content-Type", "video/mp4").setHeader("ETag", "\"licensed-v1\"")
                        .setHeader("Accept-Ranges", "bytes")
                    val range = request.getHeader("Range")
                    if (range != null) {
                        ranges += range
                        val offset = range.substringAfter("bytes=").substringBefore('-').toInt()
                        return response.setResponseCode(206).setHeader("Content-Range", "bytes $offset-${bytes.lastIndex}/${bytes.size}")
                            .setBody(Buffer().write(bytes, offset, bytes.size - offset)).throttleBody(2048, 100, TimeUnit.MILLISECONDS)
                    }
                    return response.setBody(Buffer().write(bytes)).throttleBody(2048, 100, TimeUnit.MILLISECONDS)
                }
            }
            start()
        }
        val id = runBlocking {
            // The HTTP loopback fixture is inserted below the user-facing HTTPS validator; release cleartext remains disabled.
            repository.enqueue(DownloadSpec(server.url("/authorized.mp4").toString(), "Authorized background fixture", null, "localhost", 8.0,
                FormatSelection(MediaFormat("direct", "mp4", hasVideo = true, hasAudio = true, sizeBytes = bytes.size.toLong())), true, null))
        }
        downloadId = id
        compose.runOnUiThread { actions.wake() }
        runBlocking { withTimeout(20_000) { repository.observeActive().first { rows -> rows.any { it.id == id && it.downloadedBytes > 0 && it.canPause } } } }
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        compose.waitUntil(8000) { manager.activeNotifications.any { it.id == DownloadNotifications.FOREGROUND_ID } }
        compose.waitUntil(8000) { manager.activeNotifications.any { it.notification.actions?.any { action -> action.title == "Pause" } == true } }
        manager.activeNotifications.first { it.notification.actions?.any { action -> action.title == "Pause" } == true }
            .notification.actions.first { it.title == "Pause" }.actionIntent.send()
        runBlocking { withTimeout(10_000) { repository.observeActive().first { rows -> rows.any { it.id == id && it.pauseReason == PauseReason.USER } } } }
        compose.waitUntil(8000) { manager.activeNotifications.any { it.notification.actions?.any { action -> action.title == "Resume" } == true } }
        val part = File(WorkFiles(context).directory(id), "original.mp4.part")
        val offset = part.length()
        assertTrue("A real partial file must survive pause", offset > 0)
        // Bring the app foreground before programmatically sending the PendingIntent; real notification taps have the OS user-action exemption.
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        manager.activeNotifications.first { it.notification.actions?.any { action -> action.title == "Resume" } == true }
            .notification.actions.first { it.title == "Resume" }.actionIntent.send()
        compose.activityRule.scenario.moveToState(Lifecycle.State.CREATED)
        val completed = runBlocking {
            withTimeout(30_000) {
                repository.observeRecent(20).first { rows -> rows.any { it.id == id && it.state.isTerminal } }.first { it.id == id }
            }
        }
        assertEquals(completed.error?.message, DownloadState.COMPLETED, completed.state)
        assertTrue(ranges.contains("bytes=$offset-"))
        assertEquals(bytes.size.toLong(), completed.downloadedBytes)
        runBlocking { assertTrue(storage.exists(completed.outputUri!!)) }
        val saved = context.contentResolver.openInputStream(android.net.Uri.parse(completed.outputUri))!!.use { it.readBytes() }
        assertArrayEquals(bytes, saved)
    }
}
