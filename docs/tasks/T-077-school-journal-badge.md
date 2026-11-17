# T-077 — School journal: "something new" badge

**Status:** ready  
**Component:** Android (`:feature:school-journal`)  
**Depends on:** T-076. Spec §6.

## Steps

1. `SchoolJournalBadgeRepository` (@Singleton): `hasNew: StateFlow<Boolean>` and per-tab flags
   (`unreadMessages`, `unseenGrades`), for the current Immich user (spec §4.6, §6).
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

## Result (2026-11-17)

- [x] `checkNews` + `SchoolJournalNews` (singleton, mutex, errors keep the dot), icon dot on Wall and
  School (refresh on start and every 5 min), tab dots beside the titles, Grades tab marks grades seen.
- [x] Unit tests for every rule above incl. two users and the cursor; Paparazzi: icon with/without dot
  (light, dark), tab dots.
- [x] Emulator + fake server: no dot right after connect; a new message (hook) → dot on School and Wall
  and on Messages; a new grade → dot on Grades; opening Grades removes only that dot; dot stays with
  the server down; state file keeps `gradesSeenCursor` after a restart.
- Not checkable yet on a device: a message read by one user only — reading comes with T-078
  (covered by `eachImmichUserHasTheirOwnDot`).

## Done when

- New message on the server → dot on Wall and School within 5 min or on return to the tab;
  it goes away only for the user who read it.
