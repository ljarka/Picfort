package com.github.ljarka.picfort.ui.schooljournal

import java.time.Instant
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/*
 * The school-server `/api/v1` contract (its `docs/api/openapi.yaml`). Times stay strings on the
 * wire and are parsed where used. The server only ever adds fields and enum values within v1:
 * unknown fields are ignored ([journalJson]) and enums are read leniently (`…Of` functions).
 */

/** Lenient JSON: unknown fields ignored, missing optional fields defaulted. */
val journalJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    coerceInputValues = true
}

@Serializable
data class HealthDto(val status: String = "")

@Serializable
data class ChildAccount(val id: String, val login: String = "", val name: String = "")

@Serializable
data class ChildrenDto(val children: List<ChildAccount> = emptyList())

@Serializable
data class Grade(
    val id: String,
    val childId: String = "",
    val subject: String = "",
    /** As in the e-journal: "5", "4+", "np", "+". */
    val value: String = "",
    /** "4+" → 4.5; null when not a number. */
    val numericValue: Double? = null,
    val weight: Int? = null,
    val category: String = "",
    val semester: Int = 1,
    val kind: String = "regular",
    val countsToAverage: Boolean = false,
    val teacher: String = "",
    val comment: String? = null,
    /** `YYYY-MM-DD`, school-local. */
    val issuedOn: String = "",
    val firstSeenAt: String = "",
    val updatedAt: String = "",
    val deletedAt: String? = null,
) {
    val gradeKind: GradeKind get() = GradeKind.of(kind)
}

enum class GradeKind(val wire: String) {
    REGULAR("regular"),
    PROPOSED_SEMESTER("proposed_semester"),
    SEMESTER("semester"),
    PROPOSED_FINAL("proposed_final"),
    FINAL("final"),
    UNKNOWN(""),
    ;

    companion object {
        fun of(wire: String): GradeKind = entries.firstOrNull { it.wire == wire && it != UNKNOWN } ?: UNKNOWN
    }
}

@Serializable
data class SubjectSummary(val subject: String, val average: Double? = null, val grades: List<Grade> = emptyList())

@Serializable
data class SubjectsDto(val items: List<SubjectSummary> = emptyList())

@Serializable
data class Attachment(val name: String)

@Serializable
data class MessageSummary(
    val id: String,
    val childId: String = "",
    val sender: String = "",
    val subject: String = "",
    val sentAt: String = "",
    val hasAttachments: Boolean = false,
    /** The server's shared read state: not used by Picfort (ADR 0017, read state per Immich user). */
    val read: Boolean = false,
    val readAt: String? = null,
    val deletedAt: String? = null,
    /** `message` or `announcement` (notice board); see [kind]. Last, so positional calls keep working. */
    val type: String = MESSAGE_TYPE_MESSAGE,
    /** Announcements: first / last day on the board (`yyyy-MM-dd`); null for messages or open-ended. */
    val validFrom: String? = null,
    val validTo: String? = null,
)

@Serializable
data class Message(
    val id: String,
    val childId: String = "",
    val sender: String = "",
    val subject: String = "",
    val sentAt: String = "",
    val hasAttachments: Boolean = false,
    val read: Boolean = false,
    val readAt: String? = null,
    val deletedAt: String? = null,
    val type: String = MESSAGE_TYPE_MESSAGE,
    val validFrom: String? = null,
    val validTo: String? = null,
    val bodyText: String = "",
    /** Sanitised by the server, safe to render. */
    val bodyHtml: String = "",
    val attachments: List<Attachment> = emptyList(),
)

const val MESSAGE_TYPE_MESSAGE = "message"
const val MESSAGE_TYPE_ANNOUNCEMENT = "announcement"

/** The two kinds of items in `/messages`; the API value is [apiValue] (its `type` / `?type=`). */
enum class MessageKind(val apiValue: String) {
    MESSAGE(MESSAGE_TYPE_MESSAGE),
    ANNOUNCEMENT(MESSAGE_TYPE_ANNOUNCEMENT),
    ;

    companion object {
        /** Unknown or missing values count as messages (school-server API guide). */
        fun of(type: String?): MessageKind = if (type == MESSAGE_TYPE_ANNOUNCEMENT) ANNOUNCEMENT else MESSAGE
    }
}

val MessageSummary.kind: MessageKind get() = MessageKind.of(type)
val Message.kind: MessageKind get() = MessageKind.of(type)

@Serializable
data class MessagesPage(val items: List<MessageSummary> = emptyList(), val nextCursor: String? = null)

@Serializable
data class ChangeEvent(
    val id: Long,
    val childId: String = "",
    val entityType: String = "",
    val entityId: String = "",
    val changeType: String = "",
    val detectedAt: String = "",
    /** A [Grade] or a [MessageSummary], by [entityType]; see [grade] / [message]. */
    val entity: JsonObject? = null,
    /** For `updated`: `{"field": [old, new]}`. */
    val diff: Map<String, JsonArray>? = null,
) {
    val isGrade: Boolean get() = entityType == "grade"
    val isMessage: Boolean get() = entityType == "message"

    fun grade(): Grade? = entity?.takeIf {
        isGrade
    }?.let { runCatching { journalJson.decodeFromJsonElement<Grade>(it) }.getOrNull() }

    fun message(): MessageSummary? = entity?.takeIf {
        isMessage
    }?.let { runCatching { journalJson.decodeFromJsonElement<MessageSummary>(it) }.getOrNull() }
}

@Serializable
data class ChangePage(
    val items: List<ChangeEvent> = emptyList(),
    val nextCursor: Long = 0,
    val hasMore: Boolean = false,
)

@Serializable
data class SyncRun(
    val startedAt: String = "",
    val finishedAt: String? = null,
    /** `running`, `ok`, `error`, `auth_failed`. */
    val status: String = "",
    val errorCode: String? = null,
    val itemsFetched: Int = 0,
    val changesDetected: Int = 0,
) {
    val failed: Boolean get() = status == "error" || status == "auth_failed"
    val running: Boolean get() = status == "running"
}

@Serializable
data class StatusCounts(val grades: Int = 0, val messages: Int = 0, val unreadMessages: Int = 0)

@Serializable
data class JournalStatus(
    /** `ok`, `auth_failed`, `unknown`. */
    val auth: String = "unknown",
    val lastSync: SyncRun? = null,
    val lastSuccessfulSyncAt: String? = null,
    val nextSyncAt: String? = null,
    val lastEventId: Long = 0,
    val counts: StatusCounts = StatusCounts(),
    val version: String = "",
) {
    val authFailed: Boolean get() = auth == "auth_failed"
}

@Serializable
data class SyncStarted(val status: String = "")

@Serializable
data class ErrorBody(val error: ErrorDetail? = null)

@Serializable
data class ErrorDetail(val code: String = "", val message: String = "")

/** An ISO 8601 instant from the server, or null when absent or unreadable. */
fun instantOrNull(text: String?): Instant? = text?.let { runCatching { Instant.parse(it) }.getOrNull() }
