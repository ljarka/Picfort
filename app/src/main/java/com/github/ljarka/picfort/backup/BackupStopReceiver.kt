package com.github.ljarka.picfort.backup

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** "Stop" on the backup notification (T-006): the same as Stop in Settings (T-063). */
@AndroidEntryPoint
class BackupStopReceiver : BroadcastReceiver() {
    @Inject
    lateinit var scheduler: BackupScheduler

    override fun onReceive(context: Context, intent: Intent) {
        scheduler.stop()
    }
}
