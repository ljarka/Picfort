# feature:wall — AGENTS.md

Family wall vertical slice: posts stream, today panel (events/trips/birthdays), photo posting, reactions, filtering.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/wall/FamilyWallScreen.kt` | UI: `FamilyWallScreen`, `FamilyWallContent`, post cards, photo upload dialog, filter chips |
| `ui/wall/TodayPanel.kt` | Today overview panel at the top of the wall (events, ongoing trips, next big event) |
| `ui/wall/FamilyWallViewModel.kt` | ViewModel: wall stream, polling, new post creation, reaction toggles |
| `ui/wall/WallRepository.kt` | Repository: server fetch & polling, optimistic updates |
| `ui/wall/WallApi.kt` | Server HTTP client: `/wall/posts`, `/wall/posts/{id}/reactions` |
| `ui/wall/WallScreenshotTest.kt` | Paparazzi snapshots: wall feed, dark mode, filtered, today panels |

## Public Contract / Navigation Entry Points

- `FamilyWallScreen(onSettingsClick, onEditMember, onOpenFamily, onOpenEvent, onOpenEvents, onSetUpServer)`;
  `onOpenFamily` is the "+ Family" circle at the end of the avatar row (opens Family settings).

## Dependencies & Boundaries

- Depends on `:core:model` (`FamilyMember`, `FamilyMemberProvider`, `FamilyEventsProvider`).
- Depends on `:core:ui` (`PicfortTheme`, `MemberAvatar`, `MemberCarousel`, `SettingsAction`, `ServerRequiredContent`).
- Depends on `:core:network` (`PicfortServerClient`, `PicfortServerException`).
- **NEVER** depends directly on other feature modules (`:feature:events`, `:feature:shopping`, `:feature:family`).
- Consumes family members via `FamilyMemberProvider.allMembers` (bound in `:app` from `:feature:family`): the avatar row shows manual members too; they just have no posts.
- Consumes upcoming events for `TodayPanel` via `FamilyEventsProvider` interface (bound in `:app` from `:feature:events`).
- `todayPanel`: timed events first, then ongoing trips, then one-day events (birthdays before others).

## Verification

```bash
./gradlew :feature:wall:testDebugUnitTest
./gradlew :feature:wall:verifyPaparazziDebug
./gradlew :feature:wall:recordPaparazziDebug # on intentional UI changes
```
