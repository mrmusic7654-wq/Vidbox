package com.vidbox.presentation.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.vidbox.domain.model.*
import com.vidbox.presentation.components.*

@Composable
fun HomeScreen(state: HomeState, active: List<DownloadRecord>, recent: List<DownloadRecord>, network: NetworkStatus,
    onInput: (String) -> Unit, onPaste: () -> Unit, onAnalyze: () -> Unit, onCancel: () -> Unit,
    onBrowse: () -> Unit, onDownloads: () -> Unit, onHistory: () -> Unit, callbacks: DownloadCallbacks) {
    LazyColumn(Modifier.fillMaxSize().testTag("home_screen"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(Modifier.padding(top = 6.dp, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Save it.\nKeep it.", style = MaterialTheme.typography.displaySmall)
                Text("Your videos, ready when you are.", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        item {
            OutlinedCard(shape = RoundedCornerShape(24.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text("ADD A VIDEO OR AUDIO LINK", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    OutlinedTextField(value = state.url, onValueChange = onInput, modifier = Modifier.fillMaxWidth().testTag("home_url"),
                        placeholder = { Text("https://…") }, label = { Text("Video URL") }, singleLine = true,
                        enabled = !state.analyzing, shape = RoundedCornerShape(14.dp),
                        leadingIcon = { Icon(Icons.Rounded.Link, null) },
                        trailingIcon = { IconButton(onClick = onPaste, enabled = !state.analyzing) { Icon(Icons.Rounded.ContentPaste, "Paste link") } },
                        isError = state.error != null, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                        keyboardActions = KeyboardActions(onGo = { onAnalyze() }))
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.testTag("analysis_error")) }
                    if (state.analyzing) {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Reading the source and finding available formats…", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                            TextButton(onClick = onCancel, modifier = Modifier.testTag("cancel_analysis")) { Text("Cancel") }
                        }
                    } else {
                        Button(onClick = onAnalyze, enabled = state.url.isNotBlank(), modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("analyze_button"),
                            shape = RoundedCornerShape(14.dp)) {
                            Icon(Icons.Rounded.ManageSearch, null, Modifier.size(21.dp)); Spacer(Modifier.width(9.dp)); Text("Analyze link")
                        }
                    }
                    Row(verticalAlignment = Alignment.Top) {
                        Icon(Icons.Rounded.VerifiedUser, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Only save media you own or have permission to download. Secure HTTPS links only.",
                            Modifier.padding(start = 7.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    OutlinedButton(onClick = onBrowse, modifier = Modifier.fillMaxWidth().testTag("home_browse")) {
                        Icon(Icons.Rounded.Public, null, Modifier.size(19.dp)); Spacer(Modifier.width(8.dp)); Text("Browse websites")
                    }
                }
            }
        }
        if (network.blocked) item { InfoBanner("Android is restricting network access. Check Data Saver or this app’s network settings.") }
        if (!network.connected) item { InfoBanner("You're offline. Queued downloads will wait for a connection.") }
        if (active.isNotEmpty()) {
            item { SectionHeading("In progress · ${active.size}", "View all", onDownloads) }
            items(active.take(2), key = { "active_${it.id}" }) { DownloadCard(it, callbacks) }
        }
        val saved = recent.filter { it.state.isTerminal }
        item { SectionHeading("Recently saved", if (saved.isNotEmpty()) "Library" else null, onHistory) }
        if (saved.isEmpty()) item {
            EmptyState(Icons.Rounded.VideoLibrary, "Make room for your favorites", "Paste a public media link, choose its quality, and save it to your device.",
                modifier = Modifier.padding(vertical = 2.dp))
        } else items(saved, key = { "recent_${it.id}" }) { DownloadCard(it, callbacks, compact = true) }
    }
}
