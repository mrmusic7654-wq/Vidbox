package com.vidbox.presentation.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidbox.domain.model.*
import com.vidbox.domain.util.DisplayFormat

data class DownloadCallbacks(
    val pause: (DownloadRecord) -> Unit,
    val resume: (DownloadRecord) -> Unit,
    val cancel: (DownloadRecord) -> Unit,
    val open: (DownloadRecord) -> Unit,
    val share: (DownloadRecord) -> Unit,
    val details: (DownloadRecord) -> Unit,
    val delete: (DownloadRecord, Boolean) -> Unit,
    val analyze: (DownloadRecord) -> Unit,
    /** Plays a completed video in Vidbox's built-in player (external apps stay in the menu). */
    val play: (DownloadRecord) -> Unit = {},
)

@Composable
fun DownloadCard(record: DownloadRecord, callbacks: DownloadCallbacks, compact: Boolean = false) {
    var menu by remember(record.id) { mutableStateOf(false) }
    OutlinedCard(onClick = { callbacks.details(record) }, modifier = Modifier.fillMaxWidth().testTag("download_${record.id}"),
        shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                val file = record.spec.kind == DownloadKind.FILE
                MediaThumbnail(record.spec.thumbnailUrl, Modifier.size(if (compact) 62.dp else 72.dp, if (compact) 58.dp else 68.dp),
                    record.spec.selection.primary.hasVideo, file = file)
                Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(record.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    val label = if (file) record.spec.selection.container.uppercase()
                        else "${record.spec.selection.primary.qualityLabel} · ${record.spec.selection.container.uppercase()}"
                    Text(listOfNotNull(label,
                            record.totalBytes?.let { DisplayFormat.bytes(it) }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (record.state.isTerminal) Text(dateLabel(record.completedAt ?: record.createdAt), style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box {
                    IconButton(onClick = { menu = true }, modifier = Modifier.size(48.dp).offset(x = 8.dp, y = (-8).dp)) {
                        Icon(Icons.Rounded.MoreVert, "Actions for ${record.fileName}")
                    }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(text = { Text("Download details") }, leadingIcon = { Icon(Icons.Rounded.Info, null) },
                            onClick = { menu = false; callbacks.details(record) })
                        if (record.state == DownloadState.COMPLETED) {
                            DropdownMenuItem(text = { Text("Open file") }, enabled = !record.fileMissing, leadingIcon = { Icon(Icons.Rounded.OpenInNew, null) },
                                onClick = { menu = false; callbacks.open(record) })
                            DropdownMenuItem(text = { Text("Share file") }, enabled = !record.fileMissing, leadingIcon = { Icon(Icons.Rounded.Share, null) },
                                onClick = { menu = false; callbacks.share(record) })
                        }
                        if (record.state.isTerminal) {
                            DropdownMenuItem(text = { Text("Analyze source again") }, leadingIcon = { Icon(Icons.Rounded.Link, null) },
                                onClick = { menu = false; callbacks.analyze(record) })
                            HorizontalDivider()
                            if (record.outputUri != null) DropdownMenuItem(text = { Text("Delete file & entry", color = MaterialTheme.colorScheme.error) },
                                leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) }, onClick = { menu = false; callbacks.delete(record, true) })
                            DropdownMenuItem(text = { Text("Remove from history") }, leadingIcon = { Icon(Icons.Rounded.History, null) },
                                onClick = { menu = false; callbacks.delete(record, false) })
                        }
                    }
                }
            }
            if (!record.state.isTerminal && !compact) {
                when {
                    record.state == DownloadState.DOWNLOADING && record.percent != null -> LinearProgressIndicator(
                        progress = { (record.percent!! / 100).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(5.dp))
                    record.state.isRunning -> LinearProgressIndicator(modifier = Modifier.fillMaxWidth().height(5.dp))
                    record.percent != null -> LinearProgressIndicator(progress = { record.percent!! / 100 }, modifier = Modifier.fillMaxWidth().height(5.dp))
                    else -> Box(Modifier.fillMaxWidth().height(5.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp)))
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (record.state == DownloadState.PROCESSING) "Stream copy / storage" else
                        "${DisplayFormat.bytes(record.downloadedBytes)}${record.totalBytes?.let { " / ${DisplayFormat.bytes(it)}" }.orEmpty()}",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (record.state == DownloadState.DOWNLOADING) Text("${record.percent?.let { "${it.toInt()}% · " }.orEmpty()}${DisplayFormat.bytes(record.speedBytesPerSecond)}/s",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
                record.etaSeconds?.takeIf { record.state == DownloadState.DOWNLOADING }?.let {
                    Text("${DisplayFormat.duration(it)} remaining", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (record.state == DownloadState.FAILED && !compact) record.error?.let {
                Text(it.message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) { StatusPill(record) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (record.state == DownloadState.COMPLETED && !record.fileMissing) {
                        val playableVideo = record.spec.kind == DownloadKind.MEDIA &&
                            record.spec.selection.primary.hasVideo && record.outputUri != null
                        IconButton(onClick = { callbacks.share(record) }) { Icon(Icons.Rounded.Share, "Share ${record.fileName}", modifier = Modifier.size(20.dp)) }
                        if (playableVideo) {
                            FilledTonalIconButton(onClick = { callbacks.play(record) }) {
                                Icon(Icons.Rounded.PlayArrow, "Play ${record.fileName} in Vidbox")
                            }
                        } else {
                            FilledTonalIconButton(onClick = { callbacks.open(record) }) { Icon(Icons.Rounded.OpenInNew, "Open ${record.fileName}") }
                        }
                    } else if (!compact) {
                        if (record.canPause) IconButton(onClick = { callbacks.pause(record) }) { Icon(Icons.Rounded.Pause, "Pause download") }
                        else if (record.state == DownloadState.PAUSED) FilledTonalIconButton(onClick = { callbacks.resume(record) }) { Icon(Icons.Rounded.PlayArrow, "Resume download") }
                        if (record.state == DownloadState.FAILED || record.state == DownloadState.CANCELLED) {
                            TextButton(onClick = { if (record.error?.retryable != false) callbacks.resume(record) else callbacks.analyze(record) }) {
                                Icon(Icons.Rounded.Refresh, null, Modifier.size(18.dp)); Spacer(Modifier.width(5.dp))
                                Text(if (record.error?.retryable != false) "Retry" else "Re-analyze")
                            }
                        } else if (!record.state.isTerminal) IconButton(onClick = { callbacks.cancel(record) }) { Icon(Icons.Rounded.Close, "Cancel download") }
                    }
                }
            }
        }
    }
}
