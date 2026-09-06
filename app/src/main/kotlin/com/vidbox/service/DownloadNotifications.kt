package com.vidbox.service

import android.Manifest
import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vidbox.MainActivity
import com.vidbox.R
import com.vidbox.domain.model.*
import com.vidbox.domain.util.DisplayFormat
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadNotifications @Inject constructor(@ApplicationContext private val context: Context) {
    private val manager = NotificationManagerCompat.from(context)
    private var activeIds = emptySet<Int>()

    fun initial(): Notification = base(ACTIVE_CHANNEL).setContentTitle("Vidbox downloads")
        .setContentText("Preparing your download queue…").setOngoing(true).setOnlyAlertOnce(true)
        .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE).build()

    @SuppressLint("MissingPermission") // Foreground notification is mandatory even if notification visibility was denied.
    fun update(records: List<DownloadRecord>) {
        val visible = records.filter { !it.state.isTerminal && (it.state != DownloadState.PAUSED || it.pauseReason in setOf(PauseReason.NETWORK, PauseReason.WIFI)) }
        val newIds = visible.map { notificationId(it.id) }.toSet()
        (activeIds - newIds).forEach(manager::cancel)
        activeIds = newIds
        if (!allowed()) return
        visible.forEach { record ->
            val builder = base(ACTIVE_CHANNEL).setContentTitle(record.fileName).setContentText(progressText(record))
                .setOngoing(true).setOnlyAlertOnce(true).setSilent(true).setGroup(ACTIVE_GROUP)
                .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            if (record.state == DownloadState.DOWNLOADING) {
                record.percent?.let { builder.setProgress(100, it.toInt(), false) } ?: builder.setProgress(0, 0, true)
            } else if (record.state.isRunning) builder.setProgress(0, 0, true)
            if (record.canPause) builder.addAction(0, "Pause", control(record.id, DownloadActionReceiver.PAUSE))
            builder.addAction(0, "Cancel", control(record.id, DownloadActionReceiver.CANCEL))
            manager.notify(notificationId(record.id), builder.build())
        }
        val working = visible.count { it.state.isRunning }
        manager.notify(FOREGROUND_ID, base(ACTIVE_CHANNEL)
            .setContentTitle(if (visible.size == 1) visible.first().fileName else "${visible.size} downloads · Vidbox")
            .setContentText(if (working > 0) "$working active · ${DisplayFormat.bytes(visible.sumOf { it.speedBytesPerSecond })}/s"
                else visible.firstOrNull()?.let(::progressText) ?: "Finishing up…")
            .setGroup(ACTIVE_GROUP).setGroupSummary(true).setOngoing(true).setOnlyAlertOnce(true).setSilent(true).build())
    }

    @SuppressLint("MissingPermission")
    fun terminal(record: DownloadRecord, enabled: Boolean) {
        if (!enabled || !allowed()) return
        val builder = base(RESULT_CHANNEL).setContentTitle(if (record.state == DownloadState.COMPLETED) "Download complete" else "Download needs attention")
            .setContentText(record.fileName).setAutoCancel(true).setStyle(NotificationCompat.BigTextStyle()
                .bigText(if (record.state == DownloadState.COMPLETED) "${record.fileName}\n${DisplayFormat.bytes(record.totalBytes)} · Saved to your library"
                    else "${record.fileName}\n${record.error?.message.orEmpty()}"))
        if (record.state == DownloadState.FAILED && record.error?.retryable == true) {
            builder.addAction(0, "Retry", resume(record.id))
        }
        manager.notify(notificationId(record.id) + RESULT_OFFSET, builder.build())
    }

    @SuppressLint("MissingPermission")
    fun recovery() {
        if (!allowed()) return
        manager.notify(RECOVERY_ID, base(RESULT_CHANNEL).setContentTitle("Your downloads are safe")
            .setContentText("Open Vidbox to continue interrupted downloads.").setAutoCancel(true).build())
    }
    fun clearActive() { activeIds.forEach(manager::cancel); activeIds = emptySet() }
    fun clearRecovery() = manager.cancel(RECOVERY_ID)
    fun allowed(): Boolean = manager.areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED)

    private fun base(channel: String) = NotificationCompat.Builder(context, channel)
        .setSmallIcon(R.drawable.ic_download).setColor(0xFF006B63.toInt())
        .setContentIntent(PendingIntent.getActivity(context, 1,
            Intent(context, MainActivity::class.java).putExtra(MainActivity.OPEN_DOWNLOADS, true)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE))
    private fun control(id: String, action: String): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, DownloadActionReceiver::class.java).setAction(action).setData(Uri.parse("vidbox://transfer/$id/$action"))
            .putExtra(DownloadService.EXTRA_ID, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
    private fun resume(id: String): PendingIntent = PendingIntent.getForegroundService(context, 0,
        Intent(context, DownloadService::class.java).setAction(DownloadService.RESUME).setData(Uri.parse("vidbox://transfer/$id/resume"))
            .putExtra(DownloadService.EXTRA_ID, id), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    private fun progressText(record: DownloadRecord) = when (record.state) {
        DownloadState.QUEUED -> "Queued · waiting for a download slot"
        DownloadState.EXTRACTING -> "Resolving the selected format…"
        DownloadState.DOWNLOADING -> listOfNotNull(record.percent?.let { "${it.toInt()}%" },
            "${DisplayFormat.bytes(record.speedBytesPerSecond)}/s", record.etaSeconds?.let { "${DisplayFormat.duration(it)} left" }).joinToString(" · ")
        DownloadState.PROCESSING -> "Finishing media and saving to your folder…"
        DownloadState.PAUSED -> if (record.pauseReason == PauseReason.WIFI) "Waiting for unmetered Wi-Fi" else "Waiting for connection"
        else -> record.state.name.lowercase().replaceFirstChar { it.uppercase() }
    }

    companion object {
        const val FOREGROUND_ID = 1001
        private const val RECOVERY_ID = 1002
        private const val RESULT_OFFSET = 100_000_000
        private const val ACTIVE_CHANNEL = "vidbox_transfers_v1"
        private const val RESULT_CHANNEL = "vidbox_results_v1"
        private const val ACTIVE_GROUP = "vidbox_active"
        private fun notificationId(id: String) = 2000 + (id.hashCode() and 0x03FFFFFF)
        fun createChannels(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.createNotificationChannels(listOf(
                NotificationChannel(ACTIVE_CHANNEL, "Active downloads", NotificationManager.IMPORTANCE_LOW).apply {
                    description = "Required progress and controls for background downloads"; setSound(null, null); enableVibration(false)
                },
                NotificationChannel(RESULT_CHANNEL, "Download results", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Completed downloads, errors, and recovery reminders"
                },
            ))
        }
    }
}
