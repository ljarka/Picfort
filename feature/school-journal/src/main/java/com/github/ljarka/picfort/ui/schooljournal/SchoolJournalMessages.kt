package com.github.ljarka.picfort.ui.schooljournal

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
        .distinctBy { it.id }
        .sortedByDescending { it.sentAt }
    return UnreadMessages(unread, gone)
}

internal data class UnreadMessages(val items: List<MessageSummary>, val gone: Set<String>)

fun Message.summary() = MessageSummary(
    id = id,
    childId = childId,
    sender = sender,
    subject = subject,
    sentAt = sentAt,
    hasAttachments = hasAttachments,
    read = read,
    readAt = readAt,
    deletedAt = deletedAt,
)
