package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.core.database.RemoteAssetEntity
import com.github.ljarka.picfort.local.ImageData
import java.time.Instant
import java.time.ZoneId
import java.util.Base64
import kotlin.math.abs

private const val QUARTER_HOUR_MS = 15 * 60_000L
private const val MAX_ZONE_SHIFT_MS = 14 * 3_600_000L
private const val SLACK_MS = 1_000L

/**
 * Phone media that is already on the server, so the timeline shows only the Immich copy.
 * A file counts as on the server when:
 * - Picfort's backup linked it to one of [remote] ([backupLinks]: local id → Immich id);
 * - its SHA-1, hashed by the backup ([localChecksums], hex), equals an asset's checksum;
 * - an asset has the same file name, taken at the same time. Other apps (e.g. Immich's own)
 *   upload under the original name; the time may differ by a time zone offset, since the
 *   phone's date is read in the current zone while Immich uses the one where it was taken.
 */
fun localIdsOnServer(
    remote: List<RemoteAssetEntity>,
    local: List<ImageData>,
    backupLinks: Map<Long, String>,
    localChecksums: Map<Long, String>,
    zone: ZoneId,
): Set<Long> {
    val remoteIds = remote.mapTo(HashSet()) { it.id }
    val checksums = remote.mapTo(HashSet()) { it.checksum }
    val takenByName = HashMap<String, MutableList<Long>>()
    remote.forEach { a ->
        val name = a.originalFileName?.takeIf { it.isNotBlank() } ?: return@forEach
        val taken = a.localDateTime ?: return@forEach
        takenByName.getOrPut(name.lowercase()) { ArrayList(1) } += taken
    }
    return local.mapNotNullTo(HashSet()) { m ->
        val onServer = backupLinks[m.id] in remoteIds ||
            localChecksums[m.id]?.let { hexToBase64(it) in checksums } == true ||
            takenByName[m.fileName.lowercase()]?.any { isSameMoment(wallClock(m.dateTaken, zone), it) } == true
        m.id.takeIf { onServer }
    }
}

/** DATE_TAKEN is a real instant; shifted to wall-clock time like Immich's localDateTime. */
internal fun wallClock(dateTaken: Long, zone: ZoneId): Long =
    dateTaken + zone.rules.getOffset(Instant.ofEpochMilli(dateTaken)).totalSeconds * 1000L

/** Equal up to a second, or apart by a time zone offset (a whole number of quarter hours). */
private fun isSameMoment(a: Long, b: Long): Boolean {
    val diff = abs(a - b)
    if (diff > MAX_ZONE_SHIFT_MS + SLACK_MS) return false
    val rest = diff % QUARTER_HOUR_MS
    return rest <= SLACK_MS || rest >= QUARTER_HOUR_MS - SLACK_MS
}

/** Backup stores SHA-1 as hex, Immich sends it as base64. Null if [hex] isn't valid hex. */
internal fun hexToBase64(hex: String): String? {
    if (hex.length % 2 != 0) return null
    val bytes = ByteArray(hex.length / 2)
    for (i in bytes.indices) {
        val hi = Character.digit(hex[2 * i], 16)
        val lo = Character.digit(hex[2 * i + 1], 16)
        if (hi < 0 || lo < 0) return null
        bytes[i] = ((hi shl 4) or lo).toByte()
    }
    return Base64.getEncoder().encodeToString(bytes)
}
