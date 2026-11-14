# T-065 — Family Wall slow right after an update

**Status:** done (adf4271)  
**Component:** Android app (`:feature:wall`, `:feature:family`)  
**Depends on:** T-032 (Immich users from the sync stream)

## Summary

After updating to v0.9.0 the Wall took long to show anything; switching to another tab and back
helped. The update changes the Room schema, so the database starts empty (no migrations). With
no Immich users stored, `FamilyRepository.refresh()` first syncs them through the whole timeline
(`ImmichUsersSync` → `TimelineRepository.refresh()`), and the Wall's refresh awaited it inside
`coroutineScope { launch { family.refresh() }; posts() }`: no posts until the full library sync
was done. On a big library over the public address that is minutes.

## Design

- The Wall launches `family.refresh()` without waiting; names and avatars arrive through
  `family.allMembers` when the sync is done.
- `FamilyRepository`: one Immich-users sync at a time (`AtomicBoolean`); every tab refreshes the
  family every minute, so a slow sync no longer queues more.

## Acceptance criteria

- [x] Device check (2026-11-14, emulator, test account, database deleted before each start, app
  warm): first Wall post visible 4.0–4.9 s after launch before, 2.0–2.1 s after (150-photo
  library; the difference grows with the library).
- [x] Unit tests, Paparazzi, ktlint, lint green.
