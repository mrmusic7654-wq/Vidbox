package com.vidbox.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.*
import com.vidbox.data.downloader.DownloadQueue
import com.vidbox.service.DownloadNotifications
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException

@HiltWorker
class RecoveryWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted parameters: WorkerParameters,
    private val queue: DownloadQueue,
    private val notifications: DownloadNotifications,
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result = try {
        if (queue.prepareRecovery()) notifications.recovery()
        Result.success()
    } catch (cancel: CancellationException) { throw cancel }
    catch (_: Exception) { if (runAttemptCount < 3) Result.retry() else Result.failure() }

    companion object {
        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork("recover_downloads", ExistingWorkPolicy.KEEP,
                OneTimeWorkRequestBuilder<RecoveryWorker>().build())
        }
    }
}
