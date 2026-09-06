package com.vidbox.presentation.browser

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidbox.domain.model.ErrorCode
import com.vidbox.domain.usecase.DownloadActions
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
import java.net.URLDecoder
import java.net.URLEncoder
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

data class BrowserState(
    val address: String = "",
    val currentUrl: String? = null,
    val title: String? = null,
    val canGoBack: Boolean = false,
    val canGoForward: Boolean = false,
    val loading: Boolean = false,
    val progress: Int = 0,
    val pending: PendingDownload? = null,
    val jsDialog: JsDialog? = null,
)

sealed interface BrowserEvent {
    data class Navigate(val url: String) : BrowserEvent
    data class Message(val text: String) : BrowserEvent
}

@HiltViewModel
class BrowserViewModel @Inject constructor(
    private val downloads: DownloadActions,
) : ViewModel() {
    private val mutableState = MutableStateFlow(BrowserState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<BrowserEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var enqueueing = false

    fun address(value: String) { mutableState.update { it.copy(address = value.take(8192)) } }

    fun open(value: String) {
        val raw = value.trim()
        if (raw.isEmpty()) return
        val url = normalize(raw) ?: run {
            eventChannel.trySend(BrowserEvent.Message("Enter a complete web address."))
            return
        }
        mutableState.update { it.copy(address = url, loading = true) }
        eventChannel.trySend(BrowserEvent.Navigate(url))
    }

    fun pageStarted(url: String?) {
        mutableState.update { it.copy(loading = true, progress = it.progress.coerceAtLeast(5), currentUrl = url) }
    }

    fun pageFinished(url: String?, canGoBack: Boolean, canGoForward: Boolean) {
        mutableState.update { it.copy(loading = false, progress = 100, currentUrl = url, address = url ?: it.address,
            canGoBack = canGoBack, canGoForward = canGoForward) }
    }

    fun progress(percent: Int) {
        mutableState.update { it.copy(progress = percent.coerceIn(0, 100), loading = percent < 100) }
    }

    fun title(value: String?) { mutableState.update { it.copy(title = value?.take(240)) } }

    fun unsupported(url: String) {
        eventChannel.trySend(BrowserEvent.Message("Vidbox only opens HTTPS web pages and downloaded files. This link was not opened."))
    }

    fun downloadStart(url: String, disposition: String?, mimeType: String?, contentLength: Long) {
        if (enqueueing || mutableState.value.pending != null) return
        val fileName = dispositionFilename(disposition)
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

    /** https-only policy: http addresses are upgraded, anything else becomes a search request. */
    private fun normalize(raw: String): String? {
        if (raw.contains(" ") || !raw.contains(".")) {
            return "https://duckduckgo.com/?q=" + URLEncoder.encode(raw, "UTF-8")
        }
        val withScheme = when {
            raw.startsWith("https://", ignoreCase = true) -> raw
            raw.startsWith("http://", ignoreCase = true) -> "https://" + raw.substringAfter("://")
            else -> "https://$raw"
        }
        return withScheme
    }

    private fun dispositionFilename(disposition: String?): String? {
        val value = disposition ?: return null
        Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)?.let {
            return runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull()?.takeIf(String::isNotBlank)
        }
        return Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(value)?.groupValues?.get(1)
            ?.trim()?.takeIf { it.isNotBlank() }
    }
}
