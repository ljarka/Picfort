# T-082 — School journal: background notifications

**Status:** ready  
**Component:** Android (`:feature:school-journal`, `:app` two lines)  
**Depends on:** T-077, T-078. Spec §4.7. Owner decision 2026-11-17: notifications are wanted.

## Steps

1. `SchoolJournalNotifyWorker` (`@HiltWorker`, `CoroutineWorker`): periodic every 15 min (WorkManager
   minimum; the server itself checks every 15 min), `NetworkType.CONNECTED`, unique name
   `school-journal-notify`, `KEEP`. Reads `GET /changes?after=<notifyCursor>&limit=100` while
   `hasMore`, then saves `notifyCursor = nextCursor`.
2. `SchoolJournalNotifyScheduler.start()`: enqueue when configured and notifications on, cancel
   otherwise; called from `PicfortApplication.onCreate` (one line, like `reminderScheduler.start()`)
   and after Connect / Disconnect / the toggle.
3. What notifies (spec §4.7): grade `created`, grade `updated` with `value` in `diff`, message
   `created` not already read on this phone. Nothing for `deleted` or other updates.
4. Notifications: channel "School journal" (default importance), group per child; > 3 in one run
   for a child → one summary "Ola: 2 new grades, 1 message". Tap → launch intent with
   `EXTRA_OPEN_SCHOOL_JOURNAL` (`messages:<id>` | `grades`); `MainActivity` navigates like
   `EXTRA_OPEN_EVENT_ID`. Reading a message in the app cancels its notification.
5. Server settings: switch **Notifications** (on by default); on Connect with it on, ask for
   `POST_NOTIFICATIONS` (Android 13+); denied → switch off with a hint to system settings.
6. Errors (offline, 401, 5xx): no notification, retry next period; cursor unchanged.

## Tests

- Event filter (created/updated-with-value/deleted/other), already-read message skipped,
  summary threshold, cursor only saved after success, start cursor = `lastEventId`, per Immich user.
- Device: fake server adds a grade → notification within one run (trigger with
  `adb shell cmd jobscheduler run` / WorkManager test driver), tap opens the screen.

## Done when

- A new grade or message on the server shows a notification on each parent's phone without
  opening the app; removal checklist in ADR 0017 includes the two `:app` lines.
