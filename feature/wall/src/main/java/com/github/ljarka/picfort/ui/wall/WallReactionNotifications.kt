package com.github.ljarka.picfort.ui.wall

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
import com.github.ljarka.picfort.core.network.AccessTokenStore
import com.github.ljarka.picfort.feature.wall.R
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException

private const val WORK_NAME = "wall-reactions-notify"
private const val CHANNEL_ID = "wall_reactions"
private const val PREFS = "wall_reactions"

/** Notification ids of wall reactions, away from backup, event reminders and the school journal. */
private const val NOTIFICATION_BASE = 300_000

/** A notification for the first reaction on one of my posts (T-084). */
data class ReactionNotification(val postId: Int, val title: String, val text: String)

/** "Tata reacted ❤️" / the post's text, or "your photo" for a photo without text. */
internal fun planReactionNotifications(items: List<FirstReactionDto>): List<ReactionNotification> =
    items.distinctBy { it.postId }.map { item ->
        val about = item.text?.trim()?.takeIf { it.isNotEmpty() }
            ?: if (item.hasPhoto) "Your photo" else "Your post"
        ReactionNotification(
            postId = item.postId,
            title = "${item.by.ifBlank { "Someone" }} reacted ${item.emoji}",
            text = about,
        )
    }

/**
 * Where this phone stopped reading first reactions, per Immich user (T-084). Starts at "now" the
 * first time, so reactions from before the app knew about them never notify.
 */
@Singleton
class WallReactionCursor @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun since(userId: String): String? = prefs.getString("since:$userId", null)

    fun save(userId: String, since: String) {
        prefs.edit().putString("since:$userId", since).apply()
    }
}

/** ISO time the server compares as text: `2026-11-18T12:00:00Z`. */
internal fun isoSeconds(at: Instant): String = at.truncatedTo(ChronoUnit.SECONDS).toString()

/**
 * Reads my posts' first reactions after the saved cursor and moves the cursor to the newest one
 * (T-084). The first run only starts the cursor and notifies nothing.
 */
internal suspend fun checkFirstReactions(
    api: WallApi,
    cursor: WallReactionCursor,
    userId: String,
    now: Instant = Instant.now(),
): List<ReactionNotification> {
    val since = cursor.since(userId)
    if (since == null) {
        cursor.save(userId, isoSeconds(now))
        return emptyList()
    }
    val items = api.firstReactions(since)
    if (items.isEmpty()) return emptyList()
    cursor.save(userId, items.maxOf { it.at })
    return planReactionNotifications(items)
}

fun canPostWallNotifications(context: Context): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

internal fun showReactionNotifications(context: Context, items: List<ReactionNotification>) {
    if (items.isEmpty() || !canPostWallNotifications(context)) return
    val manager = context.getSystemService(NotificationManager::class.java) ?: return
    if (manager.getNotificationChannel(CHANNEL_ID) == null) {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Family Wall reactions", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "The first reaction to each of your Family Wall posts"
            },
        )
    }
    items.forEach { item ->
        val id = NOTIFICATION_BASE + (item.postId and 0x00FF_FFFF)
        val open = context.packageManager.getLaunchIntentForPackage(context.packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }?.let {
            PendingIntent.getActivity(
                context,
                id,
                it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_favorite)
            .setContentTitle(item.title)
            .setContentText(item.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
            .setContentIntent(open)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }
}

/** Enqueues the periodic 15-minute `wall-reactions-notify` job; called from `PicfortApplication.onCreate`. */
@Singleton
class WallReactionNotifyScheduler @Inject constructor(@ApplicationContext private val context: Context) {
    fun start() {
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<WallReactionNotifyWorker>(15, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

@HiltWorker
class WallReactionNotifyWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val api: WallApi,
    private val cursor: WallReactionCursor,
    private val accessTokens: AccessTokenStore,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val userId = accessTokens.userId
        if (!api.isConfigured || userId.isBlank()) return Result.success()
        return try {
            showReactionNotifications(applicationContext, checkFirstReactions(api, cursor, userId))
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) { // offline, older server: try again next time
            Log.w("WallReactions", "Reaction check failed: ${e.message}")
            Result.success()
        }
    }
}
