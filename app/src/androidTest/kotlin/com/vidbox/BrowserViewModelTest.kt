package com.vidbox

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.vidbox.data.database.SecretCipher
import com.vidbox.data.database.VidboxDatabase
import com.vidbox.data.repository.RoomDownloadRepository
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.DownloadScheduler
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.repository.TimeProvider
import com.vidbox.domain.usecase.DownloadActions
import com.vidbox.domain.usecase.FormatPlanner
import com.vidbox.presentation.browser.BrowserEvent
import com.vidbox.presentation.browser.BrowserViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Base64

/** Browser address rules and the browser→DownloadManager pipeline on a real Room database. */
@RunWith(AndroidJUnit4::class)
class BrowserViewModelTest {
    private lateinit var db: VidboxDatabase
    private lateinit var repository: RoomDownloadRepository
    private lateinit var viewModel: BrowserViewModel
    private lateinit var settingsStore: MutableStateFlow<AppSettings>

    private class TestCipher : SecretCipher {
        override fun encrypt(plain: String) = Base64.getEncoder().encodeToString(plain.toByteArray())
        override fun decrypt(encrypted: String) = String(Base64.getDecoder().decode(encrypted))
    }

    @Before fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, VidboxDatabase::class.java).allowMainThreadQueries().build()
        repository = RoomDownloadRepository(db, TestCipher(), TimeProvider(System::currentTimeMillis), Json { encodeDefaults = true })
        settingsStore = MutableStateFlow(AppSettings())
        val settings = object : SettingsRepository {
            override val settings = settingsStore
            override suspend fun update(transform: (AppSettings) -> AppSettings) { settingsStore.value = transform(settingsStore.value) }
        }
        val actions = DownloadActions(repository, settings, object : DownloadScheduler {
            override fun start() { /* Queue stays idle: rows remain QUEUED for assertions. */ }
        }, FormatPlanner())
        viewModel = BrowserViewModel(actions, settings)
    }

    @After fun teardown() { db.close() }

    private suspend fun nextEvent(): BrowserEvent = withTimeout(5000) { viewModel.events.first() }

    @Test fun typedHostsBecomeHttpsPagesAndProseBecomesSearch() = runBlocking {
        viewModel.open("example.com/watch")
        val page = nextEvent()
        assertEquals("https://example.com/watch", (page as BrowserEvent.Navigate).url)
        viewModel.open("rocket launch video")
        val search = nextEvent()
        assertTrue((search as BrowserEvent.Navigate).url.startsWith("https://duckduckgo.com/?q="))
    }

    @Test fun pageEventsTrackSecurityTitleAndSessionHistory() = runBlocking {
        viewModel.pageStarted("https://example.com/film")
        viewModel.title("A film page")
        viewModel.pageFinished("https://example.com/film", canGoBack = false, canGoForward = false)
        val state = viewModel.state.value
        assertTrue(state.secure)
        assertEquals("A film page", state.title)
        assertEquals(listOf("https://example.com/film"), state.visited.map { it.url })
        // Repeated finish events for the same page do not duplicate history entries.
        viewModel.pageFinished("https://example.com/film", canGoBack = true, canGoForward = false)
        assertEquals(1, viewModel.state.value.visited.size)
        viewModel.pageStarted("http://insecure.example/path")
        assertTrue(!viewModel.state.value.secure)
    }

    @Test fun mediaDownloadGoesToTheQueueAndPdfGoesToFiles() = runBlocking {
        viewModel.downloadStart("https://example.com/film.mp4", null, "video/mp4", 1024)
        assertEquals("video/mp4", viewModel.state.value.pending?.mimeType)
        assertTrue(viewModel.state.value.pending!!.isMedia)
        viewModel.confirmDownload()
        withTimeout(5000) {
            while (repository.observeRecent(10).first().isEmpty()) delay(50)
        }
        val media = repository.observeRecent(10).first().single()
        assertEquals(DownloadKind.MEDIA, media.spec.kind)
        assertTrue(media.spec.isDirect)

        viewModel.downloadStart("https://example.com/paper.pdf", null, "application/pdf", 2048)
        assertFalse(viewModel.state.value.pending!!.isMedia)
        viewModel.confirmDownload()
        // Queued rows live in the active set; history only keeps terminal rows.
        withTimeout(5000) {
            while (repository.observeRecent(10).first().size < 2) delay(50)
        }
        val rows = repository.observeRecent(10).first()
        assertEquals(setOf(DownloadKind.MEDIA, DownloadKind.FILE), rows.map { it.spec.kind }.toSet())
    }

    @Test fun clearBrowsingDataDropsSessionHistory() = runBlocking {
        viewModel.pageFinished("https://example.com/one", false, false)
        viewModel.clearBrowsingData()
        assertEquals(BrowserEvent.ClearBrowsingData, nextEvent())
        viewModel.browsingDataCleared()
        assertTrue(viewModel.state.value.visited.isEmpty())
    }
}
