package com.vidbox.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.usecase.*
import com.vidbox.domain.util.ErrorMapper
import com.vidbox.domain.util.UrlValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HomeState(
    val url: String = "",
    val analyzing: Boolean = false,
    val error: String? = null,
    val media: MediaInfo? = null,
    val options: List<FormatSelection> = emptyList(),
    val selectedKey: String? = null,
    val video: Boolean = true,
    val container: String? = null,
    val enqueueing: Boolean = false,
    /** Source has video, but none of it fits MP4 (stream copy only, no re-encoding). */
    val videoUnavailable: Boolean = false,
) {
    val selected get() = options.find { it.key == selectedKey }
    val visibleOptions get() = options.filter { it.primary.hasVideo == video && (container == null || it.container == container) }
}

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val analyzeVideo: AnalyzeVideo,
    private val planner: FormatPlanner,
    private val downloads: DownloadActions,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(HomeState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<Unit>(Channel.BUFFERED)
    val queued = eventChannel.receiveAsFlow()
    private var analysis: Job? = null
    private var preferences = AppSettings()

    fun input(value: String) { mutableState.update { it.copy(url = value.take(8192), error = null) } }
    fun paste(value: String) = input(UrlValidator.findInSharedText(value))
    fun analyze() {
        analysis?.cancel()
        val input = state.value.url
        analysis = viewModelScope.launch {
            mutableState.update { it.copy(analyzing = true, error = null, media = null) }
            try {
                val result = analyzeVideo(input)
                val options = planner.options(result)
                if (options.isEmpty()) throw Errors.exception(ErrorCode.FORMAT_UNAVAILABLE)
                preferences = settings.settings.first()
                val video = options.any { it.primary.hasVideo }
                val selected = planner.default(options, preferences, video)
                mutableState.update { it.copy(analyzing = false, media = result, options = options,
                    selectedKey = selected?.key, video = video, container = null,
                    videoUnavailable = result.formats.any { it.hasVideo } && !video) }
            } catch (timeout: TimeoutCancellationException) {
                mutableState.update { it.copy(analyzing = false, error = ErrorMapper.from(timeout).message) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                mutableState.update { it.copy(analyzing = false, error = ErrorMapper.from(error).fullMessage) }
            }
        }
    }
    fun cancelAnalysis() { analysis?.cancel(); mutableState.update { it.copy(analyzing = false) } }
    fun dismissError() { mutableState.update { if (it.media != null) it else it.copy(error = null) } }
    fun dismissFormats() { mutableState.update { it.copy(media = null, error = null, options = emptyList(), selectedKey = null, videoUnavailable = false) } }
    fun select(key: String) {
        if (state.value.options.any { it.key == key }) mutableState.update { it.copy(selectedKey = key) }
    }
    fun mode(video: Boolean) {
        mutableState.update { it.copy(video = video, container = null,
            selectedKey = planner.default(it.options, preferences, video)?.key) }
    }
    fun container(value: String?) {
        mutableState.update {
            val visible = it.options.filter { option -> option.primary.hasVideo == it.video && (value == null || option.container == value) }
            it.copy(container = value, selectedKey = it.selectedKey?.takeIf { key -> visible.any { option -> option.key == key } }
                ?: planner.default(visible, preferences, it.video)?.key)
        }
    }
    fun download() {
        val snapshot = state.value
        val media = snapshot.media ?: return
        val selected = snapshot.selected ?: return
        if (snapshot.enqueueing) return
        viewModelScope.launch {
            mutableState.update { it.copy(enqueueing = true, error = null) }
            try {
                downloads.enqueue(media, selected)
                mutableState.update { it.copy(enqueueing = false, media = null, options = emptyList(), selectedKey = null) }
                eventChannel.send(Unit)
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                mutableState.update { it.copy(enqueueing = false, error = ErrorMapper.from(error).fullMessage) }
            }
        }
    }
}
