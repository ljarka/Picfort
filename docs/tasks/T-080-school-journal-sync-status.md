# T-080 — School journal: Sync now, status sheet

**Status:** ready  
**Component:** Android (`:feature:school-journal`)  
**Depends on:** T-076, T-078, T-079. Spec §4.1, §4.5, §4.5.

## Steps

No Changes tab (owner decision 2026-11-17): `/changes` only feeds the badge, New markers and
notifications.

1. Sync now (⟳): `POST /sync`, handle 202 / 409 / 429, poll `/status` every 5 s (max 2 min) until
   `lastSync.status != running`, reload current tab.
2. Status sheet complete (spec §4.1); status line in the warning color when older than 1 h,
   "last check failed" when the last run failed.

## Tests

- Sync state machine (started, in progress, too soon with Retry-After, auth failed, timeout).
- Paparazzi: syncing, status sheet, status line fresh / stale / failed, each banner.

## Done when

- Every endpoint marked "used" in spec §4.8 is called by the app (checklist in
  `feature/school-journal/AGENTS.md`).
