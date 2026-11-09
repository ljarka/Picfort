package com.github.ljarka.picfort.backup

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import com.github.ljarka.picfort.R

private const val CHANNEL_ID = "backup"
private const val NOTIFICATION_ID = 1001
private const val QUOTA_NOTIFICATION_ID = 1002

private fun ensureChannel(context: Context) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Backup", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Progress while photos are uploaded to Immich"
            }
        )
    }
}

/** Backup stopped because the Immich account is out of space; only an admin can fix it. */
fun showQuotaExceededNotification(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
    ) return // Settings still shows the reason
    ensureChannel(context)
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.let {
        PendingIntent.getActivity(context, 0, it, PendingIntent.FLAG_IMMUTABLE)
    }
    val text = "Your storage quota on the server is full. Ask your Immich admin to raise it."
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_cloud)
        .setContentTitle("Backup stopped")
        .setContentText(text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(text))
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    context.getSystemService(NotificationManager::class.java).notify(QUOTA_NOTIFICATION_ID, notification)
}

/** Ongoing "Backing up… 12 of 340" notification for the backup worker's foreground service. */
fun backupForegroundInfo(context: Context, done: Int, total: Int): ForegroundInfo {
    ensureChannel(context)
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
