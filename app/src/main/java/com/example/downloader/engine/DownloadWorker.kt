package com.example.downloader.engine

import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.example.KlipzApplication
import com.example.core.notifications.KlipzNotificationManager

/**
 * WorkManager CoroutineWorker driving background downloads with an Android 14+ compliant
 * dataSync foreground notification.
 */
class DownloadWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        const val KEY_TASK_ID = "task_id"
    }

    override suspend fun doWork(): Result {
        val taskId = inputData.getString(KEY_TASK_ID) ?: return Result.failure()
        val app = applicationContext as? KlipzApplication ?: return Result.failure()
        val downloadManager = app.container.downloadManager
        val notifications = app.container.notificationManager

        val notification = notifications.buildDownloadNotification(
            title = "Klipz Download",
            subtitle = "Transferring media in background…",
            progressPercent = 0,
            indeterminate = true
        )

        runCatching {
            val foregroundInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    KlipzNotificationManager.FOREGROUND_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                )
            } else {
                ForegroundInfo(KlipzNotificationManager.FOREGROUND_NOTIFICATION_ID, notification)
            }
            setForeground(foregroundInfo)
        }

        val succeeded = downloadManager.runTaskInWorker(taskId)
        return if (succeeded) Result.success() else Result.failure()
    }
}
