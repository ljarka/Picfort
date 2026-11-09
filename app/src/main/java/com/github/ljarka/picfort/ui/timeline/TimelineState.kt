package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.ui.timeline.layout.TimelineLayout

enum class LoadingState {
    REFRESHING, LOADING, LOADED, DEFAULT
}

/** [layout] is null until the timeline has been built and measured once. */
data class TimelineState(
    val loadingState: LoadingState = LoadingState.LOADING,
    val layout: TimelineLayout? = null,
)
