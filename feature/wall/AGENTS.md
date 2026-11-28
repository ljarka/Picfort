# feature:wall — AGENTS.md

Family wall vertical slice: posts stream, today panel (events/trips/birthdays), photo posting, reactions, filtering.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/wall/FamilyWallScreen.kt` | UI: `FamilyWallScreen`, `FamilyWallContent`, `FramePhotoCard` (`/frame/photo`, T-091), post cards (reaction line), message dialog & photo viewer (reaction bar), compose sheet |
| `ui/wall/WallReactions.kt` | `ReactionBar` (six emojis, counts, mine highlighted, who reacted), `ReactionSummary` (T-084) |
| `ui/wall/WallReactionNotifications.kt` | `WallReactionNotifyWorker` (15 min) + `WallReactionNotifyScheduler`, cursor per Immich user, "Tata reacted ❤️" (ADR 0018) |
| `ui/wall/TodayPanel.kt` | Today overview panel at the top of the wall (events, ongoing trips, next big event) |
| `ui/wall/FamilyWallViewModel.kt` | ViewModel: wall stream, polling, new post creation, pin, `toggleReaction` (instant, then the server's answer) |
| `ui/wall/WallRepository.kt` | Repository: server fetch & polling, optimistic updates |
| `ui/wall/WallAssistantImpl.kt` | `WallAssistantProvider` for Gemini AppFunctions (T-074) |
| `ui/wall/WallApi.kt` | Server HTTP client: `/wall`, `/wall/{id}` (delete, pin), `/wall/{id}/reactions` (PUT/DELETE), `/wall/first-reactions`; `WALL_REACTIONS` |
| `test/…/WallScreenshotTest.kt` | Paparazzi snapshots: wall feed (with reactions), reaction bar, dark mode, filtered, today panels |

## Public Contract / Navigation Entry Points

- `FamilyWallScreen(onSettingsClick, onEditMember, onOpenFamily, onOpenEvent, onOpenEvents, onSetUpServer, topBarActions)`;
  `topBarActions`: icons from `:app` before Settings (the school journal's, ADR 0017), unknown here;
  `onOpenFamily` is the "+" circle at the end of the avatar row (opens Family settings).
- `WallReactionNotifyScheduler.start()`: called once from `PicfortApplication.onCreate`.

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
./scripts/maestro-smoke.sh maestro/wall-reactions.yaml   # device, test server: react, count, take back
```
