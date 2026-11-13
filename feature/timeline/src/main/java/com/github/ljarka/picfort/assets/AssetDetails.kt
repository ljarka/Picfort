package com.github.ljarka.picfort.assets

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.Locale
import kotlin.math.roundToInt

data class DetailRow(val label: String, val value: String)

data class AssetDetails(
    val rows: List<DetailRow>,
    /** Latitude/longitude, for opening a map. */
    val location: Pair<Double, Double>? = null,
)

private val DATE_FORMAT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy, HH:mm", Locale.ENGLISH)

/** Formats an ISO timestamp in [zone] (the photo's own zone if Immich knows it). */
fun formatTakenAt(iso: String?, zone: ZoneId): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        DATE_FORMAT.format(Instant.parse(iso).atZone(zone))
    } catch (e: DateTimeParseException) {
        null
    }
}

fun formatFileSize(bytes: Long): String = when {
    bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f GB", bytes / (1L shl 30).toDouble())
    bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", bytes / (1L shl 20).toDouble())
    bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", bytes / (1L shl 10).toDouble())
    else -> "$bytes B"
}

/** "4032 × 3024 · 12.2 MP" */
fun formatResolution(width: Int, height: Int): String {
    val mp = width.toLong() * height / 1_000_000.0
    return if (mp >= 0.1) {
        String.format(Locale.US, "%d × %d · %.1f MP", width, height, mp)
    } else {
        "$width × $height"
    }
}

/** "ƒ/1.8 · 1/120 s · 24 mm · ISO 100", skipping unknown parts. */
fun formatExposure(exif: ExifInfo): String? = listOfNotNull(
    exif.fNumber?.let { String.format(Locale.US, "ƒ/%.1f", it).replace(".0", "") },
    exif.exposureTime?.takeIf { it.isNotBlank() }?.let { "$it s" },
    exif.focalLength?.let { "${it.roundToInt()} mm" },
    exif.iso?.let { "ISO ${it.roundToInt()}" },
).joinToString(" · ").ifEmpty { null }

fun AssetInfo.toDetails(deviceZone: ZoneId = ZoneId.systemDefault()): AssetDetails {
    val exif = exifInfo
    val photoZone = exif?.timeZone?.let { runCatching { ZoneId.of(it) }.getOrNull() } ?: deviceZone
    val camera = listOfNotNull(exif?.make, exif?.model)
        .joinToString(" ")
        // "Apple iPhone 15" rather than "Apple Apple iPhone 15"
        .let { text -> exif?.make?.let { make -> text.replace("$make $make", make) } ?: text }
        .ifBlank { null }
    val width = exif?.exifImageWidth?.roundToInt()
    val height = exif?.exifImageHeight?.roundToInt()
    val place = listOfNotNull(exif?.city, exif?.state, exif?.country)
        .filter { it.isNotBlank() }.distinct().joinToString(", ").ifBlank { null }
    val lat = exif?.latitude
    val lon = exif?.longitude

    val rows = listOfNotNull(
        exif?.description?.takeIf { it.isNotBlank() }?.let { DetailRow("Description", it) },
        formatTakenAt(exif?.dateTimeOriginal ?: fileCreatedAt, photoZone)?.let { DetailRow("Taken", it) },
        originalFileName.takeIf { it.isNotBlank() }?.let { DetailRow("File", it) },
        listOfNotNull(
            if (width != null && height != null && width > 0 && height > 0) formatResolution(width, height) else null,
            exif?.fileSizeInByte?.let(::formatFileSize),
        ).joinToString(" · ").ifBlank { null }?.let { DetailRow("Size", it) },
        camera?.let { DetailRow("Camera", it) },
        exif?.lensModel?.takeIf { it.isNotBlank() }?.let { DetailRow("Lens", it) },
        exif?.let(::formatExposure)?.let { DetailRow("Exposure", it) },
        place?.let { DetailRow("Place", it) },
        people.map { it.name }.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }
            ?.let { DetailRow("People", it.joinToString(", ")) },
    )
    return AssetDetails(rows, if (lat != null && lon != null) lat to lon else null)
}
