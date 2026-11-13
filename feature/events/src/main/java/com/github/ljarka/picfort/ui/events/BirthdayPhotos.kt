package com.github.ljarka.picfort.ui.events

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.github.ljarka.picfort.core.model.DayPhoto
import com.github.ljarka.picfort.core.model.FamilyEvent
import com.github.ljarka.picfort.core.ui.layout.justify
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One birthday (the day of birth is age 0) and the photos taken that day, newest first. */
data class BirthdayYear(val day: LocalDate, val age: Int, val photos: List<DayPhoto>)

/** The birthday photos part of the detail: null when the event isn't a birthday with a known year. */
data class BirthdayPhotosUi(val loading: Boolean = true, val years: List<BirthdayYear> = emptyList())

/**
 * The days of birth and of every birthday up to [today], newest first, with the age reached.
 * Only for birthdays whose year is known ([FamilyEvent.turns]); the series starts on the birth date.
 * A 29 February birthday falls on 28 February in other years, as the server counts it.
 */
fun birthdayDays(event: FamilyEvent, today: LocalDate): List<Pair<LocalDate, Int>> {
    if (!event.isBirthday || event.turns == null) return emptyList()
    val born = event.seriesStartDate
    return generateSequence(0) { it + 1 }
        .map { age -> born.plusYears(age.toLong()) to age }
        .takeWhile { (day, _) -> !day.isAfter(today) }
        .toList()
        .asReversed()
}

/** Groups [photos] (newest first) by the birthday they were taken on; years without photos are left out. */
fun birthdayYears(days: List<Pair<LocalDate, Int>>, photos: List<DayPhoto>): List<BirthdayYear> {
    val byDay = photos.groupBy { it.day }
    return days.mapNotNull { (day, age) -> byDay[day]?.let { BirthdayYear(day, age, it) } }
}

private sealed interface PhotoRow {
    val key: String
}

private data class YearHeader(val year: BirthdayYear) : PhotoRow {
    override val key: String get() = "y${year.day}"
}

private data class PhotoTile(val photo: DayPhoto, val index: Int, val width: Float)

private data class TilesRow(val day: LocalDate, val row: Int, val tiles: List<PhotoTile>, val height: Float) :
    PhotoRow {
    override val key: String get() = "r${day}_$row"
}

private const val PER_ROW = 2.5f
private val SPACING = 4.dp
private val SIDE = 12.dp

private fun layoutYears(years: List<BirthdayYear>, width: Float, spacing: Float): List<PhotoRow> {
    val target = (width - spacing * (PER_ROW - 1f)) / PER_ROW
    val rows = ArrayList<PhotoRow>()
    var index = 0
    years.forEach { year ->
        rows += YearHeader(year)
        justify(year.photos.map { it.ratio }, width, target, spacing).forEachIndexed { r, row ->
            val tiles = row.widths.mapIndexed { k, w ->
                PhotoTile(year.photos[row.start + k], index + row.start + k, w)
            }
            rows += TilesRow(year.day, r, tiles, row.height)
        }
        index += year.photos.size
    }
    return rows
}

private val YEAR_DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

fun birthdayYearTitle(year: BirthdayYear): String =
    (if (year.age == 0) "Born" else "Turned ${year.age}") + " · " + year.day.format(YEAR_DATE)

/**
 * The detail as one scrolling list: [header] (the event) and under it, for a birthday, the photos
 * of each birthday in justified rows like the timeline. Lazy, so years of photos stay cheap.
 */
@Composable
internal fun EventDetailList(
    photos: BirthdayPhotosUi?,
    name: String,
    onOpenPhoto: (DayPhoto, Int) -> Unit,
    modifier: Modifier = Modifier,
    /** A year's title: that day in the Photos timeline (T-056). */
    onOpenDay: (LocalDate) -> Unit = {},
    header: @Composable () -> Unit,
) {
    BoxWithConstraints(modifier) {
        val density = LocalDensity.current
        val widthPx = with(density) { (maxWidth - SIDE * 2).toPx() }
        val spacingPx = with(density) { SPACING.toPx() }
        val years = photos?.years.orEmpty()
        val rows = remember(years, widthPx, spacingPx) { layoutYears(years, widthPx, spacingPx) }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "detail") { header() }
            if (photos != null) {
                item(key = "photos-title") {
                    Text(
                        "Photos from $name's birthdays",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
                    )
                }
                when {
                    photos.loading && years.isEmpty() -> item(key = "photos-loading") {
                        CircularProgressIndicator(Modifier.padding(20.dp).size(24.dp), strokeWidth = 2.dp)
                    }

                    years.isEmpty() -> item(key = "photos-none") {
                        Text(
                            "No photos from these days in Immich yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        )
                    }
                }
                items(rows, key = { it.key }, contentType = { if (it is YearHeader) "year" else "row" }) { row ->
                    when (row) {
                        is YearHeader -> YearTitle(row.year) { onOpenDay(row.year.day) }

                        is TilesRow -> Row(
                            horizontalArrangement = Arrangement.spacedBy(SPACING),
                            modifier = Modifier.fillMaxWidth().padding(horizontal = SIDE, vertical = SPACING / 2),
                        ) {
                            row.tiles.forEach { tile ->
                                PhotoTileView(
                                    tile.photo,
                                    Modifier.size(
                                        with(density) {
                                            tile.width.toDp()
                                        },
                                        with(density) { row.height.toDp() },
                                    ),
                                ) { onOpenPhoto(tile.photo, tile.index) }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YearTitle(year: BirthdayYear, onClick: () -> Unit) {
    val label = "Show ${year.day.format(YEAR_DATE)} in Photos"
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clickable(onClickLabel = label, onClick = onClick)
            .padding(start = 20.dp, end = 12.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Text(
            birthdayYearTitle(year),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun PhotoTileView(photo: DayPhoto, modifier: Modifier, onClick: () -> Unit) {
    val label = (if (photo.isVideo) "Video, " else "Photo, ") + photo.day.format(YEAR_DATE)
    androidx.compose.foundation.layout.Box(
        modifier
            .semantics { contentDescription = label }
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = photo.thumbnailUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (photo.isVideo) {
            Icon(
                Icons.Default.PlayArrow,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                    .size(20.dp),
            )
        }
    }
}
