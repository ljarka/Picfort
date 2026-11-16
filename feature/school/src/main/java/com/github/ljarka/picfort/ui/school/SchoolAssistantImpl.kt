package com.github.ljarka.picfort.ui.school

import com.github.ljarka.picfort.core.model.AssistantSchoolEntry
import com.github.ljarka.picfort.core.model.AssistantSchoolResult
import com.github.ljarka.picfort.core.model.SchoolAssistantProvider
import java.text.Normalizer
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SchoolAssistantImpl @Inject constructor(private val repository: SchoolRepository) : SchoolAssistantProvider {
    override suspend fun getChildTimetable(childName: String, dateIso: String?): AssistantSchoolResult {
        val date = dateIso?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: LocalDate.now()
        repository.refresh()
        val children = repository.children.value
        if (children.isEmpty()) {
            return AssistantSchoolResult(
                found = false,
                date = date.toString(),
                message = "No school timetables available.",
            )
        }
        val target = findMatchingChild(children, childName)
            ?: return AssistantSchoolResult(
                found = false,
                date = date.toString(),
                message = "No child matching \"$childName\". Available: ${children.joinToString { it.name }}.",
            )

        val entries = target.on(date.dayOfWeek)
        val formatter = DateTimeFormatter.ofPattern("HH:mm")
        val mapped = entries.map { e ->
            AssistantSchoolEntry(
                title = e.title,
                kind = e.kind.wire,
                startTime = e.start.format(formatter),
                endTime = e.end.format(formatter),
                location = e.place,
            )
        }
        val day = schoolDay(entries, date.dayOfWeek, LocalTime.MIN)
        val lessonsEnd = when (val status = day.status) {
            is SchoolStatus.Today -> status.finishesAt.format(formatter)
            is SchoolStatus.Finished -> status.finishedAt.format(formatter)
            SchoolStatus.NoSchool -> null
        }
        val allEnd = entries.maxOfOrNull { it.end }?.format(formatter)
        val finishesAt = allEnd ?: lessonsEnd
        val msg = when {
            lessonsEnd != null && allEnd != null && allEnd != lessonsEnd ->
                "${target.name} finishes lessons at $lessonsEnd and activities at $allEnd on $date."

            finishesAt != null ->
                "${target.name} finishes at $finishesAt on $date."

            else ->
                "${target.name} has no classes on $date."
        }
        return AssistantSchoolResult(
            found = true,
            childName = target.name,
            date = date.toString(),
            finishesAt = finishesAt,
            entries = mapped,
            message = msg,
        )
    }

    private fun findMatchingChild(children: List<Child>, query: String): Child? {
        val normQuery = normalizeName(query)
        return children.firstOrNull { normalizeName(it.name) == normQuery }
            ?: children.firstOrNull { normalizeName(it.name).contains(normQuery) }
            ?: children.firstOrNull { normQuery.contains(normalizeName(it.name)) }
    }

    private fun normalizeName(value: String): String =
        Normalizer.normalize(value.trim().lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{InCombiningDiacriticalMarks}+"), "")
            .replace("ł", "l")
}
