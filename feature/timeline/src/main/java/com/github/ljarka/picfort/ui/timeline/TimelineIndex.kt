package com.github.ljarka.picfort.ui.timeline

import com.github.ljarka.picfort.core.database.AssetType
import com.github.ljarka.picfort.core.database.RemoteAssetEntity
import com.github.ljarka.picfort.local.ImageData
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * One photo or video on the timeline. [dateTaken] is the wall-clock time where it was taken,
 * written as if it were UTC (Immich's `localDateTime`), so months are grouped in UTC.
 */
data class TimelineAsset(
    val id: String,
    val type: AssetType,
    val dateTaken: Long,
    /** Width / height as displayed, or null if unknown. */
    val ratio: Float?,
    val isVideo: Boolean = false,
    val durationMs: Long? = null,
    val thumbhash: String? = null,
)

/** A month of the timeline; [key] is midnight UTC on its 1st, [start] its first global index. */
data class TimelineMonth(val key: Long, val start: Int, val assets: List<TimelineAsset>)

/** The whole timeline, newest first: Immich assets and phone-only media, merged. */
class TimelineIndex(val months: List<TimelineMonth>) {
    val size: Int = months.sumOf { it.assets.size }

    /** The asset at a global position (as the full-screen viewer counts them). */
    operator fun get(index: Int): TimelineAsset? {
        if (index < 0 || index >= size) return null
        var lo = 0
        var hi = months.lastIndex
        while (lo < hi) { // last month with start <= index
            val mid = (lo + hi + 1) / 2
            if (months[mid].start <= index) lo = mid else hi = mid - 1
        }
        return months[lo].assets[index - months[lo].start]
    }

    companion object {
        val EMPTY = TimelineIndex(emptyList())
    }
}

/**
 * Merges the synced Immich assets with the phone's media. Phone media already on the server
 * ([onServerLocalIds], see [localIdsOnServer]) is left out, so it shows once, as the Immich copy.
 */
fun buildTimelineIndex(
    remote: List<RemoteAssetEntity>,
    local: List<ImageData>,
    onServerLocalIds: Set<Long>,
    zone: ZoneId,
): TimelineIndex {
    val assets = ArrayList<TimelineAsset>(remote.size + local.size)
    remote.forEach { a ->
        val taken = a.localDateTime ?: return@forEach
        assets += TimelineAsset(
            id = a.id,
            type = AssetType.REMOTE,
            dateTaken = taken,
            ratio = ratioOf(a.width, a.height),
            isVideo = a.isVideo,
            // AssetV2.duration is milliseconds (Immich 3.3, T-062).
            durationMs = a.duration?.takeIf { a.isVideo && it > 0 },
            thumbhash = a.thumbhash,
        )
    }
    local.forEach { m ->
        if (m.id in onServerLocalIds) return@forEach
        assets += TimelineAsset(
            id = m.id.toString(),
            type = AssetType.LOCAL,
            dateTaken = wallClock(m.dateTaken, zone),
            ratio = ratioOf(m.width, m.height),
            isVideo = m.isVideo,
            durationMs = m.durationMs,
        )
    }
    assets.sortWith(compareByDescending<TimelineAsset> { it.dateTaken }.thenBy { it.id })

    val months = ArrayList<TimelineMonth>()
    var start = 0
    var i = 0
    while (i < assets.size) {
        val key = monthKey(assets[i].dateTaken)
        var end = i
        while (end < assets.size && monthKey(assets[end].dateTaken) == key) end++
        months += TimelineMonth(key, start, assets.subList(i, end).toList())
        start += end - i
        i = end
    }
    return TimelineIndex(months)
}

/** Midnight UTC on the 1st of the month of [wallClock] (which is written as UTC). */
fun monthKey(wallClock: Long): Long =
    Instant.ofEpochMilli(wallClock).atZone(ZoneOffset.UTC).toLocalDate().withDayOfMonth(1)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun ratioOf(width: Int?, height: Int?): Float? =
    if (width != null && height != null && width > 0 && height > 0) width.toFloat() / height else null
