package com.github.ljarka.picfort.ui.schooljournal

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

/**
 * The offline copy (spec §7): the last status, children, first page of messages and grades per
 * view, for one school-server address. Shown at once on open and when the server can't be
 * reached (read only). Shared by both Immich users on a phone: it is the server's data, not
 * anyone's read state.
 */
@Serializable
data class JournalCache(
    /** When the server last answered (ISO instant). */
    val savedAt: String = "",
    val status: JournalStatus? = null,
    val children: List<ChildAccount> = emptyList(),
    /** First page per [messagesKey]. */
    val messages: Map<String, List<MessageSummary>> = emptyMap(),
    /** Sections per [gradesKey]. */
    val grades: Map<String, CachedGrades> = emptyMap(),
)

@Serializable
data class CachedGrades(val semester: Int? = null, val sections: List<CachedSection> = emptyList())

@Serializable
data class CachedSection(
    val childId: String? = null,
    val childName: String? = null,
    val subjects: List<SubjectSummary> = emptyList(),
)

fun CachedSection.toSection() = GradeSection(childId, childName, subjects)

fun GradeSection.toCached() = CachedSection(childId, childName, subjects)

/** The messages view a cache entry belongs to (unread-only isn't cached: it depends on read state). */
fun messagesKey(child: String?, showRemoved: Boolean) = "${child ?: "all"}|${if (showRemoved) "removed" else "current"}"

/** One semester's grades for [child] ([semester] null = whole year). */
fun gradesKey(child: String?, semester: Int?) = "${child ?: "all"}|${semester?.toString() ?: "year"}"

/** The grades view last loaded for [child], whatever its semester: what opening offline shows. */
fun lastGradesKey(child: String?) = "${child ?: "all"}|last"

@Singleton
class SchoolJournalCacheStore @Inject constructor(@ApplicationContext private val context: Context) {
    fun load(address: String): JournalCache? = file(address).takeIf { it.exists() }?.let { f ->
        runCatching { journalJson.decodeFromString(JournalCache.serializer(), f.readText()) }.getOrNull()
    }

    /** Changes the copy and stamps [JournalCache.savedAt]: call only after the server answered. */
    @Synchronized
    fun update(address: String, now: Instant = Instant.now(), transform: (JournalCache) -> JournalCache) {
        val next = transform(load(address) ?: JournalCache()).copy(savedAt = now.toString())
        val f = file(address)
        f.parentFile?.mkdirs()
        val tmp = File(f.parentFile, f.name + ".tmp")
        tmp.writeText(journalJson.encodeToString(JournalCache.serializer(), next))
        tmp.renameTo(f)
    }

    private fun file(address: String) = File(journalDir(context), "cache-${shortHash(address)}.json")
}
