# feature:family — AGENTS.md

Family management vertical slice: member profiles, Immich users syncing, custom photos, birth dates, and manual members.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/family/FamilySettingsScreen.kt` | UI: `FamilySettingsContent`, `AddPersonSheetContent`, `FamilyMemberSheetContent` |
| `ui/family/FamilySettingsViewModel.kt` | ViewModel: `FamilySettingsViewModel` (member edit, save, delete) |
| `ui/family/FamilyRepository.kt` | Repository: merges Immich users (`UsersDao`) with Picfort server profiles (`/family`), implements `FamilyMemberProvider` |
| `ui/family/FamilyModule.kt` | Dagger/Hilt module: binds `FamilyBackend`, `FamilyOfflineCopy`, `PeopleSource`, `FamilyMemberProvider` |
| `ui/family/FamilyApi.kt` | Server HTTP client: `/family` endpoints |
| `ui/family/PeopleApi.kt` | Immich API client: reads recognized people and face thumbnails |
| `ui/family/PeoplePickerSheet.kt` | UI: `PeoplePickerSheetContent` for linking Immich people |
| `ui/family/FamilyScreenshotTest.kt` | Paparazzi snapshots: settings, dark mode, edit sheet, add member sheet, people picker |

## Public Contract / Navigation Entry Points

- `FamilySettingsScreen(onBack: () -> Unit)`
- `FamilyRepository` implements `FamilyMemberProvider` for cross-feature consumers.

## Dependencies & Boundaries

- Depends on `:core:model` (`FamilyMember`, `BirthDate`, `FamilyMemberProvider`, `ImmichUsersSync`).
- Depends on `:core:ui` (`PicfortTheme`, `MemberAvatar`).
- Depends on `:core:network` (`PicfortServerClient`, `PicfortServerException`, `ServerUrlStore`, `AccessTokenStore`, `FrameSettingsStore`).
- Depends on `:core:database` (`UsersDao`, `UserEntity`).
- **NEVER** depends directly on sibling feature modules (`:feature:wall`, `:feature:shopping`, `:feature:events`).

## Verification

```bash
./gradlew :feature:family:testDebugUnitTest
./gradlew :feature:family:verifyPaparazziDebug
./gradlew :feature:family:recordPaparazziDebug # on intentional UI changes
```
