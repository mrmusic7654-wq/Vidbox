package com.vidbox.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidbox.domain.model.VideoSearchResult
import com.vidbox.domain.repository.SettingsRepository
import com.vidbox.domain.usecase.SearchVideos
import com.vidbox.domain.util.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class SearchState(
    val query: String = "",
    val searching: Boolean = false,
    val error: String? = null,
    val results: List<VideoSearchResult> = emptyList(),
    val searchedFor: String? = null,
    val recents: List<String> = emptyList(),
)

sealed interface SearchEvent {
    /** The typed text is a link, not a phrase: the app shell routes it into analysis. */
    data class OpenLink(val url: String) : SearchEvent
}

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchVideos: SearchVideos,
    private val settings: SettingsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(SearchState())
    val state = mutableState.asStateFlow()
    private val eventChannel = Channel<SearchEvent>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()
    private var search: Job? = null

    init {
        viewModelScope.launch {
            settings.settings.map { it.searchHistory }.collect { recents ->
                mutableState.update { it.copy(recents = recents) }
            }
        }
    }

    fun input(value: String) { mutableState.update { it.copy(query = value.take(SearchVideos.MAX_QUERY), error = null) } }

    fun submit() {
        val query = state.value.query.trim()
        if (query.isEmpty() || state.value.searching) return
        if (SearchVideos.looksLikeLink(query)) {
            mutableState.update { it.copy(error = null) }
            eventChannel.trySend(SearchEvent.OpenLink(query))
            return
        }
        search?.cancel()
        search = viewModelScope.launch {
            mutableState.update { it.copy(searching = true, error = null) }
            try {
                val results = searchVideos(query)
                rememberRecent(query)
                mutableState.update { it.copy(searching = false, results = results, searchedFor = query) }
            } catch (timeout: TimeoutCancellationException) {
                mutableState.update { it.copy(searching = false, error = ErrorMapper.from(timeout).message) }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                mutableState.update { it.copy(searching = false, error = ErrorMapper.from(error).fullMessage) }
            }
        }
    }

    fun useRecent(query: String) { input(query); submit() }

    fun removeRecent(query: String) {
        viewModelScope.launch {
            runCatching { settings.update { it.copy(searchHistory = it.searchHistory - query) } }
        }
    }

    fun clearRecents() {
        viewModelScope.launch {
            runCatching { settings.update { it.copy(searchHistory = emptyList()) } }
        }
    }

    private suspend fun rememberRecent(query: String) {
        runCatching { settings.update { current ->
            current.copy(searchHistory =
                (listOf(query) + current.searchHistory.filterNot { it.equals(query, ignoreCase = true) }).take(MAX_RECENTS))
        } }
    }

    private companion object { const val MAX_RECENTS = 12 }
}
