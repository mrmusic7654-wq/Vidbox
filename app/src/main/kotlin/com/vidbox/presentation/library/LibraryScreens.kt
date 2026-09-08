package com.vidbox.presentation.library

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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

private enum class LibraryMode { VIDEOS, AUDIO }

/**
 * The Video and Audio tabs: every completed download of that kind on the device, in the
 * compact reference style with duration badges, sizes, sort, and search. Tapping a row
 * plays it in the built-in player (as a queue with its neighbours).
 */
@Composable
fun VideosScreen(records: List<DownloadRecord>, query: HistoryQuery, onSearch: (String) -> Unit,
    onSort: (HistorySort) -> Unit, folderLabel: String?, onChooseFolder: () -> Unit,
    onPlay: (Int) -> Unit, callbacks: DownloadCallbacks) {
    LibraryList(LibraryMode.VIDEOS, "All Videos", records, query, onSearch, onSort, folderLabel, onChooseFolder,
        onPlay, callbacks)
}

@Composable
fun AudioScreen(records: List<DownloadRecord>, query: HistoryQuery, onSearch: (String) -> Unit,
    onSort: (HistorySort) -> Unit, folderLabel: String?, onChooseFolder: () -> Unit,
    onPlay: (Int) -> Unit, callbacks: DownloadCallbacks) {
    LibraryList(LibraryMode.AUDIO, "All Audio", records, query, onSearch, onSort, folderLabel, onChooseFolder,
        onPlay, callbacks)
}

@Composable
private fun LibraryList(mode: LibraryMode, title: String, records: List<DownloadRecord>, query: HistoryQuery,
    onSearch: (String) -> Unit, onSort: (HistorySort) -> Unit, folderLabel: String?, onChooseFolder: () -> Unit,
    onPlay: (Int) -> Unit, callbacks: DownloadCallbacks) {
    var searchOpen by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val isVideo = mode == LibraryMode.VIDEOS

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("${mode.name.lowercase()}_screen")) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 10.dp, top = 12.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically) {
            Text("Vidbox", Modifier.weight(1f), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            IconButton(onClick = { searchOpen = !searchOpen }, modifier = Modifier.testTag("library_search_toggle")) {
                Icon(Icons.Rounded.Search, "Search this library")
            }
            Box {
                IconButton(onClick = { sortMenu = true }, modifier = Modifier.testTag("library_sort")) {
                    Icon(VidboxIcons.swapSort, "Sort library")
                }
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
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = {}, shape = RoundedCornerShape(50),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary),
                modifier = Modifier.testTag("library_chip_all")) {
                Icon(VidboxIcons.apps, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(title)
            }
            OutlinedButton(onClick = onChooseFolder, shape = RoundedCornerShape(50)) {
                Icon(VidboxIcons.folder, null, Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(folderLabel ?: "Folder", maxLines = 1)
            }
        }
        if (searchOpen) {
            OutlinedTextField(value = query.text, onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).testTag("library_search"),
                singleLine = true, shape = RoundedCornerShape(50),
                leadingIcon = { Icon(Icons.Rounded.Search, null) },
                placeholder = { Text(if (isVideo) "Search videos" else "Search audio") },
                trailingIcon = { if (query.text.isNotEmpty()) IconButton(onClick = { onSearch("") }) { Icon(VidboxIcons.cancel, "Clear search") } })
        }
        if (records.isEmpty()) {
            EmptyState(if (isVideo) VidboxIcons.video else VidboxIcons.audio,
                if (isVideo) "No videos yet" else "No audio yet",
                if (isVideo) "Downloaded videos land here, ready to play in Vidbox."
                else "Downloaded audio and music land here, ready to play.",
                action = null, modifier = Modifier.padding(top = 48.dp))
        } else {
            LazyColumn(contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)) {
                items(records, key = { it.id }) { record ->
                    val index = records.indexOf(record)
                    MediaRow(record, callbacks,
                        onClick = if (record.state == DownloadState.COMPLETED && !record.fileMissing &&
                            record.outputUri != null) ({ onPlay(index) }) else null,
                        tag = "download_${record.id}")
                }
            }
        }
    }
}
