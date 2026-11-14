# feature:school — AGENTS.md

School timetables (T-029): one child at a time, "finishes at" today, the week day by day, editing.
Server API: T-028 (`picfort-server/school.py`, `/school`, `school.db`).

## Scope & Files

| File | Purpose |
|---|---|
| `ui/school/SchoolModels.kt` | Domain (`Child`, `Period`, `SchoolEntry`) and pure logic: `schoolDay` + `summaryLines` (the card), `visibleDays`, `startDay`, `fillBells`, `firstFreePeriod`, `subjects`, `teacherOf`, `spokenEntry` (TalkBack) |
| `ui/school/SchoolApi.kt` | DTOs and `SchoolBackend` over `PicfortServerClient`; `EntryDraft.wire()` sends every field (`null` clears) |
| `ui/school/SchoolRepository.kt` | `children` StateFlow, offline copy (`school.json`, tied to server + user), not optimistic |
| `ui/school/SchoolViewModel.kt` | `SchoolUi`; selected child in SharedPreferences `school`; minute clock (`tick`), 60 s poll |
| `ui/school/SchoolScreen.kt` | `SchoolScreen`/`SchoolContent`: child chips, summary card, day chips + `HorizontalPager`, rows, sheets |
| `ui/school/SchoolForms.kt` | `AddChildForm` (pick from the family), `ChildForm` (bells, remove), `EntryForm` ("Save and add next"), `BellsForm` (Fill in, Copy from), `CopyDayDialog`, `TimeField` |

## Rules

- Not optimistic: a change answered with the child replaces it; every change then reloads `GET /school`
  (which also saves the offline copy).
  A 404 on a child/entry (someone else deleted it) reloads quietly; 404 on `/school` = outdated server.
- Offline (unreachable): the last copy, read only, banner "Offline. Timetable from 14:32"; edit mode off.
- Edit mode (✎) gates every change except adding a child; tap the selected chip (or long-press) to edit it.
- Activities differ from lessons by container color **and** a star icon. Today's current row is
  highlighted, finished ones dimmed. Weekend chips only with entries (always while editing).
- Times: `H:mm` or `h:mm a` from the device setting (`timeFormatter`), ranges via `timeRange`; day names from the locale.
- Children are family members (T-066): `Child.memberId` (server `immich_user_id`); name and face from
  `FamilyMemberProvider`; row = `MemberCarousel` with "+" (`addLabel`); `SchoolUi.candidates` for the picker.
- Out of scope here: children from Immich people, face photo, "Photos of Ola" (T-030).

## Public Contract

- `SchoolScreen(onSettingsClick: () -> Unit, onSetUpServer: () -> Unit = onSettingsClick)`

## Dependencies & Boundaries

- `:core:model` (`pollEvery`, `FamilyMember`, `FamilyMemberProvider`), `:core:ui` (theme, `MemberCarousel`, `MemberAvatar`, `avatarColor`, `initials`, `ServerRequiredContent`,
  `SettingsAction`, `aboveFloatingTabBar`), `:core:network` (`PicfortServerClient`, `PicfortServerException`,
  `FrameSettingsStore`, `AccessTokenStore`). **NEVER** another feature module.

## Verification

```bash
./gradlew :feature:school:testDebugUnitTest :feature:school:verifyPaparazziDebug
./gradlew :feature:school:recordPaparazziDebug # on intentional UI changes
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/school.yaml          # test server only
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/school-family.yaml   # 2 children, restart, swipe, 60 s poll
docker stop picfort_test; SKIP_SERVER_CHECK=1 DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/school-offline.yaml; docker start picfort_test
```
