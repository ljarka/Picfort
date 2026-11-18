# feature:school-journal — AGENTS.md

School journal (T-076…T-082): grades and messages from the family's
[school-server](https://github.com/ljarka/school-server) (`/api/v1`, own address and `X-School-Key`).
Spec: [docs/specs/school-journal.md](../../docs/specs/school-journal.md). **Removable by design**:
[ADR 0017](../../docs/decisions/0017-school-journal-removable-module.md) (read "How to remove" first).

## Scope & Files (package `ui.schooljournal`, namespace `feature.schooljournal`)

| File | Purpose |
|---|---|
| `SchoolJournalDtos.kt` | Wire contract (`journalJson`: lenient), `GradeKind` with `UNKNOWN`, `MessageKind` (`message` / `announcement`), `ChangeEvent.grade()/message()`, `instantOrNull` |
| `SchoolJournalApi.kt` | Every used endpoint (spec §4.8), own `OkHttpClient` + `CleartextPolicyInterceptor`; `normalizeJournalAddress` |
| `SchoolJournalError.kt` | Error codes → sealed errors + `userMessage()` (spec §5); server `message` only in logs |
| `SchoolJournalStores.kt` | `SchoolJournalConfigStore` (encrypted `schoolJournal.*`), `ReadState` + `ReadStateStore` (per Immich user, `filesDir/school-journal/`) |
| `SchoolJournalNews.kt` | `checkNews` (dot rules, spec §6) and the singleton `SchoolJournalNews` (icon + tab dots, `markGradesSeen`) |
| `SchoolJournalMessages.kt` | `loadUnread` (after the watermark minus `readIds` + `unreadIds` one by one, 404 → gone), `Message.summary()`, `senderLabel`, `boardDates` |
| `MessagesTab.kt` | Messages tab: All / Inbox / Announcements + "Unread only" chips, rows (megaphone for announcements, unread = bold + red time + dot on the right, struck = removed), long-press read/unread, infinite scroll |
| `SchoolJournalMessageScreen.kt` | One message or announcement: `fromHtml` body with links, notice-board card + dates, attachments' names, opening marks read, Mark as unread; 404 → back (`RemovedMessages`) |
| `SchoolJournalGrades.kt` | `GradeSection` (one per child with All), `loadNewGradeIds`, `defaultSemester`, `orderSubjects`, kinds, `gradeBand` |
| `GradesTab.kt` | Semester chips, subject cards (average, chips colored by band (no weight shown), New dot, labelled kinds), grade sheet |
| `SchoolJournalSync.kt` | `syncNow`: `POST /sync`, then `/status` every 5 s (2 min) until a newer run finished; `SyncOutcome.message()` |
| `SyncLine.kt` | "Last successful sync: today 14:32 (12 min ago) · next 14:47", stale > 1 h, failed flag |
| `SchoolJournalCache.kt` | Offline copy `filesDir/school-journal/cache.json` (status, children, messages page 1, grades per child + semester), tied to the address, cleared on Disconnect |
| `SchoolJournalViewModel.kt` | `SchoolJournalUi`: setup vs content, connect (`/health` → `/status` → save), banners, children |
| `SchoolJournalScreen.kt` | `SchoolJournalScreen`/`SchoolJournalContent`: child avatar row (`MemberCarousel`), status card, banner, segmented Messages/Grades switch, status sheet |
| `SchoolJournalSetup.kt` | Address + key form + Notifications toggle; as Server settings: status and Disconnect |
| `SchoolJournalTopBarAction.kt` | Building icon + red dot (`SchoolJournalIcon`, stateless) for Wall and School; polls every 5 min while started |
| `SchoolJournalNotifications.kt` | `SchoolJournalNotifyWorker` (15 min), `SchoolJournalNotifyScheduler`, `planNotifications`, `EXTRA_OPEN_SCHOOL_JOURNAL` |

## Rules

- Never the app's OkHttp (it adds Immich/Picfort credentials). Never Room, never a `:core` change.
- Read / seen state per Immich user on the phone (`AccessTokenStore.userId`); the server's shared read
  state (`…/read`, `read-all`, `unread=true`) and `GET /grades` are deliberately unused (spec §4.6, §4.8).
- New state starts at "now" (`ReadState.startingAt`): history is never unread or new.
- Grades: New ids are read before marking grades seen, and kept for the visit. All children → one
  `/grades/subjects` per child (the server would merge subjects and averages).
- 401 → setup with "The API key is no longer valid"; unreachable → offline banner, cached copy, read only.
- Removal: ADR 0017 "How to remove" (dry run green 2026-11-17); `check-modular-boundaries.sh` fails
  if anything in `core/` mentions this module.
- Neutral names only ("e-journal"), never a provider's name; no real children's data in tests or snapshots.

## Endpoints (spec §4.8)

| Endpoint | Called from |
|---|---|
| `GET /health` | Connect (`SchoolJournalViewModel.connect`) |
| `GET /status` | Connect, refresh, `SchoolJournalNews`, `syncNow` |
| `GET /children` | Child chips, message screen (child name) |
| `GET /grades/subjects` | `loadGrades` (one call per child with All) |
| `GET /messages`, `GET /messages/{id}` | Messages tab, `loadUnread`, news, message screen |
| `GET /changes` | `checkNews` (dot), `loadNewGradeIds` (New markers), `checkNotifications` (`SchoolJournalNotifyWorker`, T-082) |
| `POST /sync` | ⟳ `syncNow` |
| Not used on purpose | `GET /grades`, `PUT/DELETE …/read`, `POST …/read-all` (ADR 0017) |

## Public Contract

- `SchoolJournalTopBarAction(onClick)`, `SchoolJournalScreen(onBack, onOpenMessage, initialTab)`,
  `SchoolJournalMessageScreen(messageId, onBack)`, `SchoolJournalNotifyScheduler.start()`,
  `EXTRA_OPEN_SCHOOL_JOURNAL` — used only by `:app`.

## Dependencies & Boundaries

- `:core:model`, `:core:ui` (theme), `:core:network` (`SecurePreferencesStore`, `AccessTokenStore`,
  `CleartextPolicyInterceptor`, `defaultSchemeFor`). Nothing may depend on this module except `:app`.

## Verification

```bash
./gradlew :feature:school-journal:testDebugUnitTest :feature:school-journal:verifyPaparazziDebug
./gradlew :feature:school-journal:recordPaparazziDebug   # on intentional UI changes
python3 -I maestro/scripts/fake-school-server.py         # :5101, key test-key; emulator: 10.0.2.2:5101
./scripts/maestro-smoke.sh maestro/school-journal.yaml maestro/school-journal-offline.yaml  # needs the fake server
```
