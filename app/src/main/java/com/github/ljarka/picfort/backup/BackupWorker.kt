package com.github.ljarka.picfort.backup

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.io.IOException
import retrofit2.HttpException

const val KEY_DONE = "done"
const val KEY_TOTAL = "total"
const val KEY_UPLOADED = "uploaded"

@HiltWorker
class BackupWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val engine: BackupEngine,
    private val settings: BackupSettingsStore,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        var outcome: BackupOutcome? = null
        val (result, message) = try {
            promoteToForeground(0, 0)
            outcome = engine.run {
                setProgress(workDataOf(KEY_DONE to it.done, KEY_TOTAL to it.total))
                promoteToForeground(it.done, it.total)
            }
            when (val o = outcome) {
                is BackupOutcome.Done -> Result.success(workDataOf(KEY_UPLOADED to o.uploaded)) to summary(o)

                BackupOutcome.NoMediaAccess -> Result.failure() to "Allow access to photos and videos"

                BackupOutcome.NoLocationAccess ->
                    Result.failure() to "Open Picfort and allow photo locations: backup keeps them"

                BackupOutcome.NotLoggedIn -> Result.failure() to "Log in to back up"

                is BackupOutcome.QuotaExceeded -> Result.failure() to buildString {
                    if (o.uploaded > 0) append("Uploaded ${o.uploaded} · ")
                    append("Storage quota on the server is full. Ask your Immich admin to raise it.")
                }.also { showQuotaExceededNotification(applicationContext) }
            }
        } catch (e: IOException) {
            Log.w("BackupWorker", "Backup interrupted, will retry", e)
            Result.retry() to "Interrupted (no connection), will retry" // WorkManager backs off
        } catch (e: HttpException) {
            Log.w("BackupWorker", "Backup failed", e)
            if (e.code() >= 500) {
                Result.retry() to "Server error (HTTP ${e.code()}), will retry"
            } else {
                Result.failure() to "Server refused backup (HTTP ${e.code()})"
            }
        }
        // Stopped by the user (BackupScheduler.stop): keep its "Stopped", not the cut-off upload's error.
        if (!isStopped) {
            settings.lastResult = message
            settings.lastRunAt = System.currentTimeMillis()
            if (BackupScheduler.NOW_TAG in tags) {
                backupFinishedTitle(outcome, retrying = result == Result.retry())?.let {
                    showBackupFinishedNotification(applicationContext, it, message)
                }
            }
        }
        return result
    }

    /**
     * Runs as a foreground service so the 10-minute worker limit doesn't cut off big
     * backups. Android 12+ may refuse this while the app is in the background; the
     * backup then continues as a normal (time-limited) worker and resumes next run.
     */
    private suspend fun promoteToForeground(done: Int, total: Int) {
        try {
            setForeground(backupForegroundInfo(applicationContext, done, total))
        } catch (e: IllegalStateException) {
            if (done == 0) Log.i("BackupWorker", "Can't run in the foreground now: ${e.message}")
        }
    }

    private fun summary(o: BackupOutcome.Done) = backupSummary(o, homeAddressSet = settings.homeUrl.isNotBlank())
}

/**
 * The title of the notification a "Back up now" run leaves when it ends (T-006), above the same
 * line as in Settings. None while it will retry, or when the quota has its own notification.
 */
fun backupFinishedTitle(outcome: BackupOutcome?, retrying: Boolean): String? = when {
    retrying || outcome is BackupOutcome.QuotaExceeded -> null
    outcome is BackupOutcome.Done -> "Backup complete"
    else -> "Backup didn't finish"
}

/** The last backup's line in Settings. */
fun backupSummary(o: BackupOutcome.Done, homeAddressSet: Boolean): String = buildList {
    if (o.viaHomeNetwork) add("Home network")
    add(if (o.uploaded == 0 && o.waitingForHome == 0) "Up to date" else "Uploaded ${o.uploaded}")
    if (o.alreadyOnServer > 0) add("${o.alreadyOnServer} were already in Immich")
    if (o.rejected > 0) add("${o.rejected} not supported by the server")
    if (o.waitingForHome > 0) {
        add(
            if (homeAddressSet) {
                "${o.waitingForHome} over 100 MB wait for the home network"
            } else {
                "${o.waitingForHome} too large for the server's public address: set the home network address"
            },
        )
    }
}.joinToString(" · ")
