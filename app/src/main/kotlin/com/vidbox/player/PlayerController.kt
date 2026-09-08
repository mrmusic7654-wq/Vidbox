package com.vidbox.player

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.vidbox.di.ApplicationScope
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class PlayerUiState(
    val active: Boolean = false,
    val playing: Boolean = false,
    val title: String? = null,
    val thumbnail: String? = null,
    val isAudio: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long = 0,
    val speed: Float = 1f,
    val queueIndex: Int = 0,
    val queueSize: Int = 0,
    val error: String? = null,
)

/** One playable entry: a completed Vidbox download identified by its MediaStore URI. */
data class PlayerEntry(
    val uri: String,
    val title: String,
    val mimeType: String?,
    val thumbnail: String? = null,
) {
    val isAudio: Boolean get() = mimeType?.startsWith("audio/") == true
}

/**
 * App-scoped playback: the same ExoPlayer instance powers the full advanced player and
 * the docked mini player, so browsing tabs while media plays just works without a media
 * foreground service. Playback pauses when the whole app leaves the foreground.
 */
@Singleton
class PlayerController @Inject constructor(
    @ApplicationContext private val context: Context,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = mutableState.asStateFlow()

    private var player: ExoPlayer? = null
    private var queue: List<PlayerEntry> = emptyList()
    private var index: Int = 0
    private var ticker: Job? = null
    private var listener: Player.Listener? = null

    /** The live ExoPlayer for surfaces (PlayerView) to attach to; null until playback starts. */
    val exoPlayer: ExoPlayer? get() = player

    suspend fun play(entries: List<PlayerEntry>, startIndex: Int) = withContext(Dispatchers.Main) {
        val playable = entries.filter { it.uri.isNotBlank() }
        if (playable.isEmpty()) return@withContext
        queue = playable
        index = startIndex.coerceIn(0, playable.lastIndex)
        val engine = ensurePlayer()
        engine.setMediaItems(playable.map { MediaItem.fromUri(it.uri })
            .mapIndexed { position, item -> item.buildUpon().setTag(playable[position]).build() })
        engine.seekToDefaultPosition(index)
        engine.prepare()
        engine.playWhenReady = true
        publishCurrent()
    }

    fun togglePlayPause() = runOnEngine { engine ->
        if (engine.isPlaying) engine.pause() else engine.play()
    }

    fun pause() = runOnEngine { engine -> if (engine.isPlaying) engine.pause() }

    fun seekTo(positionMs: Long) = runOnEngine { engine -> engine.seekTo(positionMs.coerceAtLeast(0)) }

    fun rewind() = runOnEngine { engine -> engine.seekTo((engine.currentPosition - 10_000).coerceAtLeast(0)) }

    fun forward() = runOnEngine { engine -> engine.seekTo(engine.currentPosition + 10_000) }

    fun setSpeed(speed: Float) = runOnEngine { engine ->
        engine.setPlaybackSpeed(speed)
        mutableState.update { it.copy(speed = speed) }
    }

    fun next() = runOnEngine { engine -> if (engine.hasNextMediaItem()) engine.seekToNextMediaItem() }

    fun previous() = runOnEngine { engine ->
        if (engine.currentPosition > 3_000 || !engine.hasPreviousMediaItem()) engine.seekTo(0)
        else engine.seekToPreviousMediaItem()
    }

    /** Called from PlayerActivity when a media transition finished; keeps mini player state in sync. */
    fun onMediaItemChanged(entry: Any?) {
        if (entry is PlayerEntry) {
            index = queue.indexOfFirst { it.uri == entry.uri }.takeIf { it >= 0 } ?: index
            mutableState.update {
                it.copy(title = entry.title, thumbnail = entry.thumbnail, isAudio = entry.isAudio,
                    queueIndex = index, queueSize = queue.size, positionMs = 0, durationMs = 0, error = null)
            }
        }
    }

    fun close() {
        ticker?.cancel(); ticker = null
        listener?.let { listener -> player?.removeListener(listener) }
        listener = null
        player?.release()
        player = null
        queue = emptyList()
        mutableState.value = PlayerUiState()
    }

    /** Video decoding stops when the app is backgrounded; audio would need a media service. */
    fun onAppBackground() = pause()

    private fun runOnEngine(block: (ExoPlayer) -> Unit) {
        val engine = player ?: return
        scope.launch(Dispatchers.Main) { if (player === engine) block(engine) }
    }

    private fun ensurePlayer(): ExoPlayer {
        player?.let { return it }
        val created = ExoPlayer.Builder(context).build()
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                mutableState.update { it.copy(playing = isPlaying) }
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) {
                    mutableState.update { it.copy(playing = false, positionMs = it.durationMs) }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                onMediaItemChanged(mediaItem?.localConfiguration?.tag)
            }
            override fun onPlayerError(error: PlaybackException) {
                mutableState.update { it.copy(playing = false,
                    error = "This device cannot play this file. Try another app or a different format.") }
            }
        }
        created.addListener(listener)
        this.listener = listener
        player = created
        return created
    }

    private fun publishCurrent() {
        val entry = queue.getOrNull(index)
        mutableState.update {
            it.copy(active = true, playing = true, title = entry?.title, thumbnail = entry?.thumbnail,
                isAudio = entry?.isAudio == true, speed = it.speed, queueIndex = index, queueSize = queue.size,
                positionMs = 0, durationMs = 0, error = null)
        }
        startTicker()
    }

    private fun startTicker() {
        ticker?.cancel()
        ticker = scope.launch(Dispatchers.Main) {
            while (isActive && player != null) {
                val engine = player
                if (engine != null) {
                    mutableState.update {
                        it.copy(positionMs = engine.currentPosition.coerceAtLeast(0),
                            durationMs = engine.duration.takeIf { ms -> ms > 0 } ?: it.durationMs)
                    }
                }
                delay(500)
            }
        }
    }
}
