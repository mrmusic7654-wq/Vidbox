package com.vidbox.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidbox.domain.model.BrowserTab
import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.repository.TabRepository
import com.vidbox.domain.repository.TimeProvider
import com.vidbox.domain.usecase.DownloadActions
import com.vidbox.domain.util.BrowserLinks
import com.vidbox.domain.util.ErrorMapper
import com.vidbox.domain.util.FileNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class PendingDownload(
    val url: String,
    val fileName: String?,
    val mimeType: String?,
    val totalBytes: Long?,
    val isMedia: Boolean,
)

data class JsDialog(
    val message: String,
    val promptText: String? = null,
    val default: String = "",
    val onResult: (String?) -> Unit,
)

/** One visited page of one tab; in memory only, cleared with browsing data. */
data class VisitedPage(val url: String, val title: String?, val at: Long)

/** Chrome-style tab state. A tab that was never shown yet carries its [pendingUrl]. */
data class BrowserTabUi(
    val id: String,
    val address: String = "",
    val currentUrl: String? = null,
    val pendingUrl: String? = null,
    val title: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loading: Boolean = false,
    val progress: Int = 0,
    val secure: Boolean = false,
    val visited: List<VisitedPage> = emptyList(),
    val createdAt: Long = 0,
)

data class BrowserState(
    val tabs: List<BrowserTabUi> = emptyList(),
    val activeTabId: String? = null,
    val switcherOpen: Boolean = false,
    /** Page-history sheet of the active tab. */
    val historyOpen: Boolean = false,
    val homepage: String = BrowserLinks.DEFAULT_HOMEPAGE,
    val desktop: Boolean = false,
    val javaScript: Boolean = true,
    val cookies: Boolean = true,
    val pending: PendingDownload? = null,
    val jsDialog: JsDialog? = null,
) {
    val activeTab: BrowserTabUi? get() = tabs.firstOrNull { it.id == activeTabId }
}

