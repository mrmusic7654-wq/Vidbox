package com.vidbox.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.vidbox.di.ApplicationScope
import com.vidbox.domain.repository.EventLogger
import com.vidbox.domain.usecase.DownloadActions
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadActionReceiver : BroadcastReceiver() {
    @Inject lateinit var actions: DownloadActions
    @Inject lateinit var logger: EventLogger
    @Inject @ApplicationScope lateinit var scope: CoroutineScope
    override fun onReceive(context: Context, intent: Intent) {
        val id = intent.getStringExtra(DownloadService.EXTRA_ID)?.takeIf { it.matches(Regex("[a-f0-9-]{36}")) } ?: return
        if (intent.action !in setOf(PAUSE, CANCEL)) return
        val result = goAsync()
        scope.launch {
            try {
                when (intent.action) { PAUSE -> actions.pause(id); CANCEL -> actions.cancel(id) }
            } catch (error: Exception) {
                logger.event("notification_action.failed", id, mapOf("type" to error.javaClass.simpleName))
            } finally { result.finish() }
        }
    }
    companion object {
        const val PAUSE = "com.vidbox.action.PAUSE"
        const val CANCEL = "com.vidbox.action.CANCEL"
    }
}
