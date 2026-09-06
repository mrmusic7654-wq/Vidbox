package com.vidbox.presentation.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.DownloadRepository
import com.vidbox.domain.util.ErrorMapper
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.*
import javax.inject.Inject

data class HistoryState(val records: List<DownloadRecord> = emptyList(), val loading: Boolean = true, val error: String? = null)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class HistoryViewModel @Inject constructor(repository: DownloadRepository) : ViewModel() {
    private val mutableQuery = MutableStateFlow(HistoryQuery())
    val query = mutableQuery.asStateFlow()
    val state = mutableQuery.debounce(180).flatMapLatest { query ->
        repository.observeHistory(query).map { HistoryState(it, loading = false) }
            .onStart { emit(HistoryState(loading = true)) }
            .catch { emit(HistoryState(loading = false, error = ErrorMapper.from(it).message)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), HistoryState())
    fun search(value: String) { mutableQuery.update { it.copy(text = value.take(200), limit = 100) } }
    fun filter(value: HistoryFilter) { mutableQuery.update { it.copy(filter = value, limit = 100) } }
    fun sort(value: HistorySort) { mutableQuery.update { it.copy(sort = value, limit = 100) } }
    fun more() { mutableQuery.update { it.copy(limit = (it.limit + 100).coerceAtMost(10000)) } }
}
