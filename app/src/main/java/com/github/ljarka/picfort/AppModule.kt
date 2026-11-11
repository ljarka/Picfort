package com.github.ljarka.picfort

import com.github.ljarka.picfort.core.network.AppScope
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.MainScope
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class AppModule {

    @AppScope
    @Singleton
    @Provides
    fun appScope() = MainScope()

    @Provides
    fun immichUsersSync(timeline: dagger.Lazy<com.github.ljarka.picfort.ui.timeline.SyncTimelineSource>): com.github.ljarka.picfort.core.model.ImmichUsersSync =
        com.github.ljarka.picfort.core.model.ImmichUsersSync { timeline.get().refresh() }
}