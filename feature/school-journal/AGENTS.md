# feature:school-journal — AGENTS.md

School journal (T-076…T-082): grades and messages from the family's
[school-server](https://github.com/ljarka/school-server) (`/api/v1`, own address and `X-School-Key`).
Spec: [docs/specs/school-journal.md](../../docs/specs/school-journal.md). **Removable by design**:
[ADR 0017](../../docs/decisions/0017-school-journal-removable-module.md) (read "How to remove" first).

## Scope & Files (package `ui.schooljournal`, namespace `feature.schooljournal`)

| File | Purpose |
|---|---|
| `SchoolJournalDtos.kt` | Wire contract (`journalJson`: lenient), `GradeKind` with `UNKNOWN`, `ChangeEvent.grade()/message()`, `instantOrNull` |
| `SchoolJournalApi.kt` | Every used endpoint (spec §4.8), own `OkHttpClient` + `CleartextPolicyInterceptor`; `normalizeJournalAddress` |
| `SchoolJournalError.kt` | Error codes → sealed errors + `userMessage()` (spec §5); server `message` only in logs |
| `SchoolJournalStores.kt` | `SchoolJournalConfigStore` (encrypted `schoolJournal.*`), `ReadState` + `ReadStateStore` (per Immich user, `filesDir/school-journal/`) |
| `SchoolJournalNews.kt` | `checkNews` (dot rules, spec §6) and the singleton `SchoolJournalNews` (icon + tab dots, `markGradesSeen`) |
| `SyncLine.kt` | "Last successful sync: today 14:32 (12 min ago) · next 14:47", stale > 1 h, failed flag |
| `SchoolJournalViewModel.kt` | `SchoolJournalUi`: setup vs content, connect (`/health` → `/status` → save), banners, children |
| `SchoolJournalScreen.kt` | `SchoolJournalScreen`/`SchoolJournalContent`: status line, banner, child chips, tabs, status sheet |
| `SchoolJournalSetup.kt` | Address + key form; as Server settings: status and Disconnect |
| `SchoolJournalTopBarAction.kt` | Building icon + red dot (`SchoolJournalIcon`, stateless) for Wall and School; polls every 5 min while started |

## Rules

- Never the app's OkHttp (it adds Immich/Picfort credentials). Never Room, never a `:core` change.
- Read / seen state per Immich user on the phone (`AccessTokenStore.userId`); the server's shared read
  state (`…/read`, `read-all`, `unread=true`) and `GET /grades` are deliberately unused (spec §4.6, §4.8).
- New state starts at "now" (`ReadState.startingAt`): history is never unread or new.
- 401 → setup with "The API key is no longer valid"; unreachable → offline banner, data kept.
- Neutral names only ("e-journal"), never a provider's name; no real children's data in tests or snapshots.

## Public Contract

- `SchoolJournalTopBarAction(onClick)`, `SchoolJournalScreen(onBack)` — used only by `:app`.

## Dependencies & Boundaries

- `:core:model`, `:core:ui` (theme), `:core:network` (`SecurePreferencesStore`, `AccessTokenStore`,
  `CleartextPolicyInterceptor`, `defaultSchemeFor`). Nothing may depend on this module except `:app`.

## Verification

```bash
./gradlew :feature:school-journal:testDebugUnitTest :feature:school-journal:verifyPaparazziDebug
./gradlew :feature:school-journal:recordPaparazziDebug   # on intentional UI changes
python3 -I maestro/scripts/fake-school-server.py         # :5101, key test-key; emulator: 10.0.2.2:5101
```
