# T-049 — Modularization: :feature:wall

**Status:** ready  
**Component:** Android feature module  
**Depends on:** T-043 (`:core:model`), T-044 (`:core:ui`), T-045 (`:core:network`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract the Family Wall home hub (Today summary panel, member status chips, active member selector)
into an independent `:feature:wall` module.

## Scope

1. **Source migration**:
   - `ui/wall/WallScreen.kt`, `WallViewModel.kt`, `WallRepository.kt`
   - `TodaySummaryPanel.kt`, quick actions, member selection chips
2. **Paparazzi tests**:
   - Move wall snapshot tests into `:feature:wall/src/test/.../WallScreenshotTest.kt`
   - Fast isolated test cycle: `./gradlew :feature:wall:recordPaparazziDebug` / `verifyPaparazziDebug`
3. **Module `AGENTS.md`**:
   - Create `feature/wall/AGENTS.md` specifying:
     - Scope (Family Wall home dashboard, today's glance)
     - Clean data flow from repositories without tight coupling to sibling feature UI
     - Fast test commands

## Acceptance criteria

- [ ] `:feature:wall` compiles cleanly using `picfort.android.feature`.
- [ ] `./gradlew :feature:wall:testDebugUnitTest :feature:wall:verifyPaparazziDebug` green.
- [ ] `feature/wall/AGENTS.md` created.
