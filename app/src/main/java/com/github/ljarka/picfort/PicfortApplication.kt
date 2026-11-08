package com.github.ljarka.picfort

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.github.ljarka.picfort.backup.BackupScheduler
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.video.VideoFrameDecoder
import dagger.hilt.android.HiltAndroidApp
import okhttp3.OkHttpClient
import javax.inject.Inject

@HiltAndroidApp
class PicfortApplication : Application(), Configuration.Provider {
    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var backupScheduler: BackupScheduler

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder().setWorkerFactory(workerFactory).build()

    override fun onCreate() {
        super.onCreate()
        backupScheduler.apply() // keep the periodic backup in sync with settings
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components {
                    add(VideoFrameDecoder.Factory()) // thumbnails of videos on the phone
                    add(
                        OkHttpNetworkFetcherFactory(
                            callFactory = { okHttpClient }
                        )
                    )
                }
                .build()
        }
    }
}