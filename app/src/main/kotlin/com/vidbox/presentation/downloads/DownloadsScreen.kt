package com.vidbox.presentation.downloads

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vidbox.domain.model.*
import com.vidbox.presentation.components.*

/**
 * The Download tab: live transfers first, then a "Completed" list in the compact
 * downloader style, with search and sort like the reference design's top row.
 */
@Composable
fun DownloadsScreen(active: List<DownloadRecord>, completed: List<DownloadRecord>, notificationsAllowed: Boolean,
    maxConcurrent: Int, query: HistoryQuery, onSearch: (String) -> Unit, onSort: (HistorySort) -> Unit,
    onNotifications: () -> Unit, onAddLink: () -> Unit, callbacks: DownloadCallbacks) {
    var searchOpen by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("downloads_screen")) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Vidbox", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.testTag("downloads_search_toggle")) {
                Icon(Icons.Rounded.Search, "Search downloads")
            }
            Box {
                IconButton(onClick = { sortMenu = true }) { Icon(VidboxIcons.swapSort, "Sort downloads") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    HistorySort.entries.forEach { sort ->
                        DropdownMenuItem(text = { Text(when (sort) {
                            HistorySort.NEWEST -> "Newest first"; HistorySort.OLDEST -> "Oldest first"
                            HistorySort.NAME -> "Filename A–Z"; HistorySort.LARGEST -> "Largest first"
                        }) }, trailingIcon = { if (query.sort == sort) Icon(VidboxIcons.check, null) },
                            onClick = { onSort(sort); sortMenu = false })
                    }
                }
            }
        }
        if (searchOpen) {
            OutlinedTextField(value = query.text, onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("downloads_search"),
                singleLine = true, shape = RoundedCornerShape(50),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text("Search your downloads") },
                trailingIcon = { if (query.text.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(VidboxIcons.cancel, "Clear search") } })
        }
        LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            if (!notificationsAllowed) item {
                InfoBanner("Enable notifications to see background progress and controls.", action = "Enable", onAction = onNotifications)
            }
            if (active.isEmpty() && completed.isEmpty()) item {
                EmptyState(VidboxIcons.cloudDownload, "Nothing in the queue",
                    "Downloads continue here when you leave the app. Completed files stay ready to watch.",
                    action = "Add a link", onAction = onAddLink, modifier = Modifier.padding(top = 24.dp))
            }
            if (active.isNotEmpty()) {
                item { Text("Downloading · up to $maxConcurrent at once", style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 6.dp, top = 4.dp)) }
                items(active, key = { "active_${it.id}" }) { DownloadCard(it, callbacks, compact = true) }
            }
            if (completed.isNotEmpty()) {
                item {
                    Row(Modifier.fillMaxWidth().padding(start = 6.dp, top = 8.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Rounded.Check, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Text("Completed", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("${completed.size}", style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                items(completed, key = { "done_${it.id}" }) { MediaRow(it, callbacks, tag = "download_${it.id}") }
            }
        }
    }
}
