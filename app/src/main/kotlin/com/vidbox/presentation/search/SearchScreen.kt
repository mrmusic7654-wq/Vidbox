package com.vidbox.presentation.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vidbox.domain.model.VideoSearchResult
import com.vidbox.domain.util.DisplayFormat
import com.vidbox.presentation.components.DurationBadge
import com.vidbox.presentation.components.InfoBanner
import com.vidbox.presentation.components.VidboxIcons

/**
 * The full-screen "Search video online" flow from the home screen: recent searches first,
 * then YouTube results with a per-result download action. Pasting a URL here skips the
 * search and opens that exact video in the analyzer.
 */
@Composable
fun SearchScreen(state: SearchState, analysisError: String?, analyzing: Boolean, onBack: () -> Unit, onInput: (String) -> Unit,
    onSubmit: () -> Unit, onUseRecent: (String) -> Unit, onRemoveRecent: (String) -> Unit, onClearRecents: () -> Unit,
    onDownload: (VideoSearchResult) -> Unit, onOpenInBrowser: (VideoSearchResult) -> Unit) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).testTag("search_screen")) {
        Surface(color = MaterialTheme.colorScheme.background, shadowElevation = 1.dp,
            modifier = Modifier.statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("search_back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to home")
                }
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onInput,
                    modifier = Modifier.weight(1f).focusRequester(focus).testTag("search_input"),
                    placeholder = { Text("Search video online…") },
                    singleLine = true,
                    shape = RoundedCornerShape(24.dp),
                    leadingIcon = { Icon(Icons.Rounded.Search, null) },
                    trailingIcon = {
                        if (state.query.isNotEmpty()) IconButton(onClick = { onInput("") },
                            modifier = Modifier.testTag("search_clear")) {
                            Icon(Icons.Rounded.Close, "Clear search")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant))
                IconButton(onClick = onSubmit, enabled = state.query.isNotBlank(),
                    modifier = Modifier.testTag("search_submit")) {
                    Icon(Icons.Rounded.Search, "Search")
                }
            }
        }

        // Inline feedback sits directly under the search bar so it stays on screen
        // even when the results list below fills the remaining height.
        if (analyzing) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Fetching download options…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        (state.analysisError ?: analysisError)?.let {
            Box(Modifier.padding(16.dp)) { InfoBanner(it, error = true, modifier = Modifier.testTag("analysis_error")) }
        }

        when {
            state.searching -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
                Text("Searching YouTube for “${state.query.trim()}”…", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.error != null -> Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                InfoBanner(state.error, error = true, modifier = Modifier.testTag("search_error"))
                TextButton(onClick = onSubmit) { Text("Try again") }
            }
            state.query.isBlank() -> Recents(state, onUseRecent, onRemoveRecent, onClearRecents)
            else -> Results(state.results, state.searchedFor.orEmpty(), onDownload, onOpenInBrowser)
        }
    }
}

@Composable
private fun Recents(state: SearchState, onUseRecent: (String) -> Unit, onRemoveRecent: (String) -> Unit,
    onClearRecents: () -> Unit) {
    if (state.recents.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(VidboxIcons.search, null, Modifier.size(44.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f))
            Spacer(Modifier.height(14.dp))
            Text("Search YouTube by name", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(6.dp))
            Text("Type a topic, a song, a channel — or paste any video link to download it.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center)
        }
        return
    }
    Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("Recent searches", style = MaterialTheme.typography.titleMedium)
            TextButton(onClick = onClearRecents) { Text("Clear all", color = MaterialTheme.colorScheme.error) }
        }
        LazyColumn(Modifier.testTag("search_recents")) {
            items(state.recents, key = { it }) { recent ->
                Row(Modifier.fillMaxWidth().clickable { onUseRecent(recent) }
                    .padding(horizontal = 12.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null, Modifier.padding(start = 10.dp).size(22.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(recent, Modifier.weight(1f).padding(horizontal = 14.dp, vertical = 14.dp),
                        style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    IconButton(onClick = { onRemoveRecent(recent) }, modifier = Modifier.testTag("search_recent_remove")) {
                        Icon(Icons.Rounded.Close, "Remove $recent")
                    }
                }
                HorizontalDivider(Modifier.padding(horizontal = 16.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun Results(results: List<VideoSearchResult>, searchedFor: String,
    onDownload: (VideoSearchResult) -> Unit, onOpenInBrowser: (VideoSearchResult) -> Unit) {
    LazyColumn(Modifier.fillMaxSize().testTag("search_results"),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (searchedFor.isNotEmpty()) {
            item {
                Text("Results for “$searchedFor”", style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(start = 4.dp, bottom = 2.dp))
            }
        }
        items(results, key = { it.id }) { result ->
            SearchResultRow(result, onDownload, onOpenInBrowser)
        }
        if (results.isEmpty()) {
            item {
                Text("No videos found. Try a different wording.",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(20.dp))
            }
        }
    }
}

@Composable
private fun SearchResultRow(result: VideoSearchResult, onDownload: (VideoSearchResult) -> Unit,
    onOpenInBrowser: (VideoSearchResult) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onOpenInBrowser(result) }.testTag("search_result_${result.id}"),
        verticalAlignment = Alignment.Top) {
        Box {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current).data(result.thumbnailUrl).size(320).crossfade(true).build(),
                contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.size(148.dp, 84.dp).background(
                    MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(10.dp)))
            DurationBadge(result.durationSeconds, Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
        Column(Modifier.weight(1f).padding(start = 11.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(result.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(result.channel, result.viewsLabel).joinToString("  ·  "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (result.durationSeconds != null) {
                Text(DisplayFormat.duration(result.durationSeconds?.toLong()),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f))
            }
        }
        FilledTonalIconButton(onClick = { onDownload(result) },
            modifier = Modifier.size(42.dp).testTag("search_download_${result.id}")) {
            Icon(Icons.Rounded.Download, "Download ${result.title}", Modifier.size(20.dp))
        }
    }
}
