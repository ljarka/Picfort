package com.github.ljarka.picfort.core.model

/** Availability of on-device receipt reading (Gemini Nano / AICore) on this device. */
enum class ReceiptAiStatus {
    Available,
    Downloadable,
    Downloading,
    Unavailable,
}

/** Reports whether on-device receipt reading is ready on the current device. */
interface ReceiptAiStatusProvider {
    suspend fun status(): ReceiptAiStatus
}
