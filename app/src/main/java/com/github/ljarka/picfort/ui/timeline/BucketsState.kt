package com.github.ljarka.picfort.ui.timeline

enum class LoadingState {
    REFRESHING, LOADING, LOADED, DEFAULT
}

data class BucketsState(
    val loadingState: LoadingState = LoadingState.LOADING,
    val items: List<TimeBucketUi> = emptyList(),
)