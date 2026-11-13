package com.github.ljarka.picfort.ui.timeline

import kotlinx.serialization.Serializable

@Serializable
data class BucketResponse(
    val id: List<String>,
    val ratio: List<Double>,
    val fileCreatedAt: List<String>,
    val isImage: List<Boolean> = emptyList(),
    /** "H:MM:SS.ffffff" for videos, null for images. */
    val duration: List<String?> = emptyList(),
    /** Base64 ThumbHash per asset, for a blurred preview while the thumbnail loads. */
    val thumbhash: List<String?> = emptyList(),
)

/** Parses Immich's "H:MM:SS.ffffff" duration; null for images or "0:00:00.00000". */
fun parseImmichDuration(value: String?): Long? {
    val parts = value?.split(':')?.takeIf { it.size == 3 } ?: return null
    val hours = parts[0].toLongOrNull() ?: return null
    val minutes = parts[1].toLongOrNull() ?: return null
    val seconds = parts[2].toDoubleOrNull() ?: return null
    val ms = hours * 3_600_000 + minutes * 60_000 + (seconds * 1000).toLong()
    return ms.takeIf { it > 0 }
}

/** 75_000 -> "1:15", 3_725_000 -> "1:02:05". */
fun formatDuration(ms: Long): String {
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
