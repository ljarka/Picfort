# T-076 — School journal: module, API client, setup screen, entry icon

**Status:** ready  
**Component:** Android (`:feature:school-journal` new, `:feature:wall`, `:feature:school`, `:app`)  
**Depends on:** ADR 0017, [spec](../specs/school-journal.md) §2, §3, §5, §7.

## Summary

Create the removable module with a complete API client and the configuration flow. After this
task the icon opens either the setup screen or an (empty) content shell.

## Steps

1. `./scripts/new-feature.sh school-journal` (skill `picfort-new-feature-module`). The script
   puts the module name into the package path and namespace; a hyphen is not valid there, so
   fix both to `schooljournal`: namespace `com.github.ljarka.picfort.feature.schooljournal`,
   code in package `com.github.ljarka.picfort.ui.schooljournal`. Drop the `:core:database`
   dependency the script adds (ADR 0017: no Room).
2. `SchoolJournalDtos.kt`: every schema of school-server `openapi.yaml` (Status, SyncRun, ChildAccount,
   Grade, SubjectSummary, MessageSummary, Message, Attachment, ChangeEvent, ChangePage, Error).
   Enums as strings + mapping with `Unknown`; `ignoreUnknownKeys`. `ChangeEvent.entity` decoded by
   `entityType` (grade → `Grade`, message → `MessageSummary`); `diff` as `Map<String, JsonArray>?`.
3. `SchoolJournalApi.kt`: one suspend function per endpoint: `health`, `status`, `children`, `grades`
   (child, subject, semester, kind, since, includeDeleted), `subjects` (child, semester),
   `messages` (child, since, limit, cursor, includeDeleted), `message`, `changes` (child,
   after, type, limit), `sync`. No `GET /grades` (spec §4.8). No client for the server's read state (`…/read`,
   `read-all`, `unread=true`): read is per Immich user on the phone (ADR 0017).
   Own `OkHttpClient` (`CleartextPolicyInterceptor`, `X-School-Key` interceptor, 15 s timeouts).
4. `SchoolJournalError.kt`: sealed class per spec §5, incl. `Retry-After` and `WWW-Authenticate`.
5. `SchoolJournalConfigStore.kt`: `schoolJournal.url`, `schoolJournal.key`, `schoolJournal.child`
   in `SecurePreferencesStore`; `configured` StateFlow; `clear()` (also deletes
   `filesDir/school-journal/`). `ReadStateStore` (spec §4.6): per Immich user
   (`AccessTokenStore.userId`) + server, JSON file, started on connect (`watermark = now`,
   `gradesSeenCursor = lastEventId`).
6. `SchoolJournalSetup.kt` + `SchoolJournalViewModel`: Connect flow (spec §3), errors, Server settings +
   Disconnect.
7. `SchoolJournalScreen(onBack, onOpenMessage)`: setup when not configured; else shell with the
   always-visible "Last successful sync" line (spec §4.1: date/time, relative, failed last check,
   no sync yet), banners, status sheet, child chips, two empty tabs (filled by T-078, T-079).
8. `SchoolJournalTopBarAction(onClick)`: building icon (`ic_school_building.xml`), no badge yet.
9. `FamilyWallScreen`, `SchoolScreen`: add `topBarActions: @Composable RowScope.() -> Unit = {}`
   before `SettingsAction`. `:app`: `Screen.SchoolJournal`, `Screen.SchoolJournalMessage(id)` (placeholder
   until T-078), NavHost entries, pass the action on both tabs.

10. `maestro/scripts/fake-school-server.py` (moved here from T-081, owner decision 2026-11-17):
    stdlib HTTP server on `:5101`, key `test-key`, neutral sample data (2 children, grades in 2
    semesters incl. proposed/final and a removed one, messages with HTML + attachments, change events
    incl. `updated` with `diff`), `/sync` returning 202 then 429, and `POST /_test/grade` /
    `POST /_test/message` to add data during a flow. Every device check uses it, never the family's
    real school-server.

## Tests

- DTO parsing from fixtures (one JSON per schema, incl. unknown field + unknown enum).
- Error mapping for every row of spec §5; Connect validation (health down, 401, 429, cleartext).
- Paparazzi: setup empty / error / connecting; shell with banners; icon.

## Result (2026-11-17)

- [x] Module, DTOs for every schema, client for every used endpoint, error mapping, stores, setup,
  shell (status line, banners, status sheet, child chips, two empty tabs), icon on Wall and School.
- [x] Fake server; device check on the emulator against it: wrong key → "Wrong API key", bad address
  → hint, connect → status line + Ola/Kuba, child kept after restart, status sheet, icon on School,
  `auth_failed` banner with "3 h ago · last check failed", offline banner, 401 → "The API key is no
  longer valid", Disconnect removes `files/school-journal`.
- Changed from the plan: no `Screen.SchoolJournalMessage` placeholder (added with the message
  screen in T-078); `SchoolJournalScreen(onBack)` only for now. The offline banner says "Pull down
  to try again" until T-081 brings the offline copy (then: "Showing data from 14:32").
- Follow-up for T-080: the stale color (`tertiary`) is close to the error red in the brand palette;
  pick a distinct warning tone when finishing the status line.

## Done when

- Icon on Wall and School opens setup; connecting to the fake server (`:5101`) on the emulator
  shows the shell with status and children. DoD in `AGENTS.md`; `feature/school-journal/AGENTS.md` written.
