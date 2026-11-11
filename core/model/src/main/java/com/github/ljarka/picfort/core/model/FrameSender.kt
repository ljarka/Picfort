package com.github.ljarka.picfort.core.model

sealed interface FrameResult {
    data object Sent : FrameResult
    data object NotConfigured : FrameResult
    data object Busy : FrameResult
    data class TooSoon(val retryAfterSeconds: Int?) : FrameResult
    data class Error(val message: String) : FrameResult
}

interface FrameSender {
    suspend fun send(assetId: String): FrameResult
    suspend fun sendLocal(assetId: String): FrameResult
}
