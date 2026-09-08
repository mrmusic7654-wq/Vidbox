package com.vidbox.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vidbox.domain.model.*
import com.vidbox.domain.util.DisplayFormat

/** "32:05" / "1:03:58" style badge label, or null when the duration is unknown. */
fun durationBadgeLabel(seconds: Double?): String? {
    val total = seconds?.takeIf { it > 0 }?.toInt() ?: return null
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

@Composable
fun DurationBadge(seconds: Double?, modifier: Modifier = Modifier) {
    val label = durationBadgeLabel(seconds) ?: return
    Surface(color = Color.Black.copy(alpha = 0.72f), shape = RoundedCornerShape(6.dp), modifier = modifier) {
        Row(Modifier.padding(horizontal = 6.dp, vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Rounded.VideoFile, null, Modifier.size(13.dp), tint = Color.White)
            Spacer(Modifier.width(3.dp))
            Text(label, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/**
 * The compact media row used by Downloads, Video, Audio and the home screen:
 * thumbnail on the left (with a duration badge for known-length media), title,
 * a size/quality line, and a per-item overflow menu.
 */
@Composable
fun MediaRow(record: DownloadRecord, callbacks: DownloadCallbacks, modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null, showDuration: Boolean = true, tag: String? = null) {
    var menu by remember(record.id) { mutableStateOf(false) }
    val isFile = record.spec.kind == DownloadKind.FILE
    val isVideo = record.spec.selection.primary.hasVideo
    val playable = record.state == DownloadState.COMPLETED && !record.fileMissing && record.outputUri != null &&
        record.spec.kind == DownloadKind.MEDIA && isVideo
    val playableAudio = record.state == DownloadState.COMPLETED && !record.fileMissing && record.outputUri != null &&
        !isVideo
    Surface(
        onClick = { (onClick ?: { callbacks.details(record) })() },
        modifier = modifier.fillMaxWidth().let { base -> if (tag != null) base.testTag(tag) else base },
        shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 0.dp,
    ) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box {
                MediaThumbnail(record.spec.thumbnailUrl,
                    Modifier.size(if (isVideo && !isFile) 124.dp else 58.dp, if (isVideo && !isFile) 72.dp else 58.dp),
                    isVideo, file = isFile)
                if (showDuration && isVideo && !isFile) {
                    DurationBadge(record.spec.durationSeconds,
                        Modifier.align(Alignment.BottomEnd).padding(4.dp))
                }
            }
            Column(Modifier.weight(1f).padding(start = 12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(record.fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(if (isVideo && !isFile) VidboxIcons.video else VidboxIcons.audio, null,
                        Modifier.size(15.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(listOfNotNull(record.totalBytes?.let { DisplayFormat.bytes(it) },
                        if (!isFile) record.spec.selection.container.uppercase() else null)
                        .joinToString("  ·  "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (record.fileMissing || record.state == DownloadState.FAILED || record.state == DownloadState.CANCELLED) {
                        StatusPill(record)
                    }
                }
            }
            if (playable || playableAudio) {
                FilledTonalIconButton(onClick = { callbacks.play(record) }, modifier = Modifier.size(40.dp)) {
                    Icon(VidboxIcons.play, "Play ${record.fileName}", Modifier.size(20.dp))
                }
                Spacer(Modifier.width(2.dp))
            }
            Box {
                IconButton(onClick = { menu = true }, modifier = Modifier.size(40.dp)) {
                    Icon(VidboxIcons.menu, "Actions for ${record.fileName}")
                }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Details") }, leadingIcon = { Icon(VidboxIcons.info, null) },
                        onClick = { menu = false; callbacks.details(record) })
                    if (record.state == DownloadState.COMPLETED && !record.fileMissing && record.outputUri != null) {
                        DropdownMenuItem(text = { Text("Share file") }, leadingIcon = { Icon(VidboxIcons.share, null) },
                            onClick = { menu = false; callbacks.share(record) })
                        DropdownMenuItem(text = { Text("Open file") }, leadingIcon = { Icon(VidboxIcons.openExternally, null) },
                            onClick = { menu = false; callbacks.open(record) })
                    }
                    if (record.state.isTerminal) {
                        DropdownMenuItem(text = { Text("Analyze source again") }, leadingIcon = { Icon(VidboxIcons.link, null) },
                            onClick = { menu = false; callbacks.analyze(record) })
                        HorizontalDivider()
                        if (record.outputUri != null) DropdownMenuItem(
                            text = { Text("Delete file & entry", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(VidboxIcons.delete, null) },
                            onClick = { menu = false; callbacks.delete(record, true) })
                        DropdownMenuItem(text = { Text("Remove from history") }, leadingIcon = { Icon(VidboxIcons.history, null) },
                            onClick = { menu = false; callbacks.delete(record, false) })
                    }
                }
            }
        }
    }
}
