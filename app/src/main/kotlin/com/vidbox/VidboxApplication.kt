package com.vidbox

import android.app.ActivityManager
import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.vidbox.service.DownloadNotifications
import com.vidbox.worker.RecoveryWorker
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class VidboxApplication : Application(), Configuration.Provider, ImageLoaderFactory {
    @Inject lateinit var workerFactory: HiltWorkerFactory
    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).setMinimumLoggingLevel(android.util.Log.WARN).build()
    override fun onCreate() {
        super.onCreate()
        DownloadNotifications.createChannels(this)
        RecoveryWorker.enqueue(this)
    }
    override fun newImageLoader() = ImageLoader.Builder(this)
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.12).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("thumbnails")).maxSizeBytes(48L * 1024 * 1024).build() }
        .allowHardware(!getSystemService(ActivityManager::class.java).isLowRamDevice)
        .crossfade(true).respectCacheHeaders(true).build()
}
