package com.github.ljarka.picfort.ui.schooljournal

/** One child's subjects; [childName] only when all children are shown (one section each). */
data class GradeSection(val childId: String?, val childName: String?, val subjects: List<SubjectSummary>)

/** Change events read per request when looking for new grades. */
internal const val NEW_GRADES_PAGE = 500

/**
 * Ids of grades created or changed after [after] (spec §4.4): the "New" markers. Removed grades
 * aren't shown, so their events don't count.
 */
internal suspend fun loadNewGradeIds(api: SchoolJournalApi, c: Connection, after: Long): Set<String> {
    val ids = mutableSetOf<String>()
    var cursor = after
    do {
        val page = api.changes(c, after = cursor, type = "grade", limit = NEW_GRADES_PAGE)
        page.items.filter { it.isGrade && it.changeType != "deleted" }.forEach { ids += it.entityId }
        if (page.nextCursor <= cursor) break
        cursor = page.nextCursor
    } while (page.hasMore)
    return ids
}

/** The semester of the newest grade (spec §4.4), or 1 when there are none. */
fun defaultSemester(sections: List<GradeSection>): Int = sections.flatMap { s -> s.subjects.flatMap { it.grades } }
    .maxWithOrNull(compareBy<Grade>({ it.issuedOn }, { it.firstSeenAt }))
    ?.semester ?: 1

/** Subjects with a new grade first, then as the server sends them (alphabetical). */
fun orderSubjects(subjects: List<SubjectSummary>, newIds: Set<String>): List<SubjectSummary> =
    subjects.sortedByDescending { s -> s.grades.any { it.id in newIds } }

/** Regular grades as chips, oldest first; the other kinds are labelled values on the card. */
fun SubjectSummary.regularGrades(): List<Grade> =
    grades.filter { it.gradeKind == GradeKind.REGULAR || it.gradeKind == GradeKind.UNKNOWN }
        .sortedWith(compareBy({ it.issuedOn }, { it.firstSeenAt }))

fun SubjectSummary.labelledGrades(): List<Pair<String, Grade>> = grades.mapNotNull { g ->
    when (g.gradeKind) {
        GradeKind.PROPOSED_SEMESTER -> "Proposed for the semester" to g
        GradeKind.SEMESTER -> "Semester" to g
        GradeKind.PROPOSED_FINAL -> "Proposed final" to g
        GradeKind.FINAL -> "Final" to g
        GradeKind.REGULAR, GradeKind.UNKNOWN -> null
    }
}

val GradeKind.label: String
    get() = when (this) {
        GradeKind.REGULAR -> "Regular"
        GradeKind.PROPOSED_SEMESTER -> "Proposed for the semester"
        GradeKind.SEMESTER -> "Semester"
        GradeKind.PROPOSED_FINAL -> "Proposed final"
        GradeKind.FINAL -> "Final"
        GradeKind.UNKNOWN -> "Other"
    }

/** "4.33", or "—" without an average. */
fun averageText(average: Double?): String = average?.let { "%.2f".format(java.util.Locale.ROOT, it) } ?: "—"

/** Color band of a 1–6 grade (spec §4.4); null for "np", "+" and other non-numbers. */
enum class GradeBand { EXCELLENT, GOOD, FAIR, WEAK, FAILING }

fun gradeBand(numericValue: Double?): GradeBand? = when {
    numericValue == null -> null
    numericValue >= 5.0 -> GradeBand.EXCELLENT
    numericValue >= 4.0 -> GradeBand.GOOD
    numericValue >= 3.0 -> GradeBand.FAIR
    numericValue >= 2.0 -> GradeBand.WEAK
    else -> GradeBand.FAILING
}
