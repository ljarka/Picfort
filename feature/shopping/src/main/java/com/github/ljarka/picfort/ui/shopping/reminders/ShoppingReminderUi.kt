package com.github.ljarka.picfort.ui.shopping.reminders

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.feature.shopping.R
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The top bar's bell: highlighted while the list has a reminder on this phone. */
@Composable
fun ReminderBell(active: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = ImageVector.vectorResource(R.drawable.ic_shopping_reminder),
            contentDescription = if (active) "Change reminder" else "Set reminder",
            tint = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "🔔 Reminder · Friday, 17:00" under the top bar; a tap opens the sheet. */
@Composable
fun ReminderStatus(at: LocalDateTime, today: LocalDate, onClick: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = ImageVector.vectorResource(R.drawable.ic_shopping_reminder),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text("Reminder · ${reminderLabel(at, today)}", style = MaterialTheme.typography.labelLarge)
        }
    }
}

/**
 * The reminder sheet (T-040): quick presets, a custom date and time, and "Remove reminder"
 * when one is set. [now] is passed in so screenshots are stable.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderSheet(
    current: LocalDateTime?,
    now: LocalDateTime,
    onPick: (LocalDateTime) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    var pickingDate by remember { mutableStateOf(false) }
    var pickedDate by remember { mutableStateOf<LocalDate?>(null) }
    // Expanded at once: the presets are short, and a half-open sheet hides "Custom".
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        ReminderSheetContent(current, now, onPick, onCustom = { pickingDate = true }, onRemove = onRemove)
    }
    if (pickingDate) {
        ReminderDatePicker(
            initial = (current ?: now).toLocalDate(),
            today = now.toLocalDate(),
            onPick = {
                pickingDate = false
                pickedDate = it
            },
            onDismiss = { pickingDate = false },
        )
    }
    pickedDate?.let { date ->
        ReminderTimePicker(
            initial = current?.toLocalTime() ?: LocalTime.of(17, 0),
            onPick = { time ->
                pickedDate = null
                customReminderTime(date, time, LocalDateTime.now())?.let(onPick)
            },
            onDismiss = { pickedDate = null },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderDatePicker(
    initial: LocalDate,
    today: LocalDate,
    onPick: (LocalDate) -> Unit,
    onDismiss: () -> Unit,
) {
    val todayMillis = today.toPickerMillis()
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.toPickerMillis(),
        selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= todayMillis
        },
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = { state.selectedDateMillis?.let { onPick(it.fromPickerMillis()) } }) { Text("Next") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    ) {
        DatePicker(state = state)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReminderTimePicker(initial: LocalTime, onPick: (LocalTime) -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val is24Hour = LocalInspectionMode.current || remember(context) { DateFormat.is24HourFormat(context) }
    val state = rememberTimePickerState(initialHour = initial.hour, initialMinute = initial.minute, is24Hour = is24Hour)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select time") },
        text = { TimePicker(state = state) },
        confirmButton = { TextButton(onClick = { onPick(LocalTime.of(state.hour, state.minute)) }) { Text("Set") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** The sheet's list: presets, "Custom date & time…", and "Remove reminder" when one is set. */
@Composable
fun ReminderSheetContent(
    current: LocalDateTime?,
    now: LocalDateTime,
    onPick: (LocalDateTime) -> Unit = {},
    onCustom: () -> Unit = {},
    onRemove: () -> Unit = {},
) {
    Column(Modifier.verticalScroll(rememberScrollState()).navigationBarsPadding().padding(bottom = 8.dp)) {
        Text(
            text = current?.let { "Reminder · ${reminderLabel(it, now.toLocalDate())}" } ?: "Remind me",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        reminderPresets(now).forEach { preset ->
            ListItem(
                headlineContent = { Text(preset.label) },
                supportingContent = { Text(reminderLabel(preset.at, now.toLocalDate())) },
                modifier = Modifier.clickable { onPick(preset.at) },
            )
        }
        ListItem(
            headlineContent = { Text("Custom date & time…") },
            modifier = Modifier.clickable(onClick = onCustom),
        )
        if (current != null) {
            HorizontalDivider()
            ListItem(
                headlineContent = { Text("Remove reminder", color = MaterialTheme.colorScheme.error) },
                modifier = Modifier.clickable(onClick = onRemove),
            )
        }
    }
}

/** The date picker works in UTC midnights. */
private fun LocalDate.toPickerMillis(): Long = atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

private fun Long.fromPickerMillis(): LocalDate = Instant.ofEpochMilli(this).atZone(ZoneOffset.UTC).toLocalDate()

/** Epoch millis of a reminder time on this phone's clock, and back. */
fun LocalDateTime.toEpochMillis(zone: ZoneId = ZoneId.systemDefault()): Long = atZone(zone).toInstant().toEpochMilli()

fun Long.toLocalDateTime(zone: ZoneId = ZoneId.systemDefault()): LocalDateTime =
    Instant.ofEpochMilli(this).atZone(zone).toLocalDateTime()
