package com.github.ljarka.picfort.backup

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.github.ljarka.picfort.R

private const val CHANNEL_ID = "backup"
private const val NOTIFICATION_ID = 1001

/** Ongoing "Backing up… 12 of 340" notification for the backup worker's foreground service. */
fun backupForegroundInfo(context: Context, done: Int, total: Int): ForegroundInfo {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Backup", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while photos are uploaded to Immich"
            }
        )
    }
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_cloud)
        .setContentTitle("Backing up to Immich")
        .setContentText(if (total > 0) "$done of $total" else "Preparing…")
        .setProgress(total, done, total == 0)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setSilent(true)
        .build()
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(NOTIFICATION_ID, notification)
    }
}
