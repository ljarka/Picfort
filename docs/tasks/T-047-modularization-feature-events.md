# T-047 — Modularization: :feature:events

**Status:** ready  
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
   - Exposed composables: `EventsTab(onOpenEvent: (id) -> Unit, onAddEvent: () -> Unit, ...)`.
3. **Module `AGENTS.md`**:
   - `feature/events/AGENTS.md` specifying internal files, API endpoints, test commands, and birthday integration.

## Acceptance criteria

- [ ] `./gradlew :feature:events:testDebugUnitTest` passes isolated from `:app`.
- [ ] `./gradlew :feature:events:verifyPaparazziDebug` passes.
- [ ] `feature/events/AGENTS.md` created (< 80 lines).
