# T-078 — School journal: messages

**Status:** ready  
**Component:** Android (`:feature:school-journal`, `:app` route)  
**Depends on:** T-076. Spec §4.3.

## Steps

1. Messages tab: paged list (`limit=50`, `cursor`), infinite scroll, pull-to-refresh, Unread only
   chip (local read state: `since=<watermark>` minus `readIds` + `unreadIds`, spec §4.6),
   Show removed (overflow), child name with All. Bold/dot from `isRead`, never the server's `read`.
2. Row actions: Mark as read / unread (long-press menu and swipe) → `ReadStateStore`.
3. Overflow Mark all as read (selected child or All, spec §4.6) → snackbar with the count.
4. `:app`: `Screen.SchoolJournalMessage(id)` + NavHost entry next to `Screen.SchoolJournal`;
   `SchoolJournalScreen(onBack, onOpenMessage)`.
   `SchoolJournalMessageScreen(messageId, onBack)`: header, `AnnotatedString.fromHtml(bodyHtml)` with
   clickable links (`LocalUriHandler`), fallback `bodyText`, attachment names + note; marks read on
   open (locally); Mark as unread action; 404 → back + remove row (and its id from the read state).
5. After any read change → `SchoolJournalBadgeRepository.refresh()`.

## Tests

- Paging (cursor → null stops), unread filter, includeDeleted, read/unread around the watermark,
  read all per child vs All, 404.
- Paparazzi: list (unread/read/removed/attachments, All vs one child), empty, message screen.

## Result (2026-11-17)

- [x] Messages tab (paging 50 + infinite scroll, pull-to-refresh, Unread only, Show removed, child
  names with All), long-press Mark as read / unread, Mark all as read (child or All) with snackbar,
  message screen (`fromHtml` with links, attachments, read on open, Mark as unread, 404 → back and row
  gone), every read change refreshes the dot. Back from a message keeps the loaded pages.
- [x] Unit tests (read state around the watermark, read all per child vs All, unread list paging,
  marked-unread one by one, 404 → gone, child/removed filters, dates) and Paparazzi (list All / one
  child dark / unread-only empty, message screen light / removed unread dark).
- [x] Emulator + fake server (+ picfort_test): list, new message → bold + dot on Wall, open → read
  and dot gone, long-press unread → dot back, Mark all as read → "Marked 1 message as read", Unread only
  → "No unread messages", 60 extra messages → second page loads while scrolling.
- Changed from the plan: no swipe action (long-press menu only; swipe-to-act clashes with the
  pull-to-refresh and tab gestures and adds little). The child's name on the message screen comes from
  a second request, so it appears a moment later.
- The fake server's ids are now unique across resets and restarts (the app remembers read ids).
- A message read by one parent stays unread for the other: covered by tests (state file per Immich
  user); a second Immich user on the emulator was not tried.

## Done when

- Messages list, details, paging and filters work; reading clears the badge only for the user
  who read it. Server read endpoints stay unused (ADR 0017).
