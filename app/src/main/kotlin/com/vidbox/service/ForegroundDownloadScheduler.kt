package com.vidbox.service

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import com.vidbox.domain.repository.DownloadScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ForegroundDownloadScheduler @Inject constructor(@ApplicationContext private val context: Context) : DownloadScheduler {
    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java).setAction(DownloadService.WAKE))
    }
}
