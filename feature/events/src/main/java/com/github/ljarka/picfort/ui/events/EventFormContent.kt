package com.github.ljarka.picfort.ui.events

import android.text.format.DateFormat
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.core.model.EventRepeat
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.R
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

private val TIME_FMT: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventFormContent(
    state: EventFormUi,
    locale: Locale = Locale.ENGLISH,
    onTitleChange: (String) -> Unit = {},
    onStartDateChange: (LocalDate) -> Unit = {},
    onEndDateChange: (LocalDate?) -> Unit = {},
    onStartTimeChange: (LocalTime?) -> Unit = {},
    onLocationChange: (String) -> Unit = {},
    onNoteChange: (String) -> Unit = {},
    onRepeatChange: (EventRepeat?) -> Unit = {},
    onRepeatUntilChange: (LocalDate?) -> Unit = {},
    onToggleMember: (String) -> Unit = {},
    onSave: () -> Unit = {},
    onClose: () -> Unit = {},
    onMessageShown: () -> Unit = {},
    albumSection: @Composable () -> Unit = {},
) {
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(state.message) {
        state.message?.let {
            snackbar.showSnackbar(it)
            onMessageShown()
        }
    }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var pickingStartDate by remember { mutableStateOf(false) }
    var pickingEndDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var pickingRepeatUntil by remember { mutableStateOf(false) }

    if (pickingRepeatUntil) {
        val minDate = state.startDate ?: state.today
        val minMillis = localDateToPickerMillis(minDate)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToPickerMillis(state.repeatUntil ?: minDate.plusMonths(3)),
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingRepeatUntil = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { onRepeatUntilChange(pickerMillisToLocalDate(it)) }
                        pickingRepeatUntil = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingRepeatUntil = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    val handleClose = {
        if (state.hasUnsavedChanges && !state.isSaving) {
            showDiscardDialog = true
        } else {
            onClose()
        }
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("Discard changes?") },
            text = { Text("Your unsaved changes to this event will be lost.") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onClose()
                }) {
                    Text("Discard")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) {
                    Text("Keep editing")
                }
            },
        )
    }

    if (pickingStartDate) {
        val initialMillis = (state.startDate ?: state.today).let(::localDateToPickerMillis)
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initialMillis)
        DatePickerDialog(
            onDismissRequest = { pickingStartDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onStartDateChange(pickerMillisToLocalDate(millis))
                        }
                        pickingStartDate = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingStartDate = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickingEndDate) {
        val minDate = state.startDate ?: state.today
        val minMillis = localDateToPickerMillis(minDate)
        val initialMillis = (state.endDate ?: minDate).let(::localDateToPickerMillis)
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            selectableDates = object : SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long): Boolean = utcTimeMillis >= minMillis
            },
        )
        DatePickerDialog(
            onDismissRequest = { pickingEndDate = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            onEndDateChange(pickerMillisToLocalDate(millis))
                        }
                        pickingEndDate = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                TextButton(onClick = { pickingEndDate = false }) { Text("Cancel") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (pickingTime) {
        val context = LocalContext.current
        val is24Hour = remember(context) { DateFormat.is24HourFormat(context) }
        val initial = state.startTime ?: LocalTime.of(18, 0)
        val timePickerState = rememberTimePickerState(
            initialHour = initial.hour,
            initialMinute = initial.minute,
            is24Hour = is24Hour,
        )
        AlertDialog(
            onDismissRequest = { pickingTime = false },
            title = { Text("Select time") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStartTimeChange(LocalTime.of(timePickerState.hour, timePickerState.minute))
                        pickingTime = false
                    },
                ) { Text("OK") }
            },
            dismissButton = {
                Row {
                    if (state.startTime != null) {
                        TextButton(
                            onClick = {
                                onStartTimeChange(null)
                                pickingTime = false
                            },
                        ) { Text("Clear") }
                    }
                    TextButton(onClick = { pickingTime = false }) { Text("Cancel") }
                }
            },
        )
    }

    val dateFmt = remember(locale) { DateTimeFormatter.ofPattern("d MMM yyyy", locale) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (state.isEditing) "Edit event" else "New event") },
                navigationIcon = {
                    IconButton(onClick = handleClose) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    Button(
                        onClick = onSave,
                        enabled = state.canSave,
                        modifier = Modifier.padding(end = 8.dp),
                    ) {
                        if (state.isSaving) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Text("Save")
                        }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (state.editsSeries) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = ImageVector.vectorResource(R.drawable.ic_repeat),
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            "Changes all events in the series.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
            }
            OutlinedTextField(
                value = state.title,
                onValueChange = onTitleChange,
                label = { Text("Title *") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            WhoField(state.members, state.memberIds, onToggleMember)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                PickerField(
                    label = "Start date *",
                    value = state.startDate?.format(dateFmt) ?: "Select date",
                    hasValue = state.startDate != null,
                    onClick = { pickingStartDate = true },
                    modifier = Modifier.weight(1f),
                )
                PickerField(
                    label = "End date",
                    value = state.endDate?.format(dateFmt) ?: "One day",
                    hasValue = state.endDate != null,
                    onClick = { pickingEndDate = true },
                    onClear = if (state.endDate != null) ({ onEndDateChange(null) }) else null,
                    modifier = Modifier.weight(1f),
                )
            }

            PickerField(
                label = "Time (optional)",
                value = state.startTime?.format(TIME_FMT) ?: "All day",
                hasValue = state.startTime != null,
                onClick = { pickingTime = true },
                onClear = if (state.startTime != null) ({ onStartTimeChange(null) }) else null,
                modifier = Modifier.fillMaxWidth(),
            )

            RepeatField(state.repeat, onRepeatChange)

            if (state.repeat != null) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ends",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        SegmentedButton(
                            selected = state.repeatUntil == null,
                            onClick = { onRepeatUntilChange(null) },
                            shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                        ) { Text("Never") }
                        SegmentedButton(
                            selected = state.repeatUntil != null,
                            onClick = { pickingRepeatUntil = true },
                            shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                        ) { Text(state.repeatUntil?.let { "On ${it.format(dateFmt)}" } ?: "On a date") }
                    }
                }
            }

            OutlinedTextField(
                value = state.location,
                onValueChange = onLocationChange,
                label = { Text("Location") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            OutlinedTextField(
                value = state.note,
                onValueChange = onNoteChange,
                label = { Text("Note") },
                minLines = 3,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )

            albumSection()
        }
    }
}

@Composable
private fun PickerField(
    label: String,
    value: String,
    hasValue: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onClear: (() -> Unit)? = null,
) {
    OutlinedCard(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (hasValue) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                )
            }
            if (onClear != null) {
                IconButton(
                    onClick = onClear,
                    modifier = Modifier.size(28.dp),
                ) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = "Clear $label",
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
    }
}

/** "Repeat" picker: one of [EventRepeat] or "Doesn't repeat". */
@Composable
private fun RepeatField(repeat: EventRepeat?, onChange: (EventRepeat?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        PickerField(
            label = "Repeat",
            value = repeat?.label ?: "Doesn't repeat",
            hasValue = repeat != null,
            onClick = { open = true },
            modifier = Modifier.fillMaxWidth(),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            (listOf<EventRepeat?>(null) + EventRepeat.entries).forEach { option ->
                DropdownMenuItem(
                    text = { Text(option?.label ?: "Doesn't repeat") },
                    onClick = {
                        open = false
                        onChange(option)
                    },
                )
            }
        }
    }
}
