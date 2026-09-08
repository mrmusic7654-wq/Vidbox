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
import com.vidbox.domain.repository.TabRepository
import com.vidbox.domain.repository.TimeProvider
import com.vidbox.domain.usecase.DownloadActions
import com.vidbox.domain.usecase.FormatPlanner
import com.vidbox.presentation.browser.BrowserEvent
import com.vidbox.presentation.browser.BrowserViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.Base64

/** Browser tab rules and the browser→download pipeline on a real Room database. */
@RunWith(AndroidJUnit4::class)
class BrowserViewModelTest {
    private lateinit var db: VidboxDatabase
    private lateinit var repository: RoomDownloadRepository
    private lateinit var viewModel: BrowserViewModel
    private lateinit var settingsStore: MutableStateFlow<AppSettings>
    private lateinit var tabStore: MemoryTabStore

    private class TestCipher : SecretCipher {
        override fun encrypt(plain: String) = Base64.getEncoder().encodeToString(plain.toByteArray())
        override fun decrypt(encrypted: String) = String(Base64.getDecoder().decode(encrypted))
    }

    private class MemoryTabStore : TabRepository {
        var tabs: List<BrowserTab> = emptyList()
        override fun observe() = flow { emit(tabs) }
        override suspend fun all(): List<BrowserTab> = tabs
        override suspend fun replaceAll(tabs: List<BrowserTab>) { this.tabs = tabs }
        override suspend fun clear() { tabs = emptyList() }
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
        tabStore = MemoryTabStore()
        viewModel = BrowserViewModel(actions, settings, tabStore, TimeProvider(System::currentTimeMillis))
    }

    @After fun teardown() { db.close() }

    private suspend fun nextEvent(): BrowserEvent = withTimeout(5000) { viewModel.events.first() }

    /** Tab restoration runs asynchronously; wait until at least one tab exists. */
    private suspend fun awaitTabs(): String = withTimeout(5000) {
        while (viewModel.state.value.activeTabId == null) delay(20)
        viewModel.state.value.activeTabId!!
    }

    @Test fun typedHostsBecomeHttpsPagesAndProseBecomesSearch() = runBlocking {
        val tabId = awaitTabs()
        viewModel.open(tabId, "example.com/watch")
        val page = nextEvent()
        assertEquals(tabId, (page as BrowserEvent.Navigate).tabId)
        assertEquals("https://example.com/watch", page.url)
        viewModel.open(tabId, "rocket launch video")
        val search = nextEvent()
        assertEquals(tabId, (search as BrowserEvent.Navigate).tabId)
        assertTrue((search as BrowserEvent.Navigate).url.startsWith("https://duckduckgo.com/?q="))
    }

    @Test fun pageEventsTrackSecurityTitleAndSessionHistory() = runBlocking {
        val tabId = awaitTabs()
        viewModel.pageStarted(tabId, "https://example.com/film")
        viewModel.title(tabId, "A film page")
        viewModel.pageFinished(tabId, "https://example.com/film", canGoBack = false, canGoForward = false)
        val state = viewModel.state.value
        assertTrue(state.activeTab!!.secure)
        assertEquals("A film page", state.activeTab!!.title)
        assertEquals(listOf("https://example.com/film"), state.activeTab!!.visited.map { it.url })
        // Repeated finish events for the same page do not duplicate history entries.
        viewModel.pageFinished(tabId, "https://example.com/film", canGoBack = true, canGoForward = false)
        assertEquals(1, viewModel.state.value.activeTab!!.visited.size)
        viewModel.pageStarted(tabId, "http://insecure.example/path")
        assertTrue(!viewModel.state.value.activeTab!!.secure)
    }

    @Test fun tabsOpenCloseAndKeepTheirOwnUrls() = runBlocking {
        val first = awaitTabs()
        val second = viewModel.newTab("https://example.org/second")
        assertEquals(second, viewModel.state.value.activeTabId)
        viewModel.pageFinished(second, "https://example.org/second", false, false)
        viewModel.selectTab(first)
        assertEquals(first, viewModel.state.value.activeTabId)
        assertEquals("https://example.org/second", viewModel.state.value.tabs.first { it.id == second }.currentUrl)
        viewModel.closeTab(first)
        assertEquals(second, viewModel.state.value.activeTabId)
        viewModel.closeTab(second)
        // A fresh blank tab replaces the last closed one; the browser is never tab-less.
        assertEquals(1, viewModel.state.value.tabs.size)
        assertNull(viewModel.state.value.activeTab!!.currentUrl)
    }

    @Test fun mediaDownloadGoesToTheQueueAndPdfGoesToFiles() = runBlocking {
        awaitTabs()
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
        val tabId = awaitTabs()
        viewModel.pageFinished(tabId, "https://example.com/one", false, false)
        viewModel.clearBrowsingData()
        assertEquals(BrowserEvent.ClearBrowsingData, nextEvent())
        viewModel.browsingDataCleared()
        assertTrue(viewModel.state.value.activeTab!!.visited.isEmpty())
    }
}
