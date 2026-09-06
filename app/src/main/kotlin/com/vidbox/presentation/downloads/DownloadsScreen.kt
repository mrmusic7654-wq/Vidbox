package com.vidbox.presentation.downloads

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.vidbox.domain.model.*
import com.vidbox.presentation.components.*

@Composable
fun DownloadsScreen(records: List<DownloadRecord>, notificationsAllowed: Boolean, maxConcurrent: Int,
    onNotifications: () -> Unit, onAddLink: () -> Unit, callbacks: DownloadCallbacks) {
    LazyColumn(Modifier.fillMaxSize().testTag("downloads_screen"), contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Downloads", style = MaterialTheme.typography.headlineLarge)
                Text("${records.count { it.state.isRunning }} active · Up to $maxConcurrent at once",
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        if (!notificationsAllowed) item { InfoBanner("Enable notifications to see background progress and controls.", action = "Enable", onAction = onNotifications) }
        if (records.isEmpty()) item {
            EmptyState(Icons.Rounded.CloudDownload, "Nothing in the queue", "Downloads continue here when you leave the app. Completed files move to your library.", action = "Add a link", onAction = onAddLink)
        } else {
            items(records, key = { it.id }) { DownloadCard(it, callbacks) }
            item { Text("Some sources cannot resume at the exact byte. Vidbox safely restarts those transfers instead of appending incompatible data.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 8.dp)) }
        }
    }
}
