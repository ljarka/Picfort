package com.github.ljarka.picfort.ui.schooljournal

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.github.ljarka.picfort.feature.schooljournal.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/**
 * Extra on the launch intent of a tapped school-journal notification (spec §4.7, §7, read by
 * `MainActivity`): `"grades"`, `"messages"`, or `"messages:<id>"`.
 */
const val EXTRA_OPEN_SCHOOL_JOURNAL = "com.github.ljarka.picfort.OPEN_SCHOOL_JOURNAL"

internal const val OPEN_GRADES = "grades"
internal const val OPEN_MESSAGES = "messages"
internal const val OPEN_MESSAGE_PREFIX = "messages:"

private const val WORK_NAME = "school-journal-notify"
private const val CHANNEL_ID = "school_journal"
private const val CHANGES_LIMIT = 100
private const val MAX_CHANGE_PAGES = 20

/** More than this many notifications for one child in one run collapses into a single summary. */
internal const val SUMMARY_THRESHOLD = 3

/** Notification ids of the school journal, away from backup (1001–1003) and event reminders (100_000+). */
private const val NOTIFICATION_BASE = 200_000

/** One notification to show from the change feed (spec §4.7). */
data class JournalNotification(
    val key: String,
    val childId: String,
    val title: String,
    val text: String,
    /** Value for [EXTRA_OPEN_SCHOOL_JOURNAL]. */
    val target: String,
)

/**
 * Picks the change events worth notifying about (spec §4.7, T-082):
 * - grade `created` → "New grade",
 * - grade `updated` with `value` in `diff` → "Grade changed" (`Mathematics 4 → 5`),
 * - message `created` not already read by this user on this phone → "Message" / "Announcement",
 * - nothing for `deleted` or other updates.
 *
 * When a child has more than [SUMMARY_THRESHOLD] notifications in one run, they collapse into one
 * summary ("Ola: 2 new grades, 1 message").
 */
internal fun planNotifications(
    events: List<ChangeEvent>,
    readState: ReadState,
    childNames: Map<String, String>,
): List<JournalNotification> {
    data class Raw(
        val key: String,
        val childId: String,
        val isGrade: Boolean,
        val title: String,
        val text: String,
        val target: String,
    )

    fun withChild(base: String, childId: String): String {
        val name = childNames[childId]?.takeIf { it.isNotBlank() } ?: return base
        return "$base · $name"
    }

    val raw = events.mapNotNull { event ->
        when {
            event.isGrade && event.changeType == "created" -> {
                val g = event.grade()?.takeIf { it.deletedAt == null } ?: return@mapNotNull null
                val body = listOfNotNull(
                    "${g.subject} ${g.value}".trim(),
                    g.category.takeIf { it.isNotBlank() },
                ).joinToString(" · ")
                Raw(
                    key = "grade:${g.id}",
                    childId = event.childId.ifEmpty { g.childId },
                    isGrade = true,
                    title = withChild("New grade", event.childId.ifEmpty { g.childId }),
                    text = body,
                    target = OPEN_GRADES,
                )
            }

            event.isGrade && event.changeType == "updated" -> {
                val values = event.diff?.get("value") ?: return@mapNotNull null
                if (values.size < 2) return@mapNotNull null
                val old = values[0].jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val new = values[1].jsonPrimitive.contentOrNull?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (old == new) return@mapNotNull null
                val g = event.grade()?.takeIf { it.deletedAt == null } ?: return@mapNotNull null
                Raw(
                    key = "grade:${g.id}",
                    childId = event.childId.ifEmpty { g.childId },
                    isGrade = true,
                    title = withChild("Grade changed", event.childId.ifEmpty { g.childId }),
                    text = "${g.subject} $old → $new".trim(),
                    target = OPEN_GRADES,
                )
            }

            event.isMessage && event.changeType == "created" -> {
                val m = event.message()?.takeIf { it.deletedAt == null && !readState.isRead(it) }
                    ?: return@mapNotNull null
                val childId = event.childId.ifEmpty { m.childId }
                val label = if (m.kind == MessageKind.ANNOUNCEMENT) "Announcement" else "Message"
                val sender = senderLabel(m.kind, m.sender)
                val body = if (sender.isNotBlank()) "$sender: ${m.subject}" else m.subject
                Raw(
                    key = "message:${m.id}",
                    childId = childId,
                    isGrade = false,
                    title = withChild(label, childId),
                    text = body,
                    target = "$OPEN_MESSAGE_PREFIX${m.id}",
                )
            }

            else -> null
        }
    }

    // Latest event wins if the same grade/message changed twice in one batch.
    val deduped = raw.associateBy { it.key }.values.toList()
    val byChild = deduped.groupBy { it.childId }
    return byChild.flatMap { (childId, items) ->
        if (items.size <= SUMMARY_THRESHOLD) {
            items.map { JournalNotification(it.key, it.childId, it.title, it.text, it.target) }
        } else {
            val grades = items.count { it.isGrade }
            val messages = items.size - grades
            val parts = buildList {
                if (grades > 0) add(if (grades == 1) "1 new grade" else "$grades new grades")
                if (messages > 0) add(if (messages == 1) "1 message" else "$messages messages")
            }.joinToString(", ")
            val name = childNames[childId]?.takeIf { it.isNotBlank() }
            val text = if (name != null) "$name: $parts" else parts
            val target = when {
                grades > 0 && messages == 0 -> OPEN_GRADES
                messages == 1 && grades == 0 -> items.first { !it.isGrade }.target
                else -> OPEN_MESSAGES
            }
            listOf(
                JournalNotification(
                    key = "summary:$childId",
                    childId = childId,
                    title = withChild("School journal", childId),
                    text = text,
                    target = target,
                ),
            )
        }
    }
}

