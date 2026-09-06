package com.vidbox.player

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Toast
import android.widget.VideoView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vidbox.domain.model.AppTheme
import com.vidbox.domain.util.DisplayFormat
import com.vidbox.presentation.theme.VidboxTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Built-in video player: plays completed Vidbox downloads (content:// media from
 * MediaStore or a user-chosen folder) without leaving the app. If the device cannot
 * decode the container, the screen offers opening the file in an external app instead.
 */
class PlayerActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        val uri = intent.getStringExtra(EXTRA_URI)?.let(Uri::parse)
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Playback"
        val mime = intent.getStringExtra(EXTRA_MIME)
        if (uri == null) { finish(); return }
        setContent {
            VidboxTheme(AppTheme.SYSTEM) { PlayerScreen(this@PlayerActivity, uri, title, mime) }
        }
    }

    companion object {
        private const val EXTRA_URI = "uri"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_MIME = "mime"

        fun intent(context: Context, uri: Uri, title: String, mime: String?): Intent =
            Intent(context, PlayerActivity::class.java)
                .putExtra(EXTRA_URI, uri.toString())
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_MIME, mime)
    }
}

@Composable
private fun PlayerScreen(activity: Activity, uri: Uri, title: String, mime: String?) {
    val context = LocalContext.current
    var player by remember { mutableStateOf<VideoView?>(null) }
    var prepared by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(false) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var seeking by remember { mutableStateOf(false) }
    var seekTarget by remember { mutableStateOf(0L) }
    var pausedForLifecycle by remember { mutableStateOf(false) }
    // Rotations and process recreation resume where the user left off.
    var resumeAtMs by rememberSaveable { mutableStateOf(0L) }

    // Pause audio when the app leaves the foreground; resume it when it returns.
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> if (playing) {
                    player?.pause(); pausedForLifecycle = true
                }
                Lifecycle.Event.ON_RESUME -> if (pausedForLifecycle) {
                    player?.start(); pausedForLifecycle = false
                }
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    fun openExternally() {
        try {
            val external = Intent(Intent.ACTION_VIEW).setDataAndType(uri, mime ?: "video/*")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(external, "Open with"))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No app can play this file type.", Toast.LENGTH_SHORT).show()
        } catch (_: SecurityException) {
            Toast.makeText(context, "The saved file is no longer accessible.", Toast.LENGTH_SHORT).show()
        }
    }

    // Position ticker while playing; keeps the seek bar (and the rotation restore point) fresh.
    LaunchedEffect(playing, prepared) {
        while (isActive && playing && prepared && !failed) {
            val current = player?.currentPosition?.toLong() ?: 0L
            if (current > 0) { positionMs = current; resumeAtMs = current }
            delay(500)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black).testTag("player_screen")) {
        AndroidView(
            factory = { viewContext ->
                val created = VideoView(viewContext)
                created.layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                created.setOnPreparedListener { mp ->
                    val total = mp.duration.coerceAtLeast(0).toLong()
                    durationMs = total
                    if (resumeAtMs > 0 && total > 0) mp.seekTo(resumeAtMs.coerceAtMost(total).toInt())
                    prepared = true
                    failed = false
                    mp.isLooping = false
                    mp.start()
                    playing = true
                }
                created.setOnErrorListener { _, what, extra ->
                    // MEDIA_ERROR_UNKNOWN + MEDIA_ERROR_IO also fires when the source is
                    // interrupted by a stop; only surface real playback failures.
                    val realFailure = what != MediaPlayer.MEDIA_ERROR_UNKNOWN || extra != MediaPlayer.MEDIA_ERROR_IO
                    if (prepared && !failed) {
                        failed = realFailure
                        playing = false
                    } else if (realFailure) {
                        failed = true
                    }
                    true
                }
                created.setOnCompletionListener {
                    playing = false
                    positionMs = durationMs
                }
                created.setVideoURI(uri)
                player = created
                created
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top bar: back + name + external open.
        Row(Modifier.fillMaxWidth().background(Color.Black.copy(alpha = 0.45f)).padding(horizontal = 6.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { activity.finish() }, modifier = Modifier.testTag("player_back")) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back to Vidbox", tint = Color.White)
            }
            Text(title, style = MaterialTheme.typography.titleSmall, color = Color.White,
                maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            IconButton(onClick = { openExternally() }, modifier = Modifier.testTag("player_external")) {
                Icon(Icons.Rounded.OpenInNew, "Open in another app", tint = Color.White)
            }
        }

        // Bottom controls.
        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.55f)).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (failed) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Rounded.ErrorOutline, null, tint = Color.White)
                    Text("This device cannot play this file format.", color = Color.White,
                        style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                }
            } else if (prepared && durationMs > 0) {
                Slider(
                    value = (if (seeking) seekTarget else positionMs).toFloat().coerceIn(0f, durationMs.toFloat()),
                    onValueChange = { value -> seeking = true; seekTarget = value.toLong() },
                    onValueChangeFinished = {
                        seeking = false
                        player?.seekTo(seekTarget.toInt())
                        positionMs = seekTarget
                        resumeAtMs = seekTarget
                        if (!playing) { player?.start(); playing = true }
                    },
                    valueRange = 0f..durationMs.toFloat().coerceAtLeast(1f),
                    modifier = Modifier.fillMaxWidth().testTag("player_seek"))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(DisplayFormat.duration(((if (seeking) seekTarget else positionMs) / 1000).coerceAtLeast(0)),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                    Text(DisplayFormat.duration((durationMs / 1000).coerceAtLeast(0)),
                        style = MaterialTheme.typography.labelSmall, color = Color.White)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                if (failed) {
                    Button(onClick = { openExternally() }) {
                        Icon(Icons.Rounded.OpenInNew, null, Modifier.size(18.dp)); Spacer(Modifier.width(8.dp))
                        Text("Open in another app")
                    }
                } else if (prepared) {
                    FilledTonalIconButton(onClick = {
                        val current = player ?: return@FilledTonalIconButton
                        if (playing) { current.pause(); playing = false }
                        else { current.start(); playing = true }
                    }, modifier = Modifier.testTag("player_play_pause")) {
                        Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, if (playing) "Pause" else "Play")
                    }
                }
            }
        }

        if (!prepared && !failed) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center).testTag("player_buffering"))
        }
    }
}
