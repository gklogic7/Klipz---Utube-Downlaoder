package com.example.core.notifications

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R

class KlipzNotificationManager(private val context: Context) {

    companion object {
        const val CHANNEL_DOWNLOADS = "klipz_downloads"
        const val CHANNEL_PROCESSING = "klipz_processing"
        const val FOREGROUND_NOTIFICATION_ID = 4100
    }

    init {
        createChannels()
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val downloadChannel = NotificationChannel(
                CHANNEL_DOWNLOADS,
                context.getString(R.string.notif_channel_downloads_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_downloads_desc)
            }
            val processingChannel = NotificationChannel(
                CHANNEL_PROCESSING,
                context.getString(R.string.notif_channel_processing_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.notif_channel_processing_desc)
            }
            manager.createNotificationChannel(downloadChannel)
            manager.createNotificationChannel(processingChannel)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun buildDownloadNotification(
        title: String,
        subtitle: String,
        progressPercent: Int,
        indeterminate: Boolean = false
    ): Notification {
        val openIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(subtitle)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progressPercent.coerceIn(0, 100), indeterminate)
            .setContentIntent(openIntent)
            .build()
    }

    fun notifyCompleted(taskId: String, title: String, playSound: Boolean, enabled: Boolean) {
        if (!enabled || !hasNotificationPermission()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Download complete")
            .setContentText(title)
            .setAutoCancel(true)
            .setSilent(!playSound)
        runCatching {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), builder.build())
        }
    }

    fun notifyFailed(taskId: String, title: String, reason: String, enabled: Boolean) {
        if (!enabled || !hasNotificationPermission()) return
        val builder = NotificationCompat.Builder(context, CHANNEL_DOWNLOADS)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("Download failed: $title")
            .setContentText(reason)
            .setAutoCancel(true)
        runCatching {
            NotificationManagerCompat.from(context).notify(taskId.hashCode(), builder.build())
        }
    }
}
