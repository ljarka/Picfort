package com.github.ljarka.picfort.backup

import java.time.Instant

/** What we know about a file on the phone that is about to be uploaded. */
data class LocalMedia(
    val id: Long,
    val fileName: String,
    val mimeType: String,
    val size: Long,
    val dateTaken: Long,
    val dateModified: Long,
    val isVideo: Boolean,
    val durationMs: Long?,
)

/**
 * Multipart text fields for POST /api/assets. The API changed across versions:
 * - v1, v2: deviceAssetId and deviceId are required; duration is "H:MM:SS.ffffff".
 * - v2: metadata is required (an empty list is fine).
 * - v3+: device fields were removed; duration is milliseconds.
 */
fun uploadFields(media: LocalMedia, serverMajor: Int, deviceId: String): Map<String, String> {
    val created = Instant.ofEpochMilli(media.dateTaken.takeIf { it > 0 } ?: media.dateModified).toString()
    val modified = Instant.ofEpochMilli(media.dateModified.takeIf { it > 0 } ?: media.dateTaken).toString()
    val fields = linkedMapOf(
        "fileCreatedAt" to created,
        "fileModifiedAt" to modified,
        "filename" to media.fileName,
    )
    val duration = media.durationMs?.takeIf { media.isVideo && it > 0 }
    if (serverMajor < 3) {
        fields["deviceAssetId"] = "${media.fileName}-${media.size}"
        fields["deviceId"] = deviceId
        duration?.let { fields["duration"] = legacyDuration(it) }
        if (serverMajor == 2) fields["metadata"] = "[]"
    } else {
        duration?.let { fields["duration"] = it.toString() }
    }
    return fields
}

/** 75_500 -> "0:01:15.500000" */
fun legacyDuration(ms: Long): String {
    val h = ms / 3_600_000
    val m = (ms / 60_000) % 60
    val s = (ms / 1000) % 60
    val micros = (ms % 1000) * 1000
    return "%d:%02d:%02d.%06d".format(h, m, s, micros)
}
