package com.github.ljarka.picfort.ui.school

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.github.ljarka.picfort.core.model.FamilyMember
import com.github.ljarka.picfort.core.ui.avatar.MemberAvatar
import com.github.ljarka.picfort.core.ui.avatar.avatarColor
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.format.TextStyle
import java.util.Locale

private const val MAX_SUGGESTIONS = 8

@Composable
private fun FormColumn(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .imePadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) { content() }
}

// --- Child ---

/**
 * Pick who goes to school from the family (not everyone does). The faces as in the Wall's row;
 * members already in School are left out.
 */
@Composable
fun AddChildForm(candidates: List<FamilyMember>, saving: Boolean, onPick: (FamilyMember) -> Unit) {
    FormColumn {
        Text("Who goes to school?", style = MaterialTheme.typography.titleLarge)
        if (candidates.isEmpty()) {
            Text(
                "Everyone in the family is already here. Add someone in Settings → Family first.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                candidates.forEach { member ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(enabled = !saving, onClickLabel = "Add ${member.name}") { onPick(member) }
                            .padding(horizontal = 6.dp, vertical = 6.dp),
                    ) {
                        MemberAvatar(member, size = 56.dp)
                        Text(
                            member.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.width(64.dp).padding(top = 4.dp),
                        )
                    }
                }
            }
            Text(
                "Someone missing? Add them in Settings → Family.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * A child in School: the bell schedule and removing them. A child linked to a family member takes
 * name and face from Family; an older one (added by name) can still be renamed and recolored.
 */
@Composable
fun ChildForm(
    child: Child,
    avatar: FamilyMember,
    linked: Boolean,
    saving: Boolean,
    onSave: (name: String, color: String) -> Unit,
    onBells: () -> Unit,
    onDelete: (Long) -> Unit,
) {
    var name by remember { mutableStateOf(child.name) }
    var color by remember { mutableStateOf(child.color) }
    var confirmDelete by remember { mutableStateOf(false) }
    FormColumn {
        Row(verticalAlignment = Alignment.CenterVertically) {
            MemberAvatar(avatar, size = 48.dp)
            Spacer(Modifier.width(12.dp))
            Text(child.name, style = MaterialTheme.typography.titleLarge)
        }
        if (!linked) {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(50) },
                label = { Text("Name") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CHILD_COLORS.forEach { c ->
                    val picked = c == color
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(avatarColor(c))
                            .border(if (picked) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            .clickable { color = c }
                            .semantics {
                                contentDescription = c
                                selected = picked
                            },
                    ) {
                        if (picked) Icon(Icons.Default.Check, contentDescription = null, tint = Color.White)
                    }
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedButton(onClick = onBells) {
                Text(if (child.periods.isEmpty()) "Set up lesson times" else "Bell schedule · ${child.periods.size}")
            }
            Spacer(Modifier.weight(1f))
            if (!linked) {
                Button(onClick = { onSave(name, color) }, enabled = name.isNotBlank() && !saving) { Text("Save") }
            }
        }
        HorizontalDivider()
        TextButton(onClick = { confirmDelete = true }) {
            Text("Remove from School", color = MaterialTheme.colorScheme.error)
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            text = {
                Text(
                    "Remove ${child.name} from School? The whole timetable is deleted, for everyone in the family. " +
                        "${child.name} stays in the family.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(child.id)
                }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

// --- Lesson or activity ---

/**
 * Add or edit a lesson or activity on [day]. "Save and add next" keeps the form open on the next
 * free period, so a whole day goes in at once. [onSave]'s `saved` resets the form for the next one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EntryForm(
    child: Child,
    entry: SchoolEntry?,
    day: DayOfWeek,
    locale: Locale,
    time: (LocalTime) -> String,
    saving: Boolean,
    onSave: (draft: EntryDraft, addNext: Boolean, saved: () -> Unit) -> Unit,
    onDelete: (Long) -> Unit,
    onSetUpBells: () -> Unit,
) {
    var kind by remember { mutableStateOf(entry?.kind ?: EntryKind.LESSON) }
    var title by remember { mutableStateOf(entry?.title.orEmpty()) }
    var place by remember { mutableStateOf(entry?.place.orEmpty()) }
    var teacher by remember { mutableStateOf(entry?.teacher.orEmpty()) }
    var note by remember { mutableStateOf(entry?.note.orEmpty()) }
    var period by remember {
        mutableStateOf(if (entry == null) firstFreePeriod(child, day) else entry.period)
    }
    var custom by remember {
        mutableStateOf(entry?.let { it.period == null } ?: (firstFreePeriod(child, day) == null))
    }
    var start by remember { mutableStateOf(entry?.start) }
    var end by remember { mutableStateOf(entry?.end) }
    var added by remember { mutableStateOf(0) }

    val lesson = kind == EntryKind.LESSON
    val byPeriod = lesson && !custom && period != null
    val timesOk = byPeriod || (start != null && end != null && end!! > start!!)
    val draft = EntryDraft(
        kind = kind,
        weekday = day,
        title = title,
        period = period.takeIf { byPeriod },
        start = start,
        end = end,
        place = place,
        teacher = teacher.takeIf { lesson },
        note = note,
    )
    val reset: () -> Unit = {
        added += 1
        val previous = period
        title = ""
        place = ""
        teacher = ""
        note = ""
        if (byPeriod) {
            val next = firstFreePeriod(child, day, after = previous)
            if (next != null) {
                period = next
            } else {
                period = null
                custom = true
            }
        } else if (end != null) {
            val length = java.time.Duration.between(start, end)
            start = end
            end = end!!.plus(length).takeIf { it > start!! }
        }
    }

    FormColumn {
        val dayName = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
        Text(
            when {
                entry != null -> "Edit · $dayName"
                added > 0 -> "Added $added · next on $dayName"
                else -> "Add to $dayName"
            },
            style = MaterialTheme.typography.titleLarge,
        )
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            EntryKind.entries.forEachIndexed { i, k ->
                SegmentedButton(
                    selected = kind == k,
                    onClick = { kind = k },
                    shape = SegmentedButtonDefaults.itemShape(i, EntryKind.entries.size),
                ) { Text(if (k == EntryKind.LESSON) "Lesson" else "Activity") }
            }
        }
        if (lesson) {
            if (child.periods.isEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = onSetUpBells) { Text("Set up lesson times first") }
                    Text("or a custom time:", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                Text("Period", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    child.periods.forEach { p ->
                        FilterChip(
                            selected = !custom && period == p.number,
                            onClick = {
                                custom = false
                                period = p.number
                            },
                            label = { Text("${p.number} · ${time(p.start)}") },
                        )
                    }
                    FilterChip(
                        selected = custom,
                        onClick = {
                            custom = true
                            if (start == null || end == null) {
                                child.periods.firstOrNull { it.number == period }?.let {
                                    if (start == null) start = it.start
                                    if (end == null) end = it.end
                                }
                            }
                        },
                        label = { Text("Custom time") },
                    )
                }
            }
        }
        if (!lesson || custom || child.periods.isEmpty()) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                TimeField("Start", start, time) { start = it }
                Text("–")
                TimeField("End", end, time) { end = it }
            }
            if (start != null && end != null && end!! <= start!!) {
                Text("The end must be after the start", color = MaterialTheme.colorScheme.error)
            }
        }
        OutlinedTextField(
            value = title,
            onValueChange = { title = it.take(100) },
            label = { Text(if (lesson) "Subject" else "Activity") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            modifier = Modifier.fillMaxWidth(),
        )
        if (lesson) {
            val suggestions = remember(child, title) {
                subjects(child).filter {
                    it.startsWith(title.trim(), ignoreCase = true) && !it.equals(title.trim(), ignoreCase = true)
                }.take(MAX_SUGGESTIONS)
            }
            if (suggestions.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.forEach { s ->
                        SuggestionChip(onClick = {
                            title = s
                            if (teacher.isBlank()) teacher = teacherOf(child, s).orEmpty()
                        }, label = { Text(s) })
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = place,
                onValueChange = { place = it.take(100) },
                label = { Text(if (lesson) "Room" else "Place") },
                singleLine = true,
                modifier = Modifier.weight(if (lesson) 0.4f else 1f),
            )
            if (lesson) {
                OutlinedTextField(
                    value = teacher,
                    onValueChange = { teacher = it.take(100) },
                    label = { Text("Teacher") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier.weight(0.6f),
                )
            }
        }
        OutlinedTextField(
            value = note,
            onValueChange = { note = it.take(500) },
            label = { Text("Note") },
            placeholder = { Text(if (lesson) "e.g. gym clothes" else "e.g. bring swimsuit") },
            modifier = Modifier.fillMaxWidth(),
        )
        val canSave = title.isNotBlank() && timesOk && !saving
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (entry != null) {
                TextButton(onClick = { onDelete(entry.id) }, enabled = !saving) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            }
            Spacer(Modifier.weight(1f))
            if (entry == null) {
                OutlinedButton(onClick = {
                    onSave(draft, true, reset)
                }, enabled = canSave) { Text("Save and add next") }
            }
            Button(onClick = { onSave(draft, false, {}) }, enabled = canSave) { Text("Save") }
        }
    }
}

// --- Bell schedule ---

/** A child's period times. Fill in generates them; every time stays editable (long breaks). */
@Composable
fun BellsForm(
    child: Child,
    others: List<Child>,
    time: (LocalTime) -> String,
    saving: Boolean,
    onSave: (List<Period>) -> Unit,
    onCopyFrom: (Long) -> Unit,
) {
    val periods = remember { mutableStateListOf<Period>().apply { addAll(child.periods.sortedBy { it.number }) } }
    var first by remember { mutableStateOf(LocalTime.of(8, 0)) }
    var length by remember { mutableStateOf("45") }
    var pause by remember { mutableStateOf("10") }
    var count by remember { mutableStateOf("8") }
    val valid = periods.all { it.end > it.start }

    FormColumn {
        Text("${child.name} · lesson times", style = MaterialTheme.typography.titleLarge)
        periods.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("${p.number}", style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(24.dp))
                TimeField("Start", p.start, time) { periods[i] = p.copy(start = it) }
                Text("–")
                TimeField("End", p.end, time) { periods[i] = p.copy(end = it) }
                IconButton(onClick = { periods.removeAt(i) }) {
                    Icon(Icons.Default.Close, contentDescription = "Remove period ${p.number}")
                }
            }
        }
        if (!valid) Text("Each period must end after it starts", color = MaterialTheme.colorScheme.error)
        val canAddPeriod = (periods.lastOrNull()?.number ?: 0) < 15
        TextButton(
            onClick = {
                val last = periods.lastOrNull()
                val start = last?.end?.plusMinutes(10) ?: LocalTime.of(8, 0)
                periods += Period((last?.number ?: 0) + 1, start, start.plusMinutes(45))
            },
            enabled = canAddPeriod,
        ) { Text("Add period") }

        HorizontalDivider()
        Text("Generate times", style = MaterialTheme.typography.titleMedium)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("First lesson", style = MaterialTheme.typography.bodyMedium)
            TimeField("First lesson", first, time) { first = it }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NumberField("Lesson, min", length, Modifier.weight(1f)) { length = it }
            NumberField("Break, min", pause, Modifier.weight(1f)) { pause = it }
            NumberField("Lessons", count, Modifier.weight(1f)) { count = it }
        }
        val l = length.toIntOrNull()
        val b = pause.toIntOrNull()
        val n = count.toIntOrNull()
        OutlinedButton(
            onClick = {
                periods.clear()
                periods += fillBells(first, l!!, b!!, n!!)
            },
            enabled = l != null && l in 5..180 && b != null && b in 0..120 && n != null && n in 1..15,
        ) { Text("Fill in") }
        others.forEach { other ->
            TextButton(onClick = { onCopyFrom(other.id) }, enabled = !saving) { Text("Copy from ${other.name}") }
        }
        Row {
            Spacer(Modifier.weight(1f))
            Button(onClick = { onSave(periods.toList()) }, enabled = valid && !saving) { Text("Save") }
        }
    }
}

@Composable
private fun NumberField(label: String, value: String, modifier: Modifier, onChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { onChange(it.filter(Char::isDigit).take(3)) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

// --- Copy a day ---

/** Copies the day's lessons to other days; days that have lessons are replaced (said first). */
@Composable
fun CopyDayDialog(
    child: Child,
    from: DayOfWeek,
    locale: Locale,
    onDismiss: () -> Unit,
    onCopy: (List<DayOfWeek>) -> Unit,
) {
    val picked = remember { mutableStateListOf<DayOfWeek>() }
    val replaced = picked.filter { day -> child.entries.any { it.weekday == day && it.isLesson } }.sorted()
    fun name(day: DayOfWeek) = day.getDisplayName(TextStyle.FULL_STANDALONE, locale)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Copy ${name(from)}'s lessons to") },
        text = {
            Column {
                DayOfWeek.entries.filter { it != from }.forEach { day ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { if (day in picked) picked.remove(day) else picked.add(day) },
                    ) {
                        Checkbox(checked = day in picked, onCheckedChange = null, modifier = Modifier.padding(8.dp))
                        Text(name(day))
                    }
                }
                if (replaced.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Lessons on ${replaced.joinToString { name(it) }} will be replaced.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onCopy(picked.sorted()) }, enabled = picked.isNotEmpty()) {
                Text(if (replaced.isEmpty()) "Copy" else "Replace")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

// --- Time ---

/** A time button opening the Material time picker. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeField(label: String, value: LocalTime?, time: (LocalTime) -> String, onPick: (LocalTime) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val spoken = "$label, ${value?.let(time) ?: "not set"}"
    OutlinedButton(
        onClick = { open = true },
        modifier = Modifier.widthIn(min = 92.dp).semantics { contentDescription = spoken },
    ) {
        Text(value?.let(time) ?: label)
    }
    if (open) {
        val state = rememberTimePickerState(
            initialHour = value?.hour ?: 8,
            initialMinute = value?.minute ?: 0,
            is24Hour = !time(LocalTime.of(13, 0)).contains(Regex("[AaPp]")),
        )
        AlertDialog(
            onDismissRequest = { open = false },
            title = { Text(label) },
            text = { TimePicker(state = state) },
            confirmButton = {
                TextButton(onClick = {
                    onPick(LocalTime.of(state.hour, state.minute))
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        )
    }
}
