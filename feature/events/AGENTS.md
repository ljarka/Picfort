# feature:events — AGENTS.md

Family events vertical slice: upcoming and past events, details, form, recurrences, and birthday badges.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/events/EventsScreens.kt` | UI: `EventsListContent`, `EventDetailContent`, `EventFormContent`, date pickers |
| `ui/events/EventsViewModels.kt` | ViewModels: `EventsListViewModel`, `EventDetailViewModel`, `EventFormViewModel` |
| `ui/events/EventsRepository.kt` | Repository: polling, pagination of past events, midnight rollover |
| `ui/events/EventsApi.kt` | Server HTTP client: `/events` endpoints |
| `ui/events/EventsModels.kt` | Grouping, relative labels, `turnsLabel`, `repeatSummary` |
| `ui/events/EventsScreenshotTest.kt` | Paparazzi snapshots: upcoming, past, dark theme, details, form, repeat & birthdays |

## Repeat & birthdays (T-037)

- `FamilyEvent.startDate/endDate` = the server's occurrence (`occurrence_start/end`); `seriesStartDate/End` = first occurrence, edited by the form. Lists, labels and the Wall's today panel need no repeat logic.
- `birthdayOf` (member id) + `turns`: title from the member's current name (`displayTitle`), no Edit/Delete, "Edit in Family settings" (`onEditBirthday`).
- Midnight: a series whose occurrence ended is dropped from Upcoming (not moved to Past); the refresh brings its next occurrence.
- `EventDto` must not declare a companion object (it would replace the kotlinx serializer's).

## Who it is for (T-053)

- `FamilyEvent.memberIds` (server `members`); `who` adds a birthday's member. `EventMembers.kt`:
  `membersOf` (drops deleted members), `MemberStack` (row, max 3 + "+N"), `WhoLine` (detail),
  `WhoField` (form chips from `FamilyMemberProvider.allMembers`). PATCH sends `members` only when changed.
- Filter (T-054): `MemberCarousel` above Upcoming/Past; `EventsListUi.selectedMemberId` lives in the
  ViewModel (not saved); `forMember` keeps events whose `who` has the member, filtered in the app.

## Public Contract / Navigation Entry Points

- `EventsListScreen(onOpenEvent: (Long) -> Unit, onAddEvent: () -> Unit, onSettingsClick: () -> Unit, onSetUpServer: () -> Unit)`
- `EventDetailScreen(onBack: () -> Unit, onEdit: (Long) -> Unit, onEditBirthday: (memberId: String) -> Unit)`
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
