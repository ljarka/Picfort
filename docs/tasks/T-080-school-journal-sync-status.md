# T-080 — School journal: Sync now, status sheet

**Status:** ready  
**Component:** Android (`:feature:school-journal`)  
**Depends on:** T-076, T-078, T-079. Spec §4.1, §4.5, §4.5.

## Steps

No Changes tab (owner decision 2026-11-17): `/changes` only feeds the badge, New markers and
notifications.

1. Sync now (⟳): `POST /sync`, handle 202 / 409 / 429, poll `/status` every 5 s (max 2 min) until
   `lastSync.status != running`, reload current tab.
2. Stale color: `tertiary` looks like the error red in the brand palette (seen in T-076); use a
   distinct warning tone so "older than 1 h" and "last check failed" differ.
3. Status sheet complete (spec §4.1); status line in the warning color when older than 1 h,
   "last check failed" when the last run failed.

## Tests

- Sync state machine (started, in progress, too soon with Retry-After, auth failed, timeout).
- Paparazzi: syncing, status sheet, status line fresh / stale / failed, each banner.

## Result (2026-11-17)

- [x] ⟳ Sync now: "Checking the e-journal…" and a spinner in place of ⟳, waits for a run newer than
  the one before (`startedAt`), reloads the open tab and the dot; snackbars for done (n changes / nothing
  new / failed), too soon (Retry-After), still running after 2 min; 409 AUTH_FAILED → the banner.
- [x] Stale line in amber (`warningColor`), "last check failed" stays red; status sheet with the address.
- [x] Unit tests for the sync states (started → running → done, already running, failed run, too soon,
  auth failed, timeout, wrong key); Paparazzi: syncing, stale (dark), storage banner, sheet with a
  failed run. Endpoint checklist in `feature/school-journal/AGENTS.md`.
- [x] Emulator + fake server: ⟳ → "nothing new" and "just now", ⟳ again → "Try again in 5 min",
  status sheet with address and version. The fake server now keeps a run's `startedAt` until the next
  "Sync now", like the real one.

## Done when

- Every endpoint marked "used" in spec §4.8 is called by the app (checklist in
  `feature/school-journal/AGENTS.md`).