sealed interface BrowserEvent {
    data class Navigate(val tabId: String, val url: String) : BrowserEvent
    data class Message(val text: String) : BrowserEvent
    /** The screen owns the WebViews, so engine-level data clearing happens there. */
    data object ClearBrowsingData : BrowserEvent
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val downloads: DownloadActions,
    private val settings: SettingsRepository,
    private val tabStore: TabRepository,
    private val clock: TimeProvider,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var enqueueing = false
    private var persisting: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.collect { prefs ->
                mutableState.update {
                    it.copy(homepage = BrowserLinks.homepage(prefs.browserHomepage),
                        desktop = prefs.browserDesktop, javaScript = prefs.browserJavaScript,
                        cookies = prefs.browserCookies)
                }
            }
        }
        viewModelScope.launch { restoreTabs() }
    }

    private suspend fun restoreTabs() {
        val prefs = runCatching { settings.settings.first() }.getOrNull()
        val saved = if (prefs?.restoreTabs != false) {
            runCatching { tabStore.all() }.getOrDefault(emptyList())
                .sortedBy { it.position }
                .take(MAX_TABS)
                .map { tab -> BrowserTabUi(id = tab.id, address = tab.url.orEmpty(), pendingUrl = tab.url,
                    title = tab.title, currentUrl = tab.url.takeIf { BrowserLinks.isWebPage(it) },
                    secure = tab.url?.startsWith("https://") == true, createdAt = tab.createdAt) }
                .ifEmpty { null }
        } else null
        runCatching { tabStore.clear() }
        val seeded = saved ?: listOf(newTabState())
        mutableState.update {
            it.copy(tabs = seeded, activeTabId = (saved?.firstOrNull { tab -> tab.id == it.activeTabId } ?: saved?.firstOrNull()
                ?: seeded.first()).id)
        }
        persist()
    }

    // Tabs ---------------------------------------------------------------------------------------

    fun newTab(url: String? = null): String {
        val existingCount = mutableState.value.tabs.size
        if (existingCount >= MAX_TABS) {
            eventChannel.trySend(BrowserEvent.Message("Up to $MAX_TABS tabs. Close one to open another."))
            return mutableState.value.activeTabId ?: ""
        }
        val tab = if (url == null) newTabState() else newTabState().copy(
            address = url, pendingUrl = url, loading = true, secure = url.startsWith("https://"))
        mutableState.update { it.copy(tabs = it.tabs + tab, activeTabId = tab.id, switcherOpen = false) }
        persist()
        return tab.id
    }

    /** Opens a fresh tab for [url] (home shortcut tiles, search results). Returns its id. */
    fun openInNewTab(url: String): String = newTab(url)

    fun closeTab(tabId: String) {
        mutableState.update { current ->
            val tabs = current.tabs.filterNot { it.id == tabId }
            val nextTabs = if (tabs.isEmpty()) listOf(newTabState()) else tabs
            val nextActive = when {
                current.activeTabId != tabId -> current.activeTabId
                else -> {
                    val index = current.tabs.indexOfFirst { it.id == tabId }
                    (nextTabs.getOrNull(index) ?: nextTabs.lastOrNull())?.id
                }
            }
            current.copy(tabs = nextTabs, activeTabId = nextActive, switcherOpen = nextTabs.size > 0 && current.switcherOpen)
        }
        persist()
    }

    fun closeAllTabs() {
        val fresh = newTabState()
        mutableState.update { it.copy(tabs = listOf(fresh), activeTabId = fresh.id, switcherOpen = false) }
        persist()
    }

    fun selectTab(tabId: String) {
        mutableState.update { current ->
            if (current.tabs.any { it.id == tabId }) current.copy(activeTabId = tabId, switcherOpen = false)
            else current
        }
        persist()
    }

    fun toggleSwitcher(open: Boolean) { mutableState.update { it.copy(switcherOpen = open) } }

    fun toggleHistory(open: Boolean) { mutableState.update { it.copy(historyOpen = open) } }

    // Address and page lifecycle -----------------------------------------------------------------

    fun address(tabId: String, value: String) = updateTab(tabId) { it.copy(address = value.take(8192)) }

    fun open(tabId: String, value: String) {
        val url = BrowserLinks.normalize(value) ?: run {
            eventChannel.trySend(BrowserEvent.Message("Enter a shorter web address."))
            return
        }
        updateTab(tabId) { it.copy(address = url, pendingUrl = url, loading = true, canGoBack = it.canGoBack) }
        eventChannel.trySend(BrowserEvent.Navigate(tabId, url))
        persist()
    }

    fun openCurrent(value: String) {
        val tabId = state.value.activeTabId ?: return
        open(tabId, value)
    }

    fun home(tabId: String) = open(tabId, state.value.homepage)

    fun reload(tabId: String) {
        val tab = state.value.activeTab ?: return
        val url = tab.currentUrl ?: tab.pendingUrl ?: return
        open(tabId.ifBlank { tab.id }, url)
    }

    fun stop(tabId: String) = updateTab(tabId) { it.copy(loading = false) }

    fun pageStarted(tabId: String, url: String?) = updateTab(tabId) {
        it.copy(loading = true, progress = it.progress.coerceAtLeast(5), currentUrl = url,
            secure = url?.startsWith("https://") == true)
    }

    fun pageFinished(tabId: String, url: String?, canGoBack: Boolean, canGoForward: Boolean) {
        updateTab(tabId) { current ->
            current.copy(loading = false, progress = 100, currentUrl = url, address = url ?: current.address,
                canGoBack = canGoBack, canGoForward = canGoForward,
                secure = url?.startsWith("https://") == true,
                visited = rememberVisit(current.visited, url, current.title))
        }
        persist()
    }

    fun progress(tabId: String, percent: Int) = updateTab(tabId) {
        it.copy(progress = percent.coerceIn(0, 100), loading = percent < 100)
    }

    fun title(tabId: String, value: String?) = updateTab(tabId) { it.copy(title = value?.take(240)) }

    fun unsupported(url: String) {
        eventChannel.trySend(BrowserEvent.Message("Vidbox only opens HTTPS web pages and downloaded files. This link was not opened."))
    }

    fun setDesktop(enabled: Boolean) {
        viewModelScope.launch {
            try {
                settings.update { it.copy(browserDesktop = enabled) }
                eventChannel.trySend(BrowserEvent.Message(if (enabled)
                    "Desktop site on. Pages reload with a desktop layout." else "Mobile site on."))
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { eventChannel.trySend(BrowserEvent.Message(ErrorMapper.from(error).message)) }
        }
    }

    /** Site data, cache, and the per-tab page lists are cleared; the engine part happens on screen. */
    fun clearBrowsingData() {
        viewModelScope.launch { eventChannel.send(BrowserEvent.ClearBrowsingData) }
    }

    fun browsingDataCleared() {
        mutableState.update { current -> current.copy(tabs = current.tabs.map { it.copy(visited = emptyList()) },
            switcherOpen = false) }
        eventChannel.trySend(BrowserEvent.Message("Browser data cleared."))
    }

    // Downloads ----------------------------------------------------------------------------------

    fun downloadStart(url: String, disposition: String?, mimeType: String?, contentLength: Long) {
        if (enqueueing || mutableState.value.pending != null) return
        val fileName = BrowserLinks.dispositionFilename(disposition)
        val ext = FileNames.extensionOf(url, fileName)
        val media = mimeType?.startsWith("video/") == true || mimeType?.startsWith("audio/") == true ||
            (ext != null && ext in FileNames.extensions)
        mutableState.update {
            it.copy(pending = PendingDownload(url = url, fileName = fileName,
                mimeType = mimeType?.take(120), totalBytes = contentLength.takeIf { size -> size > 0 }, isMedia = media))
        }
    }

    fun confirmDownload() {
        val pending = mutableState.value.pending ?: return
        if (enqueueing) return
        mutableState.update { it.copy(pending = null) }
        enqueueing = true
        viewModelScope.launch {
            try {
                if (pending.isMedia) {
                    downloads.enqueueDirect(pending.url, fileName = pending.fileName,
                        mimeType = pending.mimeType, totalBytes = pending.totalBytes)
                } else {
                    val ext = FileNames.extensionOf(pending.url, pending.fileName) ?: "bin"
                    downloads.enqueueFile(pending.url, title = pending.fileName, extension = ext,
                        mimeType = pending.mimeType, totalBytes = pending.totalBytes)
                }
                eventChannel.send(BrowserEvent.Message("Added to your download queue"))
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                val mapped = ErrorMapper.from(error)
                val text = if (pending.isMedia && mapped.code == ErrorCode.FORMAT_UNAVAILABLE)
                    "This media link has no recognizable file type. Use the menu → “Download from this page” to analyze it instead."
                else mapped.message
                eventChannel.send(BrowserEvent.Message(text))
            } finally { enqueueing = false }
        }
    }

    fun dismissDownload() { mutableState.update { it.copy(pending = null) } }

    fun jsDialog(message: String, promptDefault: String? = null,
        isPrompt: Boolean, onResult: (String?) -> Unit) {
        mutableState.update {
            it.copy(jsDialog = JsDialog(message, if (isPrompt) (promptDefault ?: "") else null, promptDefault ?: "", onResult))
        }
    }

    fun dismissJsDialog(value: String?) {
        val dialog = mutableState.value.jsDialog ?: return
        mutableState.update { it.copy(jsDialog = null) }
        dialog.onResult(value)
    }

    // Internals ----------------------------------------------------------------------------------

    private fun updateTab(tabId: String, transform: (BrowserTabUi) -> BrowserTabUi) {
        mutableState.update { current ->
            current.copy(tabs = current.tabs.map { if (it.id == tabId) transform(it) else it })
        }
    }

    private fun newTabState(): BrowserTabUi =
        BrowserTabUi(id = UUID.randomUUID().toString(), createdAt = clock.nowMillis())

    /** Consecutive repeats and non-pages are not history entries; the list stays bounded. */
    private fun rememberVisit(existing: List<VisitedPage>, url: String?, title: String?): List<VisitedPage> {
        if (url == null) return existing
        if (existing.lastOrNull()?.url == url) return existing
        return (existing + VisitedPage(url, title, System.currentTimeMillis())).takeLast(MAX_HISTORY)
    }

    private fun persist() {
        persisting?.cancel()
        persisting = viewModelScope.launch {
            delay(400)
            val snapshot = state.value
            val tabs = snapshot.tabs.mapIndexed { index, tab -> BrowserTab(
                id = tab.id,
                url = (tab.currentUrl ?: tab.pendingUrl)?.takeIf(BrowserLinks::isWebPage),
                title = tab.title, position = index, isActive = tab.id == snapshot.activeTabId,
                createdAt = tab.createdAt, lastActiveAt = clock.nowMillis(), faviconPath = null)
            }
            runCatching { tabStore.replaceAll(tabs) }
        }
    }

    private companion object {
        const val MAX_HISTORY = 50
        const val MAX_TABS = 20
    }
}
