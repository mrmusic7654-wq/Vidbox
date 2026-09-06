package com.vidbox.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.usecase.DownloadActions
import com.vidbox.domain.util.BrowserLinks
import com.vidbox.domain.util.ErrorMapper
import com.vidbox.domain.util.FileNames
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
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

/** One visited page of this browser session; never persisted, never synced. */
data class VisitedPage(val url: String, val title: String?, val at: Long)

data class BrowserState(
    val address: String = "",
    val currentUrl: String? = null,
    val title: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loading: Boolean = false,
    val progress: Int = 0,
    val secure: Boolean = false,
    val pending: PendingDownload? = null,
    val jsDialog: JsDialog? = null,
    /** Session page history shown in the browser; cleared with browsing data. */
    val visited: List<VisitedPage> = emptyList(),
    val historyOpen: Boolean = false,
    val homepage: String = BrowserLinks.DEFAULT_HOMEPAGE,
    val desktop: Boolean = false,
    val javaScript: Boolean = true,
    val cookies: Boolean = true,
)

sealed interface BrowserEvent {
    data class Navigate(val url: String) : BrowserEvent
    data class Message(val text: String) : BrowserEvent
    /** The screen owns the WebView, so engine-level data clearing happens there. */
    data object ClearBrowsingData : BrowserEvent
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val downloads: DownloadActions,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var enqueueing = false

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
    }

    fun address(value: String) { mutableState.update { it.copy(address = value.take(8192)) } }

    fun open(value: String) {
        val url = BrowserLinks.normalize(value) ?: run {
            eventChannel.trySend(BrowserEvent.Message("Enter a shorter web address."))
            return
        }
        mutableState.update { it.copy(address = url, loading = true) }
        eventChannel.trySend(BrowserEvent.Navigate(url))
    }

    fun home() = open(state.value.homepage)

    fun stop() { mutableState.update { it.copy(loading = false) } }

    fun pageStarted(url: String?) {
        mutableState.update { it.copy(loading = true, progress = it.progress.coerceAtLeast(5), currentUrl = url,
            secure = url?.startsWith("https://") == true) }
    }

    fun pageFinished(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
        mutableState.update { current ->
            current.copy(loading = false, progress = 100, currentUrl = url, address = url ?: current.address,
                canGoBack = canGoBack, canGoForward = canGoForward,
                secure = url?.startsWith("https://") == true,
                visited = rememberVisit(current.visited, url, current.title))
        }
    }

    fun progress(percent: Int) {
        mutableState.update { it.copy(progress = percent.coerceIn(0, 100), loading = percent < 100) }
    }

    fun title(value: String?) { mutableState.update { it.copy(title = value?.take(240)) } }

    fun unsupported(url: String) {
        eventChannel.trySend(BrowserEvent.Message("Vidbox only opens HTTPS web pages and downloaded files. This link was not opened."))
    }

    fun toggleHistory(open: Boolean) { mutableState.update { it.copy(historyOpen = open) } }

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

    /** Site data, cache, and the session page list are cleared; the engine part happens on screen. */
    fun clearBrowsingData() {
        viewModelScope.launch { eventChannel.send(BrowserEvent.ClearBrowsingData) }
    }

    fun browsingDataCleared() {
        mutableState.update { it.copy(visited = emptyList(), historyOpen = false) }
        eventChannel.trySend(BrowserEvent.Message("Browser data cleared."))
    }

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
                    "This media link has no recognizable file type. Tap “Download from this page” to analyze it instead."
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

    /** Consecutive repeats and non-pages are not history entries; the list stays bounded. */
    private fun rememberVisit(existing: List<VisitedPage>, url: String?, title: String?): List<VisitedPage> {
        if (url == null) return existing
        if (existing.lastOrNull()?.url == url) return existing
        return (existing + VisitedPage(url, title, System.currentTimeMillis())).takeLast(MAX_HISTORY)
    }

    private companion object { const val MAX_HISTORY = 50 }
}
