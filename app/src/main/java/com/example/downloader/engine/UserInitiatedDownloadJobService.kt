package com.example.downloader.engine

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.PersistableBundle
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.example.KlipzApplication
import com.example.core.notifications.KlipzNotificationManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android 14+ (API 34+) User-Initiated Data Transfer JobService for long-running user-triggered downloads.
 */
class UserInitiatedDownloadJobService : JobService() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val runningJobs = mutableMapOf<Int, Job>()

    override fun onStartJob(params: JobParameters): Boolean {
        val taskId = params.extras.getString(DownloadWorker.KEY_TASK_ID) ?: return false
        val app = applicationContext as? KlipzApplication ?: return false

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val notification = app.container.notificationManager.buildDownloadNotification(
                title = "Klipz Active Transfer",
                subtitle = "Downloading media…",
                progressPercent = 0,
                indeterminate = true
            )
            setNotification(
                params,
                KlipzNotificationManager.FOREGROUND_NOTIFICATION_ID + (params.jobId % 100),
                notification,
                JOB_END_NOTIFICATION_POLICY_REMOVE
            )
        }

        val job = scope.launch {
            try {
                app.container.downloadManager.runTaskInWorker(taskId)
            } finally {
                jobFinished(params, false)
            }
        }
        runningJobs[params.jobId] = job
        return true
    }

    override fun onStopJob(params: JobParameters): Boolean {
        runningJobs.remove(params.jobId)?.cancel()
        return true
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }
}

/**
 * Schedules transfers using Android 14+ User-Initiated Data Transfer when supported and enabled,
 * falling back to WorkManager for broad Android 8+ compatibility and deferrable work.
 */
class DownloadScheduler(private val context: Context) {

    fun schedule(taskId: String, wifiOnly: Boolean, preferUserInitiated: Boolean = true) {
        if (preferUserInitiated && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val scheduledUidt = scheduleUserInitiatedJob(taskId, wifiOnly)
            if (scheduledUidt) return
        }
        scheduleWorkManager(taskId, wifiOnly)
    }

    fun cancel(taskId: String) {
        WorkManager.getInstance(context).cancelUniqueWork("klipz_dl_$taskId")
        val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
        scheduler?.cancel(jobIdFor(taskId))
    }

    private fun scheduleUserInitiatedJob(taskId: String, wifiOnly: Boolean): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return false
        return runCatching {
            val scheduler = context.getSystemService(Context.JOB_SCHEDULER_SERVICE) as? JobScheduler
                ?: return false
            val networkReqBuilder = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            if (wifiOnly) {
                networkReqBuilder.addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
            val extras = PersistableBundle().apply {
                putString(DownloadWorker.KEY_TASK_ID, taskId)
            }
            val info = JobInfo.Builder(
                jobIdFor(taskId),
                ComponentName(context, UserInitiatedDownloadJobService::class.java)
            )
                .setUserInitiated(true)
                .setRequiredNetwork(networkReqBuilder.build())
                .setExtras(extras)
                .build()
            scheduler.schedule(info) == JobScheduler.RESULT_SUCCESS
        }.getOrDefault(false)
    }

    private fun scheduleWorkManager(taskId: String, wifiOnly: Boolean) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .build()
        val request = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setConstraints(constraints)
            .setInputData(Data.Builder().putString(DownloadWorker.KEY_TASK_ID, taskId).build())
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            "klipz_dl_$taskId",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    private fun jobIdFor(taskId: String): Int = (taskId.hashCode() and 0x0FFFFFFF).coerceAtLeast(1000)
}
