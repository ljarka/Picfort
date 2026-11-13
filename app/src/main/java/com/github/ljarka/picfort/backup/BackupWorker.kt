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
        val (result, message) = try {
            promoteToForeground(0, 0)
            val outcome = engine.run {
                setProgress(workDataOf(KEY_DONE to it.done, KEY_TOTAL to it.total))
                promoteToForeground(it.done, it.total)
            }
            when (outcome) {
                is BackupOutcome.Done -> Result.success(workDataOf(KEY_UPLOADED to outcome.uploaded)) to
                    summary(outcome)

                BackupOutcome.NoMediaAccess -> Result.failure() to "Allow access to photos and videos"

                BackupOutcome.NotLoggedIn -> Result.failure() to "Log in to back up"

                is BackupOutcome.QuotaExceeded -> Result.failure() to buildString {
                    if (outcome.uploaded > 0) append("Uploaded ${outcome.uploaded} · ")
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
        settings.lastResult = message
        settings.lastRunAt = System.currentTimeMillis()
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

    private fun summary(o: BackupOutcome.Done) = buildList {
        add(if (o.uploaded == 0) "Up to date" else "Uploaded ${o.uploaded}")
        if (o.alreadyOnServer > 0) add("${o.alreadyOnServer} were already in Immich")
        if (o.rejected > 0) add("${o.rejected} not supported by the server")
    }.joinToString(" · ")
}