/**
 * Reads `/api/v1/changes?after=<notifyCursor>` while `hasMore`, plans notifications and advances
 * `notifyCursor` only after the whole batch succeeded (T-082).
 *
 * When this Immich user has no [ReadState] yet on this phone, starts it at `status.lastEventId`
 * and returns no notifications (no flood of history, spec §4.7).
 */
internal suspend fun checkNotifications(
    api: SchoolJournalApi,
    c: Connection,
    readStates: ReadStateStore,
    now: Instant = Instant.now(),
): List<JournalNotification> {
    val address = c.base.toString()
    val existing = readStates.load(address)
    if (existing == null) {
        val status = api.status(c)
        readStates.ensureStarted(address, now, status.lastEventId)
        return emptyList()
    }
    val events = mutableListOf<ChangeEvent>()
    var cursor = existing.notifyCursor
    var pages = 0
    do {
        val page = api.changes(c, after = cursor, limit = CHANGES_LIMIT)
        events += page.items
        cursor = maxOf(cursor, page.nextCursor)
        pages++
    } while (page.hasMore && pages < MAX_CHANGE_PAGES)

    if (events.isEmpty()) {
        if (cursor > existing.notifyCursor) {
            readStates.update(address) { it.notifiedUpTo(cursor) }
        }
        return emptyList()
    }

    val children = runCatching { api.children(c) }.getOrNull().orEmpty()
    val names = if (children.size >= 2) children.associate { it.id to it.name } else emptyMap()
    val current = readStates.load(address) ?: existing
    val planned = planNotifications(events, current, names)
    readStates.update(address) { it.notifiedUpTo(cursor) }
    return planned
}

fun canPostJournalNotifications(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

internal fun notificationIdForKey(key: String): Int = NOTIFICATION_BASE + (key.hashCode() and 0x0FFF_FFFF)

/** Reading a message in the app removes its notification from the shade (spec §4.7). */
fun dismissMessageNotification(context: Context, messageId: String) {
    runCatching {
        context.getSystemService(NotificationManager::class.java)
            ?.cancel(notificationIdForKey("message:$messageId"))
    }
}

internal fun showJournalNotifications(context: Context, items: List<JournalNotification>) {
    if (items.isEmpty() || !canPostJournalNotifications(context)) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "School journal", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "New grades, changed grades and new messages from the school e-journal"
            },
        )
    }
    items.forEach { item ->
        val id = notificationIdForKey(item.key)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(EXTRA_OPEN_SCHOOL_JOURNAL, item.target)
        }?.let {
            PendingIntent.getActivity(
                context,
                id,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_school_building)
            .setContentTitle(item.title)
            .setContentText(item.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
            .setGroup("school_journal_${item.childId}")
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}

/**
 * Enqueues or cancels the periodic 15-minute `school-journal-notify` job (spec §4.7, §7).
 * Called from `PicfortApplication.onCreate` and whenever the connection or the toggle changes.
 */
@Singleton
class SchoolJournalNotifyScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val config: SchoolJournalConfigStore,
) {
    fun start() {
        val work = WorkManager.getInstance(context)
        if (config.connection.value != null && config.notifications) {
            work.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<SchoolJournalNotifyWorker>(15, TimeUnit.MINUTES)
                    .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                    .build(),
            )
        } else {
            work.cancelUniqueWork(WORK_NAME)
        }
    }
}

@HiltWorker
class SchoolJournalNotifyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: SchoolJournalApi,
    private val config: SchoolJournalConfigStore,
    private val readStates: ReadStateStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = config.connection.value ?: return Result.success()
        if (!config.notifications) return Result.success()
        return try {
            val items = checkNotifications(api, c, readStates)
            showJournalNotifications(applicationContext, items)
            Result.success()
        } catch (e: SchoolJournalError) {
            Log.w("SchoolJournal", "Notification check failed: ${e.message}")
            Result.success()
        }
    }
}
