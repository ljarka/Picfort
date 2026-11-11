# T-049 — Modularization: :feature:wall

**Status:** done  
**Component:** Android feature module  
**Depends on:** T-043 (`:core:model`), T-044 (`:core:ui`), T-045 (`:core:network`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract the Family Wall home hub (Today summary panel, member status chips, active member selector)
into an independent `:feature:wall` module.

## Scope

1. **Source migration**:
   - `ui/wall/FamilyWallScreen.kt`, `FamilyWallViewModel.kt`, `WallRepository.kt`, `WallApi.kt`
   - `TodayPanel.kt`, quick actions, member selection chips
2. **Paparazzi tests**:
   - Moved wall snapshot tests into `:feature:wall/src/test/.../WallScreenshotTest.kt`
   - Fast isolated test cycle: `./gradlew :feature:wall:recordPaparazziDebug` / `verifyPaparazziDebug`
3. **Module `AGENTS.md`**:
   - Created `feature/wall/AGENTS.md` specifying scope, public contracts, dependencies, and verification.

## Acceptance criteria

- [x] `:feature:wall` compiles cleanly using `picfort.android.feature`.
- [x] `./gradlew :feature:wall:testDebugUnitTest :feature:wall:verifyPaparazziDebug` green.
- [x] `feature/wall/AGENTS.md` created.
