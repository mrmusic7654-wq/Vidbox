package com.vidbox.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.usecase.HistoryActions
import com.vidbox.domain.util.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repository: SettingsRepository,
    private val history: HistoryActions,
) : ViewModel() {
    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val messages = eventChannel.receiveAsFlow()
    val state = repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppSettings())
    fun update(transform: (AppSettings) -> AppSettings) {
        viewModelScope.launch {
            try { repository.update(transform) }
            catch (timeout: TimeoutCancellationException) { eventChannel.send(ErrorMapper.from(timeout).message) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { eventChannel.send(ErrorMapper.from(error).message) }
        }
    }
    fun location(uri: String?, label: String?) = update { it.copy(destinationTree = uri, destinationLabel = label) }
    fun clearHistory() {
        viewModelScope.launch {
            try { history.clearHistory(); eventChannel.send("History cleared. Downloaded files were not deleted.") }
            catch (timeout: TimeoutCancellationException) { eventChannel.send(ErrorMapper.from(timeout).message) }
            catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) { eventChannel.send(ErrorMapper.from(error).message) }
        }
    }
    fun report(message: String) { viewModelScope.launch { eventChannel.send(message) } }
}
