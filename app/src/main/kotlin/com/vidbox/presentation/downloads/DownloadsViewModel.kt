package com.vidbox.presentation.downloads

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import com.vidbox.domain.usecase.*
import com.vidbox.domain.util.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

sealed interface DownloadUiEvent {
    data class Message(val text: String) : DownloadUiEvent
    data class Open(val record: DownloadRecord, val share: Boolean) : DownloadUiEvent
}

@HiltViewModel
class DownloadsViewModel @Inject constructor(
    private val repository: DownloadRepository,
    private val actions: DownloadActions,
    private val history: HistoryActions,
    network: NetworkMonitor,
) : ViewModel() {
    private val eventChannel = Channel<DownloadUiEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    val active = repository.observeActive().map { it.filterNot { row -> row.state.isTerminal } }
        .catch { eventChannel.send(DownloadUiEvent.Message(ErrorMapper.from(it).message)); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val recent = repository.observeRecent(4)
        .catch { eventChannel.send(DownloadUiEvent.Message(ErrorMapper.from(it).message)); emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    val connectivity = network.status
    private var verification: Job? = null

    fun pause(id: String) = perform { actions.pause(id) }
    fun resume(id: String) = perform { actions.resume(id) }
    fun cancel(id: String) = perform { actions.cancel(id) }
    fun delete(record: DownloadRecord, deleteFile: Boolean) = perform {
        history.delete(record.id, deleteFile)
        eventChannel.send(DownloadUiEvent.Message(if (deleteFile) "File and history entry deleted" else "History entry removed. Your file was kept."))
    }
    fun open(record: DownloadRecord, share: Boolean = false) = perform {
        if (history.verify(record)) eventChannel.send(DownloadUiEvent.Open(record, share))
        else eventChannel.send(DownloadUiEvent.Message(Errors.of(ErrorCode.MISSING_FILE).message))
    }
    fun verify(records: List<DownloadRecord>) {
        verification?.cancel()
        verification = viewModelScope.launch {
            records.filter { it.state == DownloadState.COMPLETED }.forEach { history.verify(it) }
        }
    }
    fun onForeground() = perform {
        if (repository.active().any { it.state == DownloadState.QUEUED || it.state.isRunning ||
                it.pauseReason in setOf(PauseReason.NETWORK, PauseReason.WIFI) }) actions.wake()
        verify(recent.value)
    }
    private fun perform(block: suspend () -> Unit) {
        viewModelScope.launch {
            try { block() }
            catch (timeout: TimeoutCancellationException) { eventChannel.send(DownloadUiEvent.Message(ErrorMapper.from(timeout).message)) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { eventChannel.send(DownloadUiEvent.Message(ErrorMapper.from(error).message)) }
        }
    }
}
