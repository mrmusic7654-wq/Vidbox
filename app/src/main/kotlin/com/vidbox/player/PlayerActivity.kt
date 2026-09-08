package com.vidbox.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.Fullscreen
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.vidbox.domain.model.AppTheme
import com.vidbox.domain.util.DisplayFormat
import com.vidbox.presentation.components.VidboxIcons
import com.vidbox.presentation.theme.VidboxTheme
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * The advanced player. It plays whatever the app-scoped [PlayerController] has queued, so
 * the docked mini player and this screen are two views of one continuous session:
 * double-tap to skip ±10 s, drag to scrub, drag the left edge for brightness and the right
 * edge for volume, plus playback speed, resize modes, control lock, and fullscreen.
 */
@AndroidEntryPoint
class PlayerActivity : ComponentActivity() {
    @Inject lateinit var controller: PlayerController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (!controller.state.value.active) { finish(); return }
        setContent { VidboxTheme(AppTheme.SYSTEM) { PlayerScreen(this, controller) } }
    }

    companion object {
        fun start(context: Context) {
            context.startActivity(Intent(context, PlayerActivity::class.java))
        }
    }
}

@Composable
private fun PlayerScreen(activity: Activity, controller: PlayerController) {
    val state by controller.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    val window = activity.window

    var controlsVisible by remember { mutableStateOf(true) }
    var locked by rememberSaveable { mutableStateOf(false) }
    var landscape by rememberSaveable { mutableStateOf(false) }
    var resizeIndex by rememberSaveable { mutableIntStateOf(0) }
    var speedMenu by remember { mutableStateOf(false) }
    var scrubTargetMs by remember { mutableStateOf<Long?>(null) }
    var gestureHint by remember { mutableStateOf<String?>(null) }

    val resizeModes = listOf(
        AspectRatioFrameLayout.RESIZE_MODE_FIT,
        AspectRatioFrameLayout.RESIZE_MODE_FILL,
        AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    )

    fun openExternally() {
        val uri = controller.exoPlayer?.currentMediaItem?.localConfiguration?.uri ?: return
        try {
            context.startActivity(Intent.createChooser(Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, if (state.isAudio) "audio/*" else "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Open with"))
        } catch (_: ActivityNotFoundException) {
            android.widget.Toast.makeText(context, "No app can play this file type.", android.widget.Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            android.widget.Toast.makeText(context, "The saved file is no longer accessible.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    // Orientation, system bars, and auto-hide behave like a proper video app.
    DisposableEffect(landscape) {
        activity.requestedOrientation = if (landscape)
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(controlsVisible, locked) {
        val insets = WindowCompat.getInsetsController(window, view)
        insets.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (!controlsVisible) insets.hide(WindowInsetsCompat.Type.systemBars())
        else insets.show(WindowInsetsCompat.Type.systemBars())
        onDispose { }
    }
    LaunchedEffect(controlsVisible, state.playing, locked) {
        if (controlsVisible && state.playing && !locked) {
            delay(3_500); controlsVisible = false
        }
    }
    // Gestures must never leak brightness changes into the rest of the app.
    DisposableEffect(Unit) {
        val original = window.attributes.screenBrightness
        onDispose { window.attributes = window.attributes.apply { screenBrightness = original } }
    }
    BackHandler {
        if (locked) { locked = false; controlsVisible = true } else activity.finish()
    }
    LaunchedEffect(state.active) { if (!state.active) activity.finish() }

    fun hint(text: String) { gestureHint = text }
    fun clearHint() { gestureHint = null }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("player_screen")
        .pointerInput(locked) {
            detectTapGestures(
                onTap = { controlsVisible = if (locked) true else !controlsVisible },
                onDoubleTap = { offset ->
                    if (locked) return@detectTapGestures
                    if (offset.x < size.width / 2f) {
                        controller.rewind(); hint("⟨ 10 seconds")
                    } else {
                        controller.forward(); hint("10 seconds ⟩")
                    }
                    launch { delay(700); gestureHint = null }
                })
        }
        .pointerInput(locked, state.durationMs) {
            var scrubFrom = 0f
            detectHorizontalDragGestures(
                onDragStart = { if (!locked) { scrubFrom = (scrubTargetMs ?: state.positionMs).toFloat(); controlsVisible = true } },
                onDragEnd = { scrubTargetMs?.let { controller.seekTo(it) }; scrubTargetMs = null },
                onDragCancel = { scrubTargetMs = null },
            ) { change, amount ->
                if (locked || state.durationMs <= 0) return@detectHorizontalDragGestures
                change.consume()
                val total = state.durationMs.toFloat()
                val next = (scrubFrom + amount * (total / size.width) * 1.4f).coerceIn(0f, total)
                scrubTargetMs = next.toLong()
                hint("${DisplayFormat.duration((next.toLong() / 1000))} / ${DisplayFormat.duration((state.durationMs / 1000))}")
            }
        }
        .pointerInput(locked) {
            var start = 0f
            var base = 0f
            val audio = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val maxVolume = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
            detectVerticalDragGestures(
                onDragStart = { offset -> if (!locked) { start = offset.y; base = if (offset.x < size.width / 2f)
                    window.attributes.screenBrightness.takeIf { it >= 0 } ?: 0.5f
                    else audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() } },
                onDragEnd = { clearHint() },
                onDragCancel = { clearHint() },
            ) { change, amount ->
                if (locked) return@detectVerticalDragGestures
                change.consume()
                val fraction = (start - change.position.y) / size.height
                if (change.position.x < size.width / 2f) {
                    val brightness = (base + fraction).coerceIn(0.01f, 1f)
                    window.attributes = window.attributes.apply { screenBrightness = brightness }
                    hint("Brightness ${"%.0f".format(brightness * 100)}%")
                } else {
                    val target = (base + fraction * maxVolume).toInt().coerceIn(0, maxVolume)
                    audio.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                    hint("Volume ${"%.0f".format(target.toFloat() / maxVolume * 100)}%")
                }
            }
        }) {

        // Video surface (audio plays without one).
        if (!state.isAudio && state.error == null) {
            AndroidView(
                factory = { viewContext ->
                    PlayerView(viewContext).apply { useController = false }
                },
                update = { surface ->
                    surface.player = controller.exoPlayer
                    surface.resizeMode = resizeModes[resizeIndex.coerceIn(0, resizeModes.lastIndex)]
                },
                onRelease = { surface -> surface.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(
                listOf(Color(0xFF241B0C), Color(0xFF0E0C08)))), contentAlignment = Alignment.Center) {
                Icon(if (state.isAudio) VidboxIcons.audio else VidboxIcons.error, null,
                    Modifier.size(96.dp), tint = Color.White.copy(alpha = 0.55f))
            }
        }

        // Gesture hint bubble (seek preview, brightness, volume).
        gestureHint?.let { text ->
            Box(Modifier.align(Alignment.Center), contentAlignment = Alignment.Center) {
                Surface(color = Color.Black.copy(alpha = 0.75f), shape = RoundedCornerShape(12.dp)) {
                    Text(text, Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        color = Color.White, style = MaterialTheme.typography.titleMedium)
                }
            }
        }

        if (state.error != null) {
            Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(VidboxIcons.error, null, Modifier.size(40.dp), tint = Color.White)
                Spacer(Modifier.height(12.dp))
                Text(state.error.orEmpty(), color = Color.White, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                Row {
                    OutlinedButton(onClick = ::openExternally) {
                        Icon(VidboxIcons.openExternally, null, Modifier.size(17.dp), tint = Color.White)
                        Spacer(Modifier.width(7.dp))
                        Text("Open in another app", color = Color.White)
                    }
                    Spacer(Modifier.width(10.dp))
                    OutlinedButton(onClick = { controller.close() }) { Text("Close player", color = Color.White) }
                }
            }
        } else if (state.durationMs == 0L && state.positionMs == 0L) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).testTag("player_buffering"))
        }

        if (locked) {
            FilledTonalIconButton(onClick = { locked = false; controlsVisible = true },
                modifier = Modifier.align(Alignment.Center).testTag("player_unlock")) {
                Icon(VidboxIcons.insecure, "Unlock controls")
            }
        } else if (controlsVisible && state.error == null) {
            // Top bar ------------------------------------------------------------------
            Row(Modifier.align(Alignment.TopCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { activity.finish() }, modifier = Modifier.testTag("player_back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Vidbox", tint = Color.White)
                }
                Column(Modifier.weight(1f)) {
                    Text(state.title ?: "Playback", style = MaterialTheme.typography.titleSmall,
                        color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (state.queueSize > 1) {
                        Text("${state.queueIndex + 1} of ${state.queueSize}", style = MaterialTheme.typography.labelSmall,
                            color = Color.White.copy(alpha = 0.7f))
                    }
                }
                Box {
                    IconButton(onClick = { speedMenu = true }) {
                        Text("${state.speed}×", color = Color.White, style = MaterialTheme.typography.labelLarge)
                    }
                    DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                        listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { speed ->
                            DropdownMenuItem(text = { Text("${speed}×" + if (speed == state.speed) "  ✓" else "") },
                                onClick = { controller.setSpeed(speed); speedMenu = false })
                        }
                    }
                }
                IconButton(onClick = { locked = true; controlsVisible = true }) {
                    Icon(VidboxIcons.secure, "Lock controls", tint = Color.White)
                }
                IconButton(onClick = ::openExternally, modifier = Modifier.testTag("player_external")) {
                    Icon(VidboxIcons.openExternally, "Open in another app", tint = Color.White)
                }
            }

            // Center transport ---------------------------------------------------------
            Row(Modifier.align(Alignment.Center), horizontalArrangement = Arrangement.spacedBy(22.dp),
                verticalAlignment = Alignment.CenterVertically) {
                if (state.queueSize > 1) {
                    FilledTonalIconButton(onClick = { controller.previous() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)) {
                        Icon(Icons.Rounded.SkipPrevious, "Previous")
                    }
                }
                FilledIconButton(onClick = { controller.togglePlayPause() },
                    modifier = Modifier.size(74.dp).testTag("player_play_pause"),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color.White.copy(alpha = 0.20f), contentColor = Color.White)) {
                    Icon(if (state.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        if (state.playing) "Pause" else "Play", Modifier.size(38.dp))
                }
                if (state.queueSize > 1) {
                    FilledTonalIconButton(onClick = { controller.next() },
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = Color.White.copy(alpha = 0.18f), contentColor = Color.White)) {
                        Icon(Icons.Rounded.SkipNext, "Next")
                    }
                }
            }

            // Bottom bar ---------------------------------------------------------------
            Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 12.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.durationMs > 0) {
                    Slider(
                        value = (scrubTargetMs ?: state.positionMs).toFloat().coerceIn(0f, state.durationMs.toFloat()),
                        onValueChange = { scrubTargetMs = it.toLong() },
                        onValueChangeFinished = { scrubTargetMs?.let { target -> controller.seekTo(target) }; scrubTargetMs = null },
                        valueRange = 0f..state.durationMs.toFloat().coerceAtLeast(1f),
                        modifier = Modifier.fillMaxWidth().testTag("player_seek"),
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White, activeTrackColor = Color.White, inactiveTrackColor = Color.White.copy(alpha = 0.30f)))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(DisplayFormat.duration((scrubTargetMs ?: state.positionMs) / 1000),
                            style = MaterialTheme.typography.labelSmall, color = Color.White)
                        Text(DisplayFormat.duration(state.durationMs / 1000),
                            style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.75f))
                    }
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween) {
                    Row {
                        IconButton(onClick = { resizeIndex = (resizeIndex + 1) % resizeModes.size }) {
                            Icon(Icons.Rounded.AspectRatio, "Fit, fill or zoom the video", tint = Color.White)
                        }
                        IconButton(onClick = { landscape = !landscape }) {
                            Icon(Icons.Rounded.Fullscreen, "Toggle fullscreen rotation", tint = Color.White)
                        }
                    }
                    Icon(Icons.Rounded.VolumeUp, null, Modifier.size(18.dp), tint = Color.White.copy(alpha = 0.65f))
                }
            }
        }
    }
}
