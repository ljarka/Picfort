# feature:events — AGENTS.md

Family events vertical slice: upcoming and past events, details, form, recurrences, and birthday badges.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/events/EventsScreens.kt` | UI: `EventsListContent`, `EventDetailContent`, `EventFormContent`, date pickers |
| `ui/events/EventsViewModels.kt` | ViewModels: `EventsListViewModel`, `EventDetailViewModel`, `EventFormViewModel` |
| `ui/events/EventsRepository.kt` | Repository: polling, pagination of past events, midnight rollover |
| `ui/events/EventsApi.kt` | Server HTTP client: `/events` endpoints |
| `ui/events/EventsModels.kt` | UI state: `EventsListUi`, `EventDetailUi`, `EventFormUi`, relative labels |
| `ui/events/EventsScreenshotTest.kt` | Paparazzi snapshots: upcoming, past, dark theme, details, form |

## Public Contract / Navigation Entry Points

- `EventsListScreen(onOpenEvent: (Long) -> Unit, onAddEvent: () -> Unit, onSettingsClick: () -> Unit, onSetUpServer: () -> Unit)`
- `EventDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit)`
- `EventFormScreen(onBack: () -> Unit, onSaved: (Long) -> Unit, onGone: () -> Unit)`

## Dependencies & Boundaries

- Depends on `:core:model` (`FamilyEvent`, `EventsSegment`, `FamilyMemberProvider`, `FamilyEventsProvider`).
- Depends on `:core:ui` (`PicfortTheme`, `SettingsAction`, `ServerRequiredContent`).
- Depends on `:core:network` (`PicfortServerClient`, `PicfortServerException`).
- **NEVER** depends directly on other feature modules (`:feature:wall`, `:feature:shopping`, `:feature:family`).
- Implements `FamilyEventsProvider` interface for cross-feature consumers (e.g. Wall's today panel).
- Consumes family members via `FamilyMemberProvider` interface.

## Verification

```bash
./gradlew :feature:events:testDebugUnitTest
./gradlew :feature:events:verifyPaparazziDebug
./gradlew :feature:events:recordPaparazziDebug # on intentional UI changes
```
