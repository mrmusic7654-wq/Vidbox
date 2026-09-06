package com.vidbox.service

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.os.PowerManager
import com.vidbox.data.downloader.DownloadQueue
import com.vidbox.domain.model.*
import com.vidbox.domain.repository.*
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {
    @Inject lateinit var queue: DownloadQueue
    @Inject lateinit var repository: DownloadRepository
    @Inject lateinit var settings: SettingsRepository
    @Inject lateinit var notifications: DownloadNotifications
    @Inject lateinit var logger: EventLogger
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var runner: Job? = null
    private var wakeJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var preferences = AppSettings()
    private var lastStartId = 0
    private var rerun = false
    private var pendingCommands = 0
    private var foreground = false

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.createChannels(this)
        try {
            startForeground(DownloadNotifications.FOREGROUND_ID, notifications.initial(), ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            foreground = true
        } catch (error: RuntimeException) {
            logger.event("service.failed", fields = mapOf("type" to error.javaClass.simpleName))
            scope.launch { queue.pauseForSystemLimit(); notifications.recovery(); stopSelf() }
            return
        }
        notifications.clearRecovery()
        wakeLock = getSystemService(PowerManager::class.java).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$packageName:downloads")
            .apply { setReferenceCounted(false) }
        scope.launch {
            combine(repository.observeActive(), settings.settings) { rows, prefs ->
                preferences = prefs
                rows
            }.sample(1000).collect { rows ->
                    notifications.update(rows)
                    manageWakeLock(rows.any { it.state.isRunning })
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!foreground) { stopSelf(); return START_NOT_STICKY }
        lastStartId = startId
        rerun = true
        pendingCommands++
        scope.launch {
            try {
                if (intent?.action == RESUME) {
                    intent.getStringExtra(EXTRA_ID)?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) }?.let { id ->
                        repository.transition(id, setOf(DownloadState.PAUSED, DownloadState.FAILED, DownloadState.CANCELLED), DownloadState.QUEUED)
                    }
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                logger.event("service_command.failed", fields = mapOf("type" to error.javaClass.simpleName))
            } finally {
                pendingCommands--
                if (scope.isActive) startSession()
            }
        }
        return START_STICKY
    }

    private fun startSession() {
        rerun = true
        if (runner?.isActive == true) return
        runner = scope.launch {
            var drained = false
            try {
                while (isActive) {
                    val ownedStartId = lastStartId
                    rerun = false
                    queue.run { notifications.terminal(it, preferences.completionNotifications) }
                    val remaining = repository.active()
                    if (rerun || pendingCommands > 0 || lastStartId != ownedStartId || remaining.any {
                            it.state == DownloadState.QUEUED || it.state.isRunning ||
                                it.pauseReason in setOf(PauseReason.NETWORK, PauseReason.WIFI)
                        }) {
                        yield()
                        continue
                    }
                    // No suspension between this check and teardown. AMS also rejects an old stop ID
                    // if a newer start is pending delivery, closing the enqueue-at-idle race.
                    if (!stopSelfResult(ownedStartId)) { yield(); continue }
                    notifications.update(remaining)
                    drained = true
                    break
                }
            } catch (cancel: CancellationException) { throw cancel }
            catch (error: Exception) {
                logger.event("service.failed", fields = mapOf("type" to error.javaClass.simpleName))
                queue.pauseForSystemLimit()
                notifications.recovery()
            } finally {
                runner = null
                manageWakeLock(false)
                notifications.clearActive()
                foreground = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                if (!drained) stopSelf()
            }
        }
    }

    private fun manageWakeLock(active: Boolean) {
        if (active && wakeJob?.isActive != true) {
            wakeJob = scope.launch {
                try {
                    while (isActive) {
                        wakeLock?.acquire(10 * 60 * 1000L)
                        delay(9 * 60 * 1000L)
                    }
                } finally { wakeLock?.takeIf { it.isHeld }?.release() }
            }
        } else if (!active) {
            wakeJob?.cancel(); wakeJob = null
            wakeLock?.takeIf { it.isHeld }?.release()
        }
    }

    /** Android 15+ imposes an aggregate six-hour dataSync budget. Never try to evade it. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        runner?.cancel()
        scope.launch {
            try { queue.pauseForSystemLimit(); notifications.recovery() }
            finally { stopForeground(STOP_FOREGROUND_REMOVE); stopSelf() }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() {
        scope.cancel()
        wakeLock?.takeIf { it.isHeld }?.release()
        notifications.clearActive()
        super.onDestroy()
    }
    companion object {
        const val WAKE = "com.vidbox.action.WAKE"
        const val RESUME = "com.vidbox.action.RESUME"
        const val EXTRA_ID = "download_id"
    }
}
