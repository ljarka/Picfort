package com.github.ljarka.picfort.ui.schooljournal

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** One page of the messages list (spec §4.3). */
internal const val MESSAGES_PAGE = 50

/** Unread-only reads at most this many pages after the watermark (10 × 100 messages). */
internal const val UNREAD_MAX_PAGES = 10

/**
 * The messages this user hasn't read (spec §4.6): everything sent after the watermark that isn't
 * in `readIds`, plus each message marked unread on purpose (`GET /messages/{id}`; one that is gone
 * returns 404 and is reported in [UnreadMessages.gone] so the caller can forget it).
 * [child]: only this child's messages. Newest first.
 */
internal suspend fun loadUnread(
    api: SchoolJournalApi,
    c: Connection,
    state: ReadState,
    child: String?,
    includeDeleted: Boolean = false,
    /** Null = messages and announcements. Filtered here, so read state stays one list. */
    kind: MessageKind? = null,
): UnreadMessages {
    val recent = mutableListOf<MessageSummary>()
    var cursor: String? = null
    var pages = 0
    do {
        val page = api.messages(
            c,
            child = child,
            since = state.watermark,
            limit = 100,
            cursor = cursor,
            includeDeleted = includeDeleted,
        )
        recent += page.items
        cursor = page.nextCursor
        pages++
    } while (cursor != null && pages < UNREAD_MAX_PAGES)

    val gone = mutableSetOf<String>()
    val marked = state.unreadIds.filter { id -> recent.none { it.id == id } }.mapNotNull { id ->
        try {
            api.message(c, id).summary()
        } catch (e: SchoolJournalError.NotFound) {
            gone += id
            null
        }
    }.filter { child == null || it.childId == child }

    val unread = (recent + marked)
        .filter { !state.isRead(it) && (includeDeleted || it.deletedAt == null) }
        .filter { kind == null || it.kind == kind }
        .distinctBy { it.id }
        .sortedByDescending { it.sentAt }
    return UnreadMessages(unread, gone)
}

internal data class UnreadMessages(val items: List<MessageSummary>, val gone: Set<String>)

fun Message.summary() = MessageSummary(
    id = id,
    childId = childId,
    type = type,
    sender = sender,
    subject = subject,
    sentAt = sentAt,
    hasAttachments = hasAttachments,
    read = read,
    readAt = readAt,
    deletedAt = deletedAt,
    validFrom = validFrom,
    validTo = validTo,
)

/** Who to show: the sender, or "Notice board" for an announcement without an author. */
fun senderLabel(kind: MessageKind, sender: String): String =
    if (kind == MessageKind.ANNOUNCEMENT && sender.isBlank()) "Notice board" else sender

/** When an announcement is on the school's board ("On the board 18 Nov – 20 Nov"); null if unknown. */
fun boardDates(validFrom: String?, validTo: String?, locale: Locale): String? {
    val format = DateTimeFormatter.ofPattern("d MMM", locale)
    fun date(text: String?) = text?.let { runCatching { LocalDate.parse(it) }.getOrNull() }?.format(format)
    val from = date(validFrom)
    val to = date(validTo)
    return when {
        from != null && to != null && from == to -> "On the board on $from"
        from != null && to != null -> "On the board $from – $to"
        to != null -> "On the board until $to"
        from != null -> "On the board from $from"
        else -> null
    }
}
