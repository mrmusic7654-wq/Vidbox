package com.vidbox.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vidbox.domain.model.*
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun BrandMark(size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size / 3)).background(MaterialTheme.colorScheme.primary)
        .semantics { contentDescription = "Vidbox logo" }, contentAlignment = Alignment.Center) {
        Icon(VidboxIcons.play, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(size * 0.72f).offset(y = (-2).dp))
        Box(Modifier.align(Alignment.BottomCenter).padding(bottom = size * 0.18f).size(size * 0.4f, 2.dp)
            .background(MaterialTheme.colorScheme.onPrimary, CircleShape))
    }
}

@Composable
fun MediaThumbnail(url: String?, modifier: Modifier = Modifier, video: Boolean = true, file: Boolean = false, pixels: Int = 256) {
    Box(modifier.clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
        Icon(when {
            file -> VidboxIcons.file
            video -> VidboxIcons.video
            else -> VidboxIcons.audio
        }, null, tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f), modifier = Modifier.size(30.dp))
        if (url != null) AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(url).size(pixels).crossfade(true).build(),
            contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
    }
}

@Composable
fun SectionHeading(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier,
    action: String? = null, onAction: () -> Unit = {}) {
    Column(modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(28.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(22.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) { Spacer(Modifier.height(20.dp)); FilledTonalButton(onClick = onAction) { Text(action) } }
    }
}

@Composable
fun InfoBanner(text: String, error: Boolean = false, modifier: Modifier = Modifier, action: String? = null,
    onAction: () -> Unit = {}) {
    val background = if (error) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer
    val foreground = if (error) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(14.dp), color = background, modifier = modifier) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(if (error) VidboxIcons.error else VidboxIcons.info, null, Modifier.size(20.dp), tint = foreground)
            Text(text, color = foreground, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f).padding(start = 10.dp))
            if (action != null) TextButton(onClick = onAction) { Text(action, color = foreground) }
        }
    }
}

fun stateLabel(record: DownloadRecord): String = when (record.state) {
    DownloadState.QUEUED -> "Queued"
    DownloadState.EXTRACTING -> "Resolving format"
    DownloadState.DOWNLOADING -> "Downloading"
    DownloadState.PROCESSING -> "Finishing & saving"
    DownloadState.COMPLETED -> if (record.fileMissing) "File unavailable" else "Saved"
    DownloadState.FAILED -> "Needs attention"
    DownloadState.CANCELLED -> "Cancelled"
    DownloadState.PAUSED -> when (record.pauseReason) {
        PauseReason.NETWORK -> "Waiting for connection"
        PauseReason.WIFI -> "Waiting for Wi-Fi"
        PauseReason.SYSTEM -> "Paused by Android"
        else -> "Paused"
    }
}

@Composable
fun StatusPill(record: DownloadRecord) {
    val color = when {
        record.state == DownloadState.FAILED || record.fileMissing -> MaterialTheme.colorScheme.error
        record.state == DownloadState.CANCELLED -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.primary
    }
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(6.dp)) {
        Text(stateLabel(record), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = color,
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

fun dateLabel(timestamp: Long): String = DateTimeFormatter.ofPattern("MMM d, yyyy · HH:mm", Locale.getDefault())
    .format(Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()))
