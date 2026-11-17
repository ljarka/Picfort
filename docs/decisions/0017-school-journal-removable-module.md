# ADR 0017 — School journal as an optional, removable module

**Status:** proposed  
**Date:** 2026-11-17  
**Spec:** [docs/specs/school-journal.md](../specs/school-journal.md)

## Context

The family runs [school-server](https://github.com/ljarka/school-server), a separate service
that reads the children's school e-journal and exposes grades, messages and a change feed at
`/api/v1` with its own API key (`X-School-Key`). Picfort should show it, entered from an icon on
Family Wall and School. It is an unofficial integration with a third-party e-journal: it may have
to go away (provider terms, server retired), so removing it must be cheap and safe.
school-server's own ADR says the same from its side: Picfort never depends on it.

## Decision

1. Everything lives in **`:feature:school-journal`**: API client, DTOs, config, cache, badge, screens.
2. **No `:core:*` change.** No contract in `:core:model`, no Room table, no class in
   `:core:network`. The module only *uses* existing core pieces.
3. **Wall and School stay unaware.** They get a generic `topBarActions` slot; `:app` fills it
   with `SchoolJournalTopBarAction`. No feature depends on `:feature:school-journal` (ADR 0011 rule).
4. **Separate server, separate credentials.** Own address + key (encrypted
   `SecurePreferencesStore`, prefix `schoolJournal.`), own `OkHttpClient`; never the Picfort server
   key or the Immich token. Configured inside the journal screen, not in app Settings.
5. **Data on the server, "new" per person on the phone.** The server holds grades and messages.
   "Read" and "seen" are **per Immich user**, kept by the module on the phone
   (`filesDir/school-journal/state-<server>-<immichUserId>.json`; id from `AccessTokenStore.userId`),
   so one parent reading a message does not clear it for the other (owner decision 2026-11-17).
   The server's shared read state (`PUT|DELETE …/read`, `read-all`, `unread=true`,
   `counts.unreadMessages`) is deliberately not used. Rejected: per-reader state on
   school-server (needs a server change) and on the Picfort server (breaks removability).
6. Device tests use a fake school-server (`maestro/scripts/fake-school-server.py`), never the
   real one (real children's data must not reach the public repo).

## How to remove

1. `rm -r feature/school-journal`
2. `settings.gradle.kts`: drop `include(":feature:school-journal")`; `app/build.gradle.kts`: drop the dependency.
3. `:app`: drop `Screen.SchoolJournal`, `Screen.SchoolJournalMessage`, their NavHost entries, the two
   `topBarActions = { SchoolJournalTopBarAction … }` arguments (Wall, School), the
   `schoolJournalNotifyScheduler.start()` line in `PicfortApplication` and the
   `EXTRA_OPEN_SCHOOL_JOURNAL` handling in `MainActivity`.
4. Docs: root `AGENTS.md` module map row, `docs/ARCHITECTURE.md`, mark this ADR superseded.
5. `./scripts/check-modular-boundaries.sh && ./gradlew testDebugUnitTest`.

Leftover `schoolJournal.*` encrypted prefs and `filesDir/school-journal/` on phones are harmless.
The periodic work `school-journal-notify` disappears with its worker class: WorkManager drops work
whose class no longer exists (it fails once and is not rescheduled); to be tidy, ship one release
that calls `WorkManager.cancelUniqueWork("school-journal-notify")` before deleting the module.

## Consequences

- + Removal is a folder and a handful of lines; a reviewer can see the whole integration in one place.
- + A broken school-server can't break other screens: only the icon's badge stays off.
- − Some small duplication (own HTTP client setup, own cache) instead of sharing `:core`.
- − Read state is lost on reinstall and not shared between two devices of the same person.
- − The badge only refreshes while the app is open; outside it, notifications (T-082, every
  15 min) tell about new grades and messages.
