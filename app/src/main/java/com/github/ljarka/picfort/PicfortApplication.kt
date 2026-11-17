package com.github.ljarka.picfort

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.video.VideoFrameDecoder
import com.github.ljarka.picfort.backup.BackupScheduler
import com.github.ljarka.picfort.local.LocalThumbnailFetcherFactory
import com.github.ljarka.picfort.ui.events.reminders.ReminderScheduler
import com.github.ljarka.picfort.ui.schooljournal.SchoolJournalNotifyScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class PicfortApplication :
    Application(),
    Configuration.Provider {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var backupScheduler: BackupScheduler

    @Inject
    lateinit var reminderScheduler: ReminderScheduler

    @Inject
    lateinit var schoolJournalNotifyScheduler: SchoolJournalNotifyScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        backupScheduler.apply() // keep the periodic backup in sync with settings
        reminderScheduler.start() // event reminders follow the events (T-039)
        schoolJournalNotifyScheduler.start() // school journal notifications (T-082, ADR 0017)
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(LocalThumbnailFetcherFactory()) // phone photos/videos in the grid
                    add(VideoFrameDecoder.Factory()) // video frames before Android 10
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = { okHttpClient },
                        ),
                    )
                }
                .build()
        }
    }
}
