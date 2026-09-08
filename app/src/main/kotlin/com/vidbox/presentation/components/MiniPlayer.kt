package com.vidbox.presentation.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.vidbox.player.PlayerUiState

/**
 * The docked mini player: stays above the bottom navigation (and the browser bar) for as
 * long as something is playing, exactly one tap away from the full advanced player.
 */
@Composable
fun MiniPlayer(state: PlayerUiState, onToggle: () -> Unit, onClose: () -> Unit, onOpen: () -> Unit,
    modifier: Modifier = Modifier) {
    Surface(shape = RoundedCornerShape(18.dp), tonalElevation = 6.dp, shadowElevation = 10.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
            .testTag("mini_player").clickable(onClick = onOpen)) {
        Column {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    if (state.thumbnail != null) {
                        AsyncImage(model = ImageRequest.Builder(LocalContext.current).data(state.thumbnail)
                            .size(128).crossfade(true).build(), contentDescription = null,
                            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(if (state.isAudio) VidboxIcons.audio else VidboxIcons.video, null,
                            Modifier.size(22.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
                Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                    Text(state.title ?: "Playing", style = MaterialTheme.typography.titleSmall,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(if (state.isAudio) "Audio" else "Video", style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onToggle, modifier = Modifier.size(42.dp).testTag("mini_player_toggle")) {
                    Icon(if (state.playing) VidboxIcons.pause else VidboxIcons.play,
                        if (state.playing) "Pause" else "Play")
                }
                IconButton(onClick = onClose, modifier = Modifier.size(38.dp).testTag("mini_player_close")) {
                    Icon(VidboxIcons.cancel, "Stop playback")
                }
            }
            if (state.durationMs > 0) {
                LinearProgressIndicator(
                    progress = { (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    trackColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        }
    }
}
