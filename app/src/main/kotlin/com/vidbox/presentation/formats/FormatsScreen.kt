package com.vidbox.presentation.formats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.vidbox.domain.model.FormatSelection
import com.vidbox.domain.model.MediaFormat
import com.vidbox.domain.util.DisplayFormat
import com.vidbox.presentation.components.*
import com.vidbox.presentation.home.HomeState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormatsScreen(state: HomeState, onBack: () -> Unit, onMode: (Boolean) -> Unit, onContainer: (String?) -> Unit,
    onSelect: (String) -> Unit, onDownload: () -> Unit, snackbarHost: SnackbarHostState) {
    val media = state.media ?: return
    val containers = state.options.filter { it.primary.hasVideo == state.video }
        .map { it.container }.distinct().sorted()
    BackHandler { if (!state.enqueueing) onBack() }
    Scaffold(containerColor = MaterialTheme.colorScheme.background,
        topBar = { TopAppBar(title = { Text("Choose your download", style = MaterialTheme.typography.titleMedium) },
            navigationIcon = { IconButton(onClick = onBack, enabled = !state.enqueueing) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to home") } },
            colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        snackbarHost = { SnackbarHost(snackbarHost) },
        bottomBar = {
            Surface(shadowElevation = 8.dp, color = MaterialTheme.colorScheme.surface) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    state.error?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error) }
                    state.selected?.let { selected ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${selected.primary.qualityLabel} · ${selected.container.uppercase()}", style = MaterialTheme.typography.labelLarge)
                            Text(sizeLabel(selected), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Button(onClick = onDownload, enabled = state.selected != null && !state.enqueueing,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("download_button"), shape = RoundedCornerShape(14.dp)) {
                        if (state.enqueueing) CircularProgressIndicator(Modifier.size(20.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
                        else Icon(Icons.Rounded.Download, null, Modifier.size(22.dp))
                        Spacer(Modifier.width(9.dp)); Text(if (state.enqueueing) "Adding to queue…" else "Download media")
                    }
                }
            }
        }) { insets ->
        LazyColumn(Modifier.fillMaxSize().padding(insets).testTag("formats_screen"), contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)) {
            item {
                Box(Modifier.fillMaxWidth()) {
                    MediaThumbnail(media.thumbnailUrl, Modifier.fillMaxWidth().heightIn(max = 240.dp).aspectRatio(16f / 9), pixels = 960)
                    media.durationSeconds?.let {
                        Surface(color = Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(6.dp), modifier = Modifier.align(Alignment.BottomEnd).padding(12.dp)) {
                            Text(DisplayFormat.duration(it.toLong()), Modifier.padding(horizontal = 8.dp, vertical = 4.dp), color = Color.White, style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(media.title, style = MaterialTheme.typography.headlineMedium, maxLines = 4, overflow = TextOverflow.Ellipsis)
                    Text(listOfNotNull(media.uploader, media.source).joinToString(" · "), style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (media.isDirect) InfoBanner("Original media file. This server does not report every codec or duration field.")
                }
            }
            item { HorizontalDivider(Modifier.padding(top = 6.dp)) }
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Quality & format", style = MaterialTheme.typography.titleLarge)
                    if (state.videoUnavailable) InfoBanner(
                        "This source's video is only available in codecs that can't be placed in MP4 without re-encoding, so Vidbox shows the audio options only.")
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        if (state.options.any { it.primary.hasVideo }) FilterChip(selected = state.video, onClick = { onMode(true) },
                            label = { Text("Video") }, leadingIcon = { Icon(Icons.Rounded.Movie, null, Modifier.size(18.dp)) })
                        if (state.options.any { !it.primary.hasVideo }) FilterChip(selected = !state.video, onClick = { onMode(false) },
                            label = { Text("Audio only") }, leadingIcon = { Icon(Icons.Rounded.MusicNote, null, Modifier.size(18.dp)) })
                    }
                    if (containers.size > 1) {
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(selected = state.container == null, onClick = { onContainer(null) }, label = { Text("All formats") })
                            containers.forEach { container ->
                                FilterChip(selected = state.container == container, onClick = { onContainer(container) }, label = { Text(container.uppercase()) })
                            }
                        }
                    }
                    val modeLabel = when {
                        state.video && containers == listOf("mp4") -> "MP4 video · only qualities this source offers · no re-encoding"
                        state.video -> "Original video file · saved as-is · no re-encoding"
                        else -> "Original audio formats · no re-encoding"
                    }
                    Text("${state.visibleOptions.size} available · $modeLabel", style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.visibleOptions, key = { it.key }) { option ->
                FormatCard(option, state.selectedKey == option.key, onSelect = { onSelect(option.key) })
            }
            if (state.selected?.requiresMerging == true) item {
                InfoBanner("Video and audio arrive separately. FFmpeg combines them on your device using stream copy, keeping the original quality.")
            }
        }
    }
}

@Composable
private fun FormatCard(option: FormatSelection, selected: Boolean, onSelect: () -> Unit) {
    OutlinedCard(onClick = onSelect, modifier = Modifier.fillMaxWidth().testTag("format_${option.key}"), shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.outlinedCardColors(containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f) else MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = selected, onClick = null, modifier = Modifier.padding(end = 14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(option.primary.qualityLabel, style = MaterialTheme.typography.titleMedium)
                        option.primary.fps?.let { Text("${if (it % 1.0 == 0.0) it.toInt().toString() else it.toString()} fps", style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                    Text(listOfNotNull(option.container.uppercase(), sizeLabel(option).takeIf { it.isNotEmpty() })
                        .joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (option.requiresMerging) Icon(Icons.Rounded.MergeType, "Merging required", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            }
            if (selected) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Column(Modifier.padding(start = 2.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    val primary = option.primary
                    if (primary.hasVideo) Text(videoLabel(primary), style = MaterialTheme.typography.bodySmall)
                    val audio = option.audio
                    when {
                        audio != null -> Text(audioLabel(audio), style = MaterialTheme.typography.bodySmall)
                        primary.hasAudio == false -> Text("No audio track in this stream", style = MaterialTheme.typography.bodySmall)
                        primary.hasAudio == true -> Text(if (primary.audioCodec != null) "Audio included · ${primary.audioCodec}" else "Audio included", style = MaterialTheme.typography.bodySmall)
                        else -> Text("Audio track", style = MaterialTheme.typography.bodySmall)
                    }
                    Text(when {
                        option.requiresMerging -> "Video + audio · stream copy merge"
                        !primary.hasVideo -> "Audio only · no merging needed"
                        primary.hasAudio == true -> "Video + audio · no merging needed"
                        primary.hasAudio == false -> "Video only · no audio track"
                        else -> "Video + audio"
                    }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (option.requiresMerging) Text("Audio is downloaded separately; it is muxed on device without re-encoding.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

private fun videoLabel(format: MediaFormat): String {
    val parts = buildList {
        format.videoCodec?.let { add("Codec $it") }
        format.width?.let { w -> format.height?.let { h -> add("$w × $h") } }
    }
    return if (parts.isEmpty()) "Video stream" else parts.joinToString(" · ")
}

private fun audioLabel(format: MediaFormat): String {
    val parts = buildList {
        format.audioCodec?.let { add("Codec $it") }
        format.bitrateKbps?.takeIf { it > 0 }?.let { add("${it.toInt()} kbps") }
    }
    return if (parts.isEmpty()) "Separate audio track" else "Separate audio · ${parts.joinToString(" · ")}"
}

private fun sizeLabel(option: FormatSelection): String =
    if (option.approximateSize && option.estimatedBytes != null) "≈ ${DisplayFormat.bytes(option.estimatedBytes)}"
    else option.estimatedBytes?.let { DisplayFormat.bytes(it) }.orEmpty()
