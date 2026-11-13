package com.github.ljarka.picfort.backup

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.WorkQuery
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val PERIODIC = "backup-periodic"
private const val NOW = "backup-now"

sealed interface BackupStatus {
    data object Idle : BackupStatus
    data class Running(val done: Int, val total: Int) : BackupStatus
    data class Waiting(val reason: String) : BackupStatus
}

@Singleton
class BackupScheduler @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: BackupSettingsStore,
) {
    private val workManager = WorkManager.getInstance(context)

    private fun constraints() = Constraints.Builder()
        .setRequiredNetworkType(if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(settings.chargingOnly)
        .build()

    /** Applies the current settings: schedules or cancels the periodic backup. */
    fun apply() {
        if (settings.enabled) {
            val request = PeriodicWorkRequestBuilder<BackupWorker>(6, TimeUnit.HOURS)
                .setConstraints(constraints())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
                .build()
            workManager.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        } else {
            workManager.cancelUniqueWork(PERIODIC)
        }
    }

    /** "Back up now": runs as soon as the constraints allow. */
    fun runNow() {
        val request = OneTimeWorkRequestBuilder<BackupWorker>()
            .addTag(NOW)
            .setConstraints(constraints())
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 1, TimeUnit.MINUTES)
            .build()
        workManager.enqueueUniqueWork(NOW, ExistingWorkPolicy.KEEP, request)
    }

    fun cancelAll() {
        workManager.cancelUniqueWork(PERIODIC)
        workManager.cancelUniqueWork(NOW)
    }

    val status: Flow<BackupStatus> =
        workManager.getWorkInfosFlow(WorkQuery.fromUniqueWorkNames(PERIODIC, NOW)).map { infos ->
            val running = infos.firstOrNull { it.state == WorkInfo.State.RUNNING }
            when {
                running != null -> BackupStatus.Running(
                    running.progress.getInt(KEY_DONE, 0),
                    running.progress.getInt(KEY_TOTAL, 0),
                )

                infos.any { NOW in it.tags && it.state == WorkInfo.State.ENQUEUED } -> BackupStatus.Waiting(
                    if (settings.wifiOnly) "Waiting for Wi-Fi" else "Waiting for network",
                )

                else -> BackupStatus.Idle
            }
        }
}
