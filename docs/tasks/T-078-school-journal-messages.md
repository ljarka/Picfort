# T-078 — School journal: messages

**Status:** ready  
**Component:** Android (`:feature:school-journal`, `:app` route)  
**Depends on:** T-076. Spec §4.3.

## Steps

1. Messages tab: paged list (`limit=50`, `cursor`), infinite scroll, pull-to-refresh, Unread only
   chip (local read state: `since=<watermark>` minus `readIds` + `unreadIds`, spec §4.7),
   Show removed (overflow), child name with All. Bold/dot from `isRead`, never the server's `read`.
2. Row actions: Mark as read / unread (long-press menu and swipe) → `ReadStateStore`.
3. Overflow Mark all as read (selected child or All, spec §4.7) → snackbar with the count.
4. `SchoolJournalMessageScreen(messageId, onBack)`: header, `AnnotatedString.fromHtml(bodyHtml)` with
   clickable links (`LocalUriHandler`), fallback `bodyText`, attachment names + note; marks read on
   open (locally); Mark as unread action; 404 → back + remove row (and its id from the read state).
5. After any read change → `SchoolJournalBadgeRepository.refresh()`.

## Tests

- Paging (cursor → null stops), unread filter, includeDeleted, read/unread around the watermark,
  read all per child vs All, 404.
- Paparazzi: list (unread/read/removed/attachments, All vs one child), empty, message screen.

## Done when

- Messages list, details, paging and filters work; reading clears the badge only for the user
  who read it. Server read endpoints stay unused (ADR 0017).
