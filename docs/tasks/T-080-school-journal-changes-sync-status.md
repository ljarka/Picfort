# T-080 — School journal: changes feed, Sync now, status sheet

**Status:** ready  
**Component:** Android (`:feature:school-journal`)  
**Depends on:** T-076, T-078, T-079. Spec §4.1, §4.5, §4.6.

## Steps

1. Changes tab: `since = now − 30 d` then `after = nextCursor` while `hasMore`, newest first;
   type chips (All / Grades / Messages); Load older (90 d, then `after=0`); row texts from
   `changeType` + `diff`; tap opens grade sheet / message screen. Showing the tab marks grades seen.
2. Sync now (⟳): `POST /sync`, handle 202 / 409 / 429, poll `/status` every 5 s (max 2 min) until
   `lastSync.status != running`, reload current tab.
3. Status sheet complete (spec §4.1), stale-data banner (> 1 h).

## Tests

- Changes paging (`since` never combined with `after`), diff formatting incl. unknown fields,
  sync state machine (started, in progress, too soon with Retry-After, auth failed, timeout).
- Paparazzi: changes list, syncing, status sheet, each banner.

## Done when

- Every server endpoint is used by the app except the read-state ones (`PUT|DELETE …/read`,
  `read-all`; ADR 0017) — checklist in `feature/school-journal/AGENTS.md`.
