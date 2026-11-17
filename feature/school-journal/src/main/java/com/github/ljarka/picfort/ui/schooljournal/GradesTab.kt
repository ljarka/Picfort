package com.github.ljarka.picfort.ui.schooljournal

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private val SEMESTERS = listOf(1 to "Semester 1", 2 to "Semester 2", null to "Whole year")

/** The Grades tab (spec §4.4): semester chips, one card per subject, a sheet per grade. */
@Composable
fun GradesTab(state: SchoolJournalUi, clock: JournalClock, actions: SchoolJournalActions) {
    val grades = state.grades
    var open by remember { mutableStateOf<Pair<Grade, String>?>(null) }
    LazyColumn(Modifier.fillMaxSize()) {
        item(key = "semesters") {
            Row(
                Modifier.fillMaxWidth().horizontalScroll(
                    rememberScrollState(),
                ).padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SEMESTERS.forEach { (semester, label) ->
                    FilterChip(
                        selected = grades.semesterChosen && grades.semester == semester,
                        onClick = { actions.onSelectSemester(semester) },
                        label = { Text(label) },
                    )
                }
            }
        }
        val empty = grades.sections.all { it.subjects.isEmpty() }
        when {
            grades.loading && !grades.loaded -> item(key = "loading") {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }

            grades.loaded && empty -> item(key = "empty") {
                Text(
                    if (grades.semester == null) "No grades yet" else "No grades in this semester",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                )
            }

            else -> grades.sections.forEach { section ->
                section.childName?.let { name ->
                    item(key = "child-${section.childId}") {
                        Text(
                            name,
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
                        )
                    }
                }
                items(orderSubjects(section.subjects, grades.newIds), key = {
                    "${section.childId}-${it.subject}"
                }) { subject ->
                    SubjectCard(subject, grades.newIds, onOpen = { open = it to subject.subject })
                }
            }
        }
        item(key = "bottom") { Spacer(Modifier.height(24.dp)) }
    }
    open?.let { (grade, subject) -> GradeSheet(grade, subject, grade.id in grades.newIds, clock) { open = null } }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectCard(subject: SubjectSummary, newIds: Set<String>, onOpen: (Grade) -> Unit) {
    OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(subject.subject, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    "Average ${averageText(subject.average)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val regular = subject.regularGrades()
            if (regular.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    regular.forEach { GradeChip(it, isNew = it.id in newIds) { onOpen(it) } }
                }
            }
            subject.labelledGrades().forEach { (label, grade) ->
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onOpen(grade)
                    },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    if (grade.id in newIds) NewDot()
                    Spacer(Modifier.size(6.dp))
                    Text(grade.value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** A grade: its value on the band's color, the weight small in the corner, a dot when new. */
@Composable
private fun GradeChip(grade: Grade, isNew: Boolean, onClick: () -> Unit) {
    val (container, content) = bandColors(gradeBand(grade.numericValue))
    val description = buildString {
        append("${grade.value}, ${grade.category}")
        grade.weight?.let { append(", weight $it") }
        if (isNew) append(", new")
    }
    Box {
        Box(
            Modifier
                .defaultMinSize(minWidth = 44.dp, minHeight = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(container)
                .clickable(onClick = onClick)
                .semantics { contentDescription = description }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                grade.value,
                color = content,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            grade.weight?.let {
                Text(
                    "$it",
                    color = content.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(start = 18.dp, top = 18.dp),
                )
            }
        }
        if (isNew) NewDot(Modifier.align(Alignment.TopEnd).padding(2.dp))
    }
}

@Composable
private fun NewDot(modifier: Modifier = Modifier) {
    Box(modifier.size(10.dp).clip(CircleShape).background(MaterialTheme.colorScheme.error))
}

/** Container and text colors per band, readable in light and dark themes; neutral for non-numbers. */
@Composable
private fun bandColors(band: GradeBand?): Pair<Color, Color> {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    if (band == null) return MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    val container = when (band) {
        GradeBand.EXCELLENT -> if (dark) 0xFF2E5B33 else 0xFFC8E6C9
        GradeBand.GOOD -> if (dark) 0xFF294A66 else 0xFFD0E4F5
        GradeBand.FAIR -> if (dark) 0xFF5C4B12 else 0xFFFFF0C2
        GradeBand.WEAK -> if (dark) 0xFF6B3A12 else 0xFFFFDDC2
        GradeBand.FAILING -> if (dark) 0xFF6E2626 else 0xFFFFD2D2
    }
    return Color(container) to if (dark) Color(0xFFF2F0F4) else Color(0xFF1C1B1F)
}

/** Every field of one grade (spec §4.4). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GradeSheet(grade: Grade, subject: String, isNew: Boolean, clock: JournalClock, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss) { GradeDetails(grade, subject, isNew, clock) }
}

@Composable
fun GradeDetails(grade: Grade, subject: String, isNew: Boolean, clock: JournalClock) {
    fun at(text: String?) =
        instantOrNull(text)?.let { whenText(it, clock.now, clock.zone, clock.locale, clock.is24Hour, withToday = true) }
    val issued = runCatching {
        LocalDate.parse(grade.issuedOn).format(DateTimeFormatter.ofPattern("d MMM yyyy", clock.locale))
    }
        .getOrDefault(grade.issuedOn)
    Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(grade.value, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.size(16.dp))
            Column {
                Text(subject, style = MaterialTheme.typography.titleMedium)
                Text(
                    grade.category,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (isNew) {
                Spacer(Modifier.weight(1f))
                Text("New", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.labelLarge)
            }
        }
        grade.comment?.takeIf { it.isNotBlank() }?.let {
            Spacer(Modifier.height(12.dp))
            Text(it, style = MaterialTheme.typography.bodyLarge)
        }
        Spacer(Modifier.height(12.dp))
        DetailRow("Kind", grade.gradeKind.label)
        DetailRow("Weight", grade.weight?.toString() ?: "—")
        DetailRow("As a number", grade.numericValue?.let { averageText(it) } ?: "—")
        DetailRow("Counts to the average", if (grade.countsToAverage) "Yes" else "No")
        DetailRow("Semester", grade.semester.toString())
        DetailRow("Teacher", grade.teacher.ifBlank { "—" })
        DetailRow("Issued on", issued)
        at(grade.firstSeenAt)?.let { DetailRow("First seen", it) }
        if (grade.updatedAt != grade.firstSeenAt) at(grade.updatedAt)?.let { DetailRow("Last changed", it) }
        at(grade.deletedAt)?.let { DetailRow("Removed from the e-journal", it) }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
    }
}
