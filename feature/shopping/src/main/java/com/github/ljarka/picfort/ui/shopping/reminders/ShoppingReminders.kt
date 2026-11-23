package com.github.ljarka.picfort.ui.shopping.reminders

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.edit
import com.github.ljarka.picfort.core.model.ShoppingList
import com.github.ljarka.picfort.feature.shopping.R
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** Extra on the launch intent of a tapped shopping reminder: the list to open (read by `MainActivity`). */
const val EXTRA_OPEN_SHOPPING_LIST_ID = "com.github.ljarka.picfort.OPEN_SHOPPING_LIST_ID"

internal const val ACTION_FIRE = "com.github.ljarka.picfort.shopping.REMINDER_FIRE"
internal const val ACTION_SNOOZE = "com.github.ljarka.picfort.shopping.REMINDER_SNOOZE"
internal const val EXTRA_LIST_ID = "list_id"
internal const val EXTRA_TITLE = "title"

private const val PREFS = "shopping_reminders"
private const val KEY_REMINDERS = "reminders"
private const val CHANNEL_ID = "shopping_reminders"

/** Notification ids of shopping reminders: away from events (100 000+) and the wall (300 000+). */
private const val NOTIFICATION_BASE = 400_000

/** An alarm missed while the phone was off still comes up this late; older ones are dropped. */
internal const val MAX_LATE_MILLIS = 12 * 60 * 60 * 1000L

const val SNOOZE_MILLIS = 60 * 60 * 1000L

/** One list's reminder on this phone; the title is kept for a notification without any copy of the list. */
@Serializable
data class ShoppingReminder(val listId: Long, val title: String, val at: Long)

/**
 * This phone's shopping reminders (T-040): set per phone like event reminders (ADR 0015), nothing on
 * the server. One per list, stored in preferences, armed with AlarmManager, shown by
 * [ShoppingReminderReceiver].
 */
@Singleton
class ShoppingReminders @Inject constructor(@ApplicationContext private val context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ShoppingReminder.serializer())
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private val _reminders = MutableStateFlow(read())

    /** By list id. */
    val reminders: StateFlow<Map<Long, ShoppingReminder>> = _reminders.asStateFlow()

    fun set(listId: Long, title: String, at: Long) {
        save(_reminders.value + (listId to ShoppingReminder(listId, title, at)))
        arm(listId, at)
    }

    /** Removes the reminder and its alarm; a shown notification stays (see [dismiss]). */
    fun cancel(listId: Long) {
        if (listId !in _reminders.value) return
        save(_reminders.value - listId)
        alarms.cancel(pendingIntent(listId))
    }

    /** Re-arms every reminder (after a reboot, an update, a clock change); too old ones are dropped. */
    fun rearm(now: Long = System.currentTimeMillis()) {
        val (kept, stale) = read().values.partition { it.at >= now - MAX_LATE_MILLIS }
        stale.forEach { alarms.cancel(pendingIntent(it.listId)) }
        save(kept.associateBy { it.listId })
        kept.forEach { arm(it.listId, it.at) }
    }

    /**
     * Follows the lists the phone knows: a list that was there and is gone (deleted, archived by
     * anyone) loses its reminder, and a shown reminder whose items are all checked goes away.
     */
    fun onListsChanged(before: Map<Long, ShoppingList>, after: Map<Long, ShoppingList>) {
        goneLists(_reminders.value.keys, before, after).forEach {
            cancel(it)
            dismiss(it)
        }
        finishedLists(before, after).forEach(::dismiss)
    }

    /** False on Android 12+ until the user allows exact alarms: a reminder may then come a few minutes late. */
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarms.canScheduleExactAlarms()

    fun canPostNotifications(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
        context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(listId: Long, title: String, text: String) {
        if (!canPostNotifications()) return
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Shopping reminders", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "A shopping list's items, at the time set on the list"
                },
            )
        }
        // CLEAR_TOP + SINGLE_TOP: a running app gets the list in onNewIntent instead of a second activity.
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_SHOPPING_LIST_ID, listId)
        }?.let {
            PendingIntent.getActivity(
                context,
                listId.toInt(),
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val snooze = PendingIntent.getBroadcast(
            context,
            listId.toInt(),
            Intent(context, ShoppingReminderReceiver::class.java)
                .setAction(ACTION_SNOOZE)
                .putExtra(EXTRA_LIST_ID, listId)
                .putExtra(EXTRA_TITLE, title),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_shopping_reminder)
            .setContentTitle(reminderTitle(title))
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setContentIntent(open)
            .addAction(0, "Open list", open)
            .addAction(0, "Snooze 1h", snooze)
            .setAutoCancel(true)
            .build()
        manager.notify(notificationId(listId), notification)
    }

    fun dismiss(listId: Long) {
        context.getSystemService(NotificationManager::class.java).cancel(notificationId(listId))
    }

    private fun notificationId(listId: Long) = NOTIFICATION_BASE + (listId and 0x000F_FFFF).toInt()

    private fun arm(listId: Long, at: Long) {
        val intent = pendingIntent(listId)
        if (exactAllowed()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        } else {
            alarms.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent)
        }
    }

    /** One alarm per list: the request code is the list id. */
    private fun pendingIntent(listId: Long): PendingIntent = PendingIntent.getBroadcast(
        context,
        listId.toInt(),
        Intent(context, ShoppingReminderReceiver::class.java)
            .setAction(ACTION_FIRE)
            .putExtra(EXTRA_LIST_ID, listId),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun read(): Map<Long, ShoppingReminder> = prefs.getString(KEY_REMINDERS, null)
        ?.let { runCatching { json.decodeFromString(serializer, it) }.getOrNull() }
        .orEmpty()
        .associateBy { it.listId }

    private fun save(reminders: Map<Long, ShoppingReminder>) {
        prefs.edit { putString(KEY_REMINDERS, json.encodeToString(serializer, reminders.values.toList())) }
        _reminders.value = reminders
    }
}
