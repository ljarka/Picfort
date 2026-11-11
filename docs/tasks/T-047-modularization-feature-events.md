# T-047 — Modularization: :feature:events

**Status:** done (2026-11-12)  
**Component:** Android feature module  
**Depends on:** T-043 (`:core:model`), T-044 (`:core:ui`), T-045 (`:core:network`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract family events (upcoming/past lists, event details, event form, repeat picker, birthdays)
into an independent `:feature:events` module.

## Scope

1. **Move files into `:feature:events`**:
   - `ui/events/EventsScreens.kt`
   - `ui/events/EventsViewModels.kt`
   - `ui/events/EventsRepository.kt`
   - `ui/events/EventsApi.kt`
   - `ui/events/EventsModels.kt`
   - Events snapshot tests moved from `ScreenshotTest.kt` into `EventsScreenshotTest.kt`.
2. **Navigation Entry Point**:
   - Exposed composables: `EventsListScreen(...)`, `EventDetailScreen(...)`, `EventFormScreen(...)`.
3. **Module `AGENTS.md`**:
   - `feature/events/AGENTS.md` specifying internal files, API endpoints, test commands, and birthday integration.

## Acceptance criteria

- [x] `./gradlew :feature:events:testDebugUnitTest` passes isolated from `:app`.
- [x] `./gradlew :feature:events:verifyPaparazziDebug` passes.
- [x] `feature/events/AGENTS.md` created (< 80 lines).
