package com.github.ljarka.picfort.ui.shopping.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.github.ljarka.picfort.core.network.AppScope
import com.github.ljarka.picfort.ui.shopping.ShoppingRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/** A list's reminder may be changed while its old alarm is on the way: one more than this ahead is not due. */
private const val EARLY_TOLERANCE_MILLIS = 60_000L

/** How long a fired reminder waits for the server before it uses the copy on the phone. */
private const val REFRESH_TIMEOUT_MILLIS = 8_000L

@EntryPoint
@InstallIn(SingletonComponent::class)
interface ShoppingReminderEntryPoint {
    fun reminders(): ShoppingReminders
    fun repository(): ShoppingRepository
    fun sync(): ShoppingReminderSync

    @AppScope
    fun scope(): CoroutineScope
}

private fun Context.entryPoint() =
    EntryPointAccessors.fromApplication(applicationContext, ShoppingReminderEntryPoint::class.java)

/**
 * Shows a list's reminder when its alarm goes off, with the items still to buy: loaded from the
 * server when it answers, else the copy on the phone. Nothing left to buy, or the list deleted or
 * archived: no notification, the reminder is dropped. Also handles "Snooze 1h".
 */
class ShoppingReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val listId = intent.getLongExtra(EXTRA_LIST_ID, -1L).takeIf { it > 0 } ?: return
        val entry = context.entryPoint()
        val reminders = entry.reminders()
        when (intent.action) {
            ACTION_SNOOZE -> {
                val title = intent.getStringExtra(EXTRA_TITLE)
                    ?: entry.repository().lists.value[listId]?.title
                    ?: ""
                reminders.dismiss(listId)
                reminders.set(listId, title, System.currentTimeMillis() + SNOOZE_MILLIS)
            }

            ACTION_FIRE -> {
                val reminder = reminders.reminders.value[listId] ?: return
                if (reminder.at > System.currentTimeMillis() + EARLY_TOLERANCE_MILLIS) return
                val pending = goAsync()
                entry.scope().launch {
                    try {
                        fire(entry, reminder)
                    } finally {
                        pending.finish()
                    }
                }
            }
        }
    }

    private suspend fun fire(entry: ShoppingReminderEntryPoint, reminder: ShoppingReminder) {
        val repository = entry.repository()
        entry.sync().start()
        val error = withTimeoutOrNull(REFRESH_TIMEOUT_MILLIS) { repository.refresh() }
        val loaded = error == null && repository.offlineSince.value == null
        val list = repository.lists.value[reminder.listId]
        val reminders = entry.reminders()
        // Online and the list isn't among the active ones: deleted or archived.
        val gone = (loaded && list == null) || list?.archived == true
        val text = if (gone) null else reminderText(list)
        reminders.cancel(reminder.listId)
        if (text == null) return
        reminders.show(reminder.listId, list?.title ?: reminder.title, text)
    }
}

/** Re-arms the stored reminders after a reboot, an app update, a clock or time zone change. */
class ShoppingReminderRearmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        context.entryPoint().reminders().rearm()
    }
}

/** Keeps reminders in step with the lists the phone knows ([ShoppingReminders.onListsChanged]). */
@Singleton
class ShoppingReminderSync @Inject constructor(
    private val repository: ShoppingRepository,
    private val reminders: ShoppingReminders,
    @AppScope private val scope: CoroutineScope,
) {
    private var started = false

    @Synchronized
    fun start() {
        if (started) return
        started = true
        scope.launch {
            var before = repository.lists.value
            repository.lists.collect { after ->
                if (after !== before) reminders.onListsChanged(before, after)
                before = after
            }
        }
    }
}
