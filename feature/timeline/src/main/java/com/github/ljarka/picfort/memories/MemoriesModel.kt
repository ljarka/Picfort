package com.github.ljarka.picfort.memories

import com.github.ljarka.picfort.collections.GridAsset
import com.github.ljarka.picfort.collections.parseSearchDuration
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

/** An Immich memory (`GET /api/memories`, Immich 3): "On this day" or a birthday, with its photos. */
@Serializable
data class MemoryDto(
    val id: String,
    val type: String = "on_this_day",
    val data: MemoryDataDto = MemoryDataDto(),
    /** The day the memory is for (ISO date-time). */
    val memoryAt: String = "",
    val isSaved: Boolean = false,
    val assets: List<MemoryAssetDto> = emptyList(),
)

@Serializable
data class MemoryDataDto(val year: Int = 0, val personName: String? = null)

@Serializable
data class MemoryAssetDto(
    val id: String,
    val type: String = "IMAGE",
    val duration: JsonElement? = null,
    val thumbhash: String? = null,
)

/** One memory as the Memories screen shows it (T-096). */
data class MemoryUi(
    val id: String,
    val title: String,
    val subtitle: String,
    val date: LocalDate,
    val assets: List<GridAsset>,
    val isSaved: Boolean,
    val isBirthday: Boolean,
) {
    /** The viewer's list for this memory (`CollectionsRepository.putList`). */
    val source: String get() = "memory:$id"
}

data class MemorySection(val title: String, val memories: List<MemoryUi>)

private fun dayOf(memoryAt: String): LocalDate? = runCatching { OffsetDateTime.parse(memoryAt).toLocalDate() }
    .recoverCatching { LocalDate.parse(memoryAt.take(10)) }
    .getOrNull()

private fun yearsAgo(years: Int): String = when {
    years <= 0 -> "This year"
    years == 1 -> "1 year ago"
    else -> "$years years ago"
}

/**
 * Immich memories into the screen's sections, newest day first: "Today" (the most recent year
 * first), then "Saved", then "Earlier". Memories without photos are left out. [locale] formats
 * the dates (read in the ViewModel, not in a composable).
 */
fun memorySections(memories: List<MemoryDto>, today: LocalDate, locale: Locale): List<MemorySection> {
    val dateFormat = DateTimeFormatter.ofLocalizedDate(FormatStyle.LONG).withLocale(locale)
    val items = memories.mapNotNull { m ->
        val day = dayOf(m.memoryAt) ?: return@mapNotNull null
        if (m.assets.isEmpty()) return@mapNotNull null
        val year = m.data.year.takeIf { it > 0 } ?: day.year
        val date = runCatching { day.withYear(year) }.getOrDefault(day)
        val birthday = m.type == "birthday"
        val count = m.assets.size
        val photos = if (count == 1) "1 photo" else "$count photos"
        val title = if (birthday) {
            "${m.data.personName?.takeIf { it.isNotBlank() } ?: "A"}'s birthday"
        } else {
            yearsAgo(today.year - year)
        }
        MemoryUi(
            id = m.id,
            title = title,
            subtitle = "${date.format(dateFormat)} · $photos",
            date = day,
            assets = m.assets.map { a ->
                GridAsset(a.id, isVideo = a.type == "VIDEO", durationMs = parseSearchDuration(a.duration))
            },
            isSaved = m.isSaved,
            isBirthday = birthday,
        ) to year
    }
    val todays = items.filter { it.first.date == today }.sortedByDescending { it.second }.map { it.first }
    val rest = items.filter { it.first.date != today }
        .sortedWith(compareByDescending<Pair<MemoryUi, Int>> { it.first.date }.thenByDescending { it.second })
        .map { it.first }
    return listOfNotNull(
        MemorySection("Today", todays).takeIf { todays.isNotEmpty() },
        MemorySection("Saved", rest.filter { it.isSaved }).takeIf { rest.any { it.isSaved } },
        MemorySection("Earlier", rest.filterNot { it.isSaved }).takeIf { rest.any { !it.isSaved } },
    )
}
