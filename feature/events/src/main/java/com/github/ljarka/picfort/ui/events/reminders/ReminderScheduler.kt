package com.github.ljarka.picfort.ui.events.reminders

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.model.FamilyMemberProvider
import com.github.ljarka.picfort.ui.events.EventsRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.util.Locale
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

private const val PERIODIC = "event-reminders"

/**
 * Keeps this phone's alarms in line with the upcoming events (T-039): re-plans whenever the list, the
 * defaults, an event's choice or a member's name changes, and every 6 hours in the background
 * ([EventRemindersWorker]), so new events from others get their reminder without opening the app.
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repository: EventsRepository,
    private val family: FamilyMemberProvider,
    private val store: ReminderStore,
) {
    private val alarms = ReminderAlarms(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val lock = Mutex()
    private var started = false

    /** Called once from `Application.onCreate`. */
    fun start() {
        if (started) return
        started = true
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<EventRemindersWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
        scope.launch {
            combine(
                repository.upcoming,
                repository.upcomingLoaded,
                store.defaults,
                store.choices,
                family.allMembers.onStart { emit(emptyList()) },
            ) { events, loaded, _, _, members -> Triple(events, loaded, members) }
                .filter { (_, loaded, _) -> loaded }
                .collect { (events, _, members) -> reschedule(events, members) }
        }
    }

    /** Re-plans from [events]; the background worker calls it directly after its refresh. */
    suspend fun reschedule(events: List<FamilyEvent>, members: List<FamilyMember>? = null) = lock.withLock {
        val names = (members ?: runCatching { family.allMembers.first() }.getOrDefault(emptyList()))
            .associate { it.id to it.name }
        store.keepChoicesFor(events.mapTo(HashSet()) { it.id })
        val previous = store.plan
        val next = planReminders(
            events = events,
            defaults = store.defaults.value,
            choices = store.choices.value,
            delivered = store.delivered,
            armed = previous.mapTo(HashSet()) { it.key },
            now = LocalDateTime.now(),
            memberName = names::get,
            locale = Locale.getDefault(),
        )
        alarms.apply(previous, next)
        store.plan = next
        // A reminder already in the shade for an event that's gone (deleted) goes away.
        val ids = events.mapTo(HashSet()) { it.id }
        previous.filter { it.eventId !in ids }.forEach { dismissReminder(context, it.eventId) }
    }

    fun exactAllowed(): Boolean = alarms.exactAllowed()
}

/** Every 6 hours: fetch upcoming events and re-plan the reminders. */
@HiltWorker
class EventRemindersWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val repository: EventsRepository,
    private val family: FamilyMemberProvider,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        runCatching { family.refresh() }
        repository.refreshUpcoming()
        if (repository.upcomingLoaded.value) scheduler.reschedule(repository.upcoming.value)
        return Result.success()
    }
}
