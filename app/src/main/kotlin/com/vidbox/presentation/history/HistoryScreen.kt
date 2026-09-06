package com.vidbox.presentation.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vidbox.domain.model.*
import com.vidbox.presentation.components.*

@Composable
fun HistoryScreen(state: HistoryState, query: HistoryQuery, onSearch: (String) -> Unit, onFilter: (HistoryFilter) -> Unit,
    onSort: (HistorySort) -> Unit, onMore: () -> Unit, onAddLink: () -> Unit, callbacks: DownloadCallbacks) {
    var sortMenu by remember { mutableStateOf(false) }
    LazyColumn(Modifier.fillMaxSize().testTag("history_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Your library", style = MaterialTheme.typography.headlineLarge)
                    Text("Saved locally. Kept for later.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { sortMenu = true }) { Icon(Icons.Rounded.Sort, "Sort history") }
                    DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                        HistorySort.entries.forEach { sort ->
                            DropdownMenuItem(text = { Text(when (sort) {
                                HistorySort.NEWEST -> "Newest first"; HistorySort.OLDEST -> "Oldest first"
                                HistorySort.NAME -> "Filename A–Z"; HistorySort.LARGEST -> "Largest first"
                            }) }, trailingIcon = { if (query.sort == sort) Icon(Icons.Rounded.Check, null) },
                                onClick = { onSort(sort); sortMenu = false })
                        }
                    }
                }
            }
        }
        item {
            OutlinedTextField(value = query.text, onValueChange = onSearch, modifier = Modifier.fillMaxWidth().testTag("history_search"), singleLine = true,
                shape = RoundedCornerShape(14.dp), leadingIcon = { Icon(Icons.Rounded.Search, null) }, placeholder = { Text("Search your downloads") },
                trailingIcon = { if (query.text.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(Icons.Rounded.Close, "Clear search") } })
        }
        item {
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                HistoryFilter.entries.forEach { filter ->
                    FilterChip(selected = query.filter == filter, onClick = { onFilter(filter) }, label = { Text(when (filter) {
                        HistoryFilter.ALL -> "All"; HistoryFilter.COMPLETED -> "Saved"; HistoryFilter.FAILED -> "Failed"; HistoryFilter.CANCELLED -> "Cancelled"
                    }) })
                }
            }
        }
        if (state.loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
        state.error?.let { item { InfoBanner(it, error = true) } }
        if (!state.loading && state.records.isEmpty()) item {
            EmptyState(Icons.Rounded.VideoLibrary, if (query.text.isNotBlank() || query.filter != HistoryFilter.ALL) "No matching downloads" else "A library of your own",
                if (query.text.isNotBlank() || query.filter != HistoryFilter.ALL) "Try another search or switch to All." else "Completed downloads will appear here, even after you restart Vidbox.",
                action = if (query.text.isBlank() && query.filter == HistoryFilter.ALL) "Save your first video" else null, onAction = onAddLink)
        }
        items(state.records, key = { it.id }) { DownloadCard(it, callbacks) }
        if (state.records.size >= query.limit && query.limit < 10000) item {
            OutlinedButton(onClick = onMore, modifier = Modifier.fillMaxWidth()) { Text("Load more") }
        }
    }
}
