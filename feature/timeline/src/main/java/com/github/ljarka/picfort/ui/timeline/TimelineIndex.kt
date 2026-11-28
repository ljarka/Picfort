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

/**
 * A month of the timeline; [key] is midnight UTC on its 1st, [start] its first global index.
 * [best]: positions in [assets] of its "Best of the month" photos, newest first (T-094).
 */
data class TimelineMonth(
    val key: Long,
    val start: Int,
    val assets: List<TimelineAsset>,
    val best: List<Int> = emptyList(),
)

/** At most this many photos in a month's "Best" strip (T-094). */
const val BEST_PER_MONTH = 12

/** Fewer best photos than this and the month shows no strip, so half-analyzed months don't look broken. */
const val BEST_MIN = 3

/** Positions of [assets]' Immich photos whose id is in [bestIds], newest first; empty below [BEST_MIN]. */
internal fun bestOfMonth(assets: List<TimelineAsset>, bestIds: Set<String>): List<Int> {
    if (bestIds.isEmpty()) return emptyList()
    val best = assets.indices
        .filter { assets[it].type == AssetType.REMOTE && !assets[it].isVideo && assets[it].id in bestIds }
        .take(BEST_PER_MONTH)
    return if (best.size >= BEST_MIN) best else emptyList()
}

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

/** What the photo AI saw (T-098, T-099). [analyzed] has every labelled id; the others are subsets. */
data class AiLabels(
    val analyzed: Set<String> = emptySet(),
    val nonPhotos: Set<String> = emptySet(),
    val sensitive: Set<String> = emptySet(),
)

/** AI categories the "hide screenshots & documents" filter hides; the server keeps the same list. */
val AI_NON_PHOTO_CATEGORIES = setOf("screenshot", "document", "receipt", "meme")

fun aiLabelsOf(labels: List<com.github.ljarka.picfort.core.database.AiLabelEntity>) = AiLabels(
    analyzed = labels.mapTo(HashSet()) { it.assetId },
    nonPhotos = labels.filter { it.category in AI_NON_PHOTO_CATEGORIES }.mapTo(HashSet()) { it.assetId },
    sensitive = labels.filter { it.sensitive }.mapTo(HashSet()) { it.assetId },
)

private val NON_PHOTO_BUCKETS = setOf("screenshots", "whatsapp images", "download", "downloads")

/**
 * True for Immich photos that look like screenshots, saved images without camera EXIF, or
 * OCR-detected documents (T-088). Videos are never hidden.
 */
internal fun isHiddenNonPhoto(
    asset: RemoteAssetEntity,
    nonCameraIds: Set<String>,
    documentIds: Set<String>,
    ai: AiLabels = AiLabels(),
): Boolean {
    if (asset.isVideo) return false
    // The photo AI's answer beats the guesses below (T-099): a WhatsApp photo without camera EXIF
    // or a street sign caught by OCR stays; a screenshot the AI recognized goes.
    if (asset.id in ai.analyzed) return asset.id in ai.nonPhotos
    if (asset.id in documentIds || asset.id in nonCameraIds) return true
    return asset.originalFileName?.contains("screenshot", ignoreCase = true) == true
}

/** True for phone-only photos in screenshot/saved folders (T-088). Videos are never hidden. */
internal fun isHiddenLocalNonPhoto(media: ImageData): Boolean {
    if (media.isVideo) return false
    if (media.fileName.contains("screenshot", ignoreCase = true)) return true
    return media.bucketName?.lowercase() in NON_PHOTO_BUCKETS
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
    hideNonPhotos: Boolean = false,
    nonCameraIds: Set<String> = emptySet(),
    documentIds: Set<String> = emptySet(),
    bestIds: Set<String> = emptySet(),
    ai: AiLabels = AiLabels(),
): TimelineIndex {
    val assets = ArrayList<TimelineAsset>(remote.size + local.size)
    remote.forEach { a ->
        if (hideNonPhotos && isHiddenNonPhoto(a, nonCameraIds, documentIds, ai)) return@forEach
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
        if (hideNonPhotos && isHiddenLocalNonPhoto(m)) return@forEach
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
        val monthAssets = assets.subList(i, end).toList()
        months += TimelineMonth(key, start, monthAssets, bestOfMonth(monthAssets, bestIds))
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
