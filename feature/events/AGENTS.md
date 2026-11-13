# feature:events — AGENTS.md

Family events vertical slice: upcoming and past events, details, form, recurrences, and birthday badges.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/events/EventsScreens.kt` | UI: `EventsListContent`, `EventDetailContent` |
| `ui/events/EventFormContent.kt` | UI: `EventFormContent` + form-only pickers (`PickerField`, `RepeatField`) |
| `ui/events/EventsViewModels.kt` | ViewModels: `EventsListViewModel`, `EventDetailViewModel`, `EventFormViewModel` |
| `ui/events/EventsRepository.kt` | Repository: polling, pagination of past events, midnight rollover |
| `ui/events/EventsApi.kt` | Server HTTP client: `/events` endpoints |
| `ui/events/EventsModels.kt` | Grouping, relative labels, `turnsLabel`, `repeatSummary` |
| `ui/events/BirthdayPhotos.kt` | Birthday photos (T-055): `birthdayDays`, `birthdayYears`, `EventDetailList` (lazy detail + rows) |
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

## Birthday photos (T-055)

- A birthday with a known year (`turns`): days = `seriesStartDate` (birth) + N years up to today; photos
  from `DayPhotosProvider`, one section per year ("Turned 8 · 16 Nov 2025"), empty years left out.
- The detail is one `LazyColumn` (event as the first item); tap → `onOpenPhoto(id, index, "event:<id>")`.
- A year's title (whole row, ">") → `onOpenDay(day)`: Photos on that day (T-056, routed by `:app`).

## Public Contract / Navigation Entry Points

- `EventsListScreen(onOpenEvent: (Long) -> Unit, onAddEvent: () -> Unit, onSettingsClick: () -> Unit, onSetUpServer: () -> Unit)`
- `EventDetailScreen(onBack, onEdit: (Long) -> Unit, onEditBirthday: (memberId: String) -> Unit, onOpenPhoto: (assetId, index, source) -> Unit, onOpenDay: (LocalDate) -> Unit)`
- `EventFormScreen(onBack: () -> Unit, onSaved: (Long) -> Unit, onGone: () -> Unit)`

## Dependencies & Boundaries

- Depends on `:core:model` (`FamilyEvent`, `EventsSegment`, `FamilyMemberProvider`, `FamilyEventsProvider`, `DayPhotosProvider`).
- Depends on `:core:ui` (`PicfortTheme`, `SettingsAction`, `ServerRequiredContent`, `justify`); Coil for thumbnails.
- Depends on `:core:network` (`PicfortServerClient`, `PicfortServerException`).
- **NEVER** depends directly on other feature modules (`:feature:wall`, `:feature:shopping`, `:feature:family`).
- Implements `FamilyEventsProvider` interface for cross-feature consumers (e.g. Wall's today panel).
- Consumes family members via `FamilyMemberProvider` interface.

## Verification

```bash
./gradlew :feature:events:testDebugUnitTest
./gradlew :feature:events:verifyPaparazziDebug
./gradlew :feature:events:recordPaparazziDebug # on intentional UI changes
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/events-member-filter.yaml   # T-054, test server
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/events-birthday-photos.yaml  # T-055, T-056
```
