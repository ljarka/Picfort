package com.github.ljarka.picfort.ui.events.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.github.ljarka.picfort.feature.events.R
import java.time.LocalDateTime
import java.time.ZoneId

/** Extra on the launch intent of a tapped reminder: the event to open (read by `MainActivity`). */
const val EXTRA_OPEN_EVENT_ID = "com.github.ljarka.picfort.OPEN_EVENT_ID"

private const val EXTRA_KEY = "reminder_key"
private const val CHANNEL_ID = "event_reminders"

/** Notification ids of reminders: away from the backup's (1001–1003). */
private const val NOTIFICATION_BASE = 100_000

/** Arms and cancels the AlarmManager alarms of a plan; no Hilt, so the boot receiver can use it. */
class ReminderAlarms(private val context: Context) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    /** False on Android 12+ until the user allows exact alarms: reminders may then come a few minutes late. */
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun arm(reminder: PlannedReminder) {
        val at = LocalDateTime.parse(reminder.trigger).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val intent = pendingIntent(reminder.eventId, reminder.key)
        if (exactAllowed()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    fun cancel(eventId: Long) {
        alarms.cancel(pendingIntent(eventId, null))
    }

    /** One alarm per event: the request code is the event id, the extras say which occurrence. */
    private fun pendingIntent(eventId: Long, key: String?): PendingIntent = PendingIntent.getBroadcast(
        context,
        eventId.toInt(),
        Intent(context, ReminderReceiver::class.java).putExtra(EXTRA_KEY, key),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    /** Replaces the armed plan with [next]: alarms of events no longer in it are cancelled. */
    fun apply(previous: List<PlannedReminder>, next: List<PlannedReminder>) {
        val nextIds = next.mapTo(HashSet()) { it.eventId }
        previous.filter { it.eventId !in nextIds }.forEach { cancel(it.eventId) }
        next.forEach(::arm)
    }
}

/** Shows a reminder when its alarm goes off, from the stored plan (no network needed). */
class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val key = intent.getStringExtra(EXTRA_KEY) ?: return
        val store = ReminderStore(context)
        val reminder = store.plan.firstOrNull { it.key == key } ?: return
        if (key in store.delivered) return
        showReminder(context, reminder)
        store.markDelivered(key)
    }
}

/** Re-arms the stored plan after a reboot, an app update, a clock or time zone change. */
class ReminderRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val plan = ReminderStore(context).plan
        ReminderAlarms(context).apply(emptyList(), plan)
    }
}

fun canPostNotifications(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

private fun showReminder(context: Context, reminder: PlannedReminder) {
    if (!canPostNotifications(context)) return
    val manager = context.getSystemService(NotificationManager::class.java)
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Event reminders", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "Family events and birthdays, at the time chosen in Settings or on the event"
            },
        )
    }
    // CLEAR_TOP + SINGLE_TOP: a running app gets the event in onNewIntent instead of a second activity.
    val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        putExtra(EXTRA_OPEN_EVENT_ID, reminder.eventId)
    }?.let {
        PendingIntent.getActivity(
            context,
            reminder.eventId.toInt(),
            it,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
    val notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notifications)
        .setContentTitle(reminder.title)
        .setContentText(reminder.text)
        .setStyle(NotificationCompat.BigTextStyle().bigText(reminder.text))
        .setCategory(NotificationCompat.CATEGORY_REMINDER)
        .setPriority(NotificationCompat.PRIORITY_HIGH)
        .setContentIntent(open)
        .setAutoCancel(true)
        .build()
    manager.notify(NOTIFICATION_BASE + reminder.eventId.toInt(), notification)
}

/** A deleted event's reminder goes away from the notification shade too. */
fun dismissReminder(context: Context, eventId: Long) {
    context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_BASE + eventId.toInt())
}
