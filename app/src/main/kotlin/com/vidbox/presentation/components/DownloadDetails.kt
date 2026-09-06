package com.vidbox.presentation.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vidbox.domain.model.DownloadKind
import com.vidbox.domain.model.DownloadRecord
import com.vidbox.domain.model.DownloadState
import com.vidbox.domain.util.DisplayFormat

@Composable
fun DownloadDetails(record: DownloadRecord, onDismiss: () -> Unit, onPlay: (() -> Unit)? = null) {
    val playableVideo = onPlay != null && record.state == DownloadState.COMPLETED && !record.fileMissing &&
        record.spec.kind == DownloadKind.MEDIA && record.spec.selection.primary.hasVideo && record.outputUri != null
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Download details") },
        confirmButton = {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                if (playableVideo) {
                    TextButton(onClick = { onDismiss(); onPlay?.invoke() }) {
                        Icon(VidboxIcons.play, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp))
                        Text("Play in Vidbox")
                    }
                }
                TextButton(onClick = onDismiss) { Text("Done") }
            }
        },
        text = {
            SelectionContainer {
                Column(Modifier.heightIn(max = 480.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    val file = record.spec.kind == DownloadKind.FILE
                    DetailLine("Filename", record.fileName)
                    DetailLine("Status", stateLabel(record))
                    record.error?.let { DetailLine("What happened", it.message) }
                    DetailLine("Source", record.spec.source)
                    DetailLine("Original link", record.spec.url)
                    DetailLine(if (file) "File type" else "Format",
                        if (file) record.mimeType ?: "${record.spec.selection.container.uppercase()} file"
                        else "${record.spec.selection.primary.qualityLabel} · ${record.spec.selection.container.uppercase()}")
                    if (!file) {
                        DetailLine("Format IDs", listOfNotNull(record.spec.selection.primary.id, record.spec.selection.audio?.id).joinToString(" + "))
                        DetailLine("Video codec", record.spec.selection.primary.videoCodec ?: if (record.spec.selection.primary.hasVideo) "—" else "No video")
                        DetailLine("Audio codec", record.spec.selection.audio?.audioCodec ?: record.spec.selection.primary.audioCodec
                            ?: if (record.spec.selection.primary.hasAudio == false) "No audio" else "—")
                        DetailLine("Merging", if (record.spec.selection.requiresMerging) "Separate video + audio · stream copy" else "Not required")
                    }
                    DetailLine("Duration", DisplayFormat.duration(record.spec.durationSeconds?.toLong()))
                    DetailLine("File size", DisplayFormat.bytes(record.totalBytes))
                    DetailLine("Transferred", DisplayFormat.bytes(record.downloadedBytes))
                    DetailLine("Created", dateLabel(record.createdAt))
                    record.startedAt?.let { DetailLine("Started", dateLabel(it)) }
                    record.completedAt?.let { DetailLine("Finished", dateLabel(it)) }
                    DetailLine("Attempts", record.attempt.toString())
                    DetailLine("Resume support", if (record.resumeSupported) "Can continue when supported by the source" else "Server has not provided stable range validators; interruptions restart safely")
                    record.outputUri?.let { DetailLine("Saved location", it) }
                    DetailLine("Download ID", record.id)
                }
            }
        })
}

@Composable
private fun DetailLine(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
