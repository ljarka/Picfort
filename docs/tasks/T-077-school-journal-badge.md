# T-077 — School journal: "something new" badge

**Status:** ready  
**Component:** Android (`:feature:school-journal`)  
**Depends on:** T-076. Spec §6.

## Steps

1. `SchoolJournalBadgeRepository` (@Singleton): `hasNew: StateFlow<Boolean>` and per-tab flags
   (`unreadMessages`, `unseenGrades`), for the current Immich user (spec §4.7, §6).
   `refresh()` = `GET /messages?since=<watermark>&limit=100` (any id not in `readIds`, or
   `unreadIds` not empty) + `GET /status` and (if `lastEventId > gradesSeenCursor`)
   `GET /changes?after=cursor&type=grade&limit=1`. Errors keep last value.
   Not configured → false, no calls.
2. `markGradesSeen(upTo: Long)`: advances the cursor (never backwards), refreshes.
3. `SchoolJournalBadgeViewModel` + `SchoolJournalTopBarAction`: `BadgedBox` with a small red `Badge` dot;
   refresh on `ON_START` and every 5 min (`pollEvery`) while started.
4. Tab dots in `SchoolJournalScreen`; leaving the journal screen triggers `refresh()`.

## Tests

- Badge rules: unread only, grade changes only, both, none, cursor not moving backwards,
  first connect sets cursor = `lastEventId`, offline keeps value, two Immich users on one phone
  (one reads → the other still has the dot).
- Paparazzi: icon with / without dot, light & dark.

## Done when

- New message on the server → dot on Wall and School within 5 min or on return to the tab;
  it goes away only for the user who read it.
