# T-050 — Modularization: :feature:timeline and :feature:frame

**Status:** done  
**Component:** Android feature modules  
**Depends on:** T-043 (`:core:model`), T-044 (`:core:ui`), T-045 (`:core:network`, `:core:database`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract photo timeline viewing and ambient e-paper frame mode into dedicated feature modules:
`:feature:timeline` and `:feature:frame`.

## Scope

1. **`:feature:timeline`**:
   - `TimelineScreen.kt`, `TimelineViewModel.kt`
   - `JustifiedLayout.kt`, `TimelineRows.kt`
   - Photo detail viewer (`ImageDetailsScreen.kt`), zoom, metadata sheet, collections, sync, local media provider
   - `feature/timeline/AGENTS.md`
2. **`:feature:frame`**:
   - `FrameScreen.kt`, `FrameViewModel.kt`, `FrameRepository.kt`
   - Server setup dialog and setup screen
   - `feature/frame/AGENTS.md`
3. **Paparazzi tests**:
   - Migrated timeline and frame screenshot tests to their respective modules.

## Acceptance criteria

- [x] `:feature:timeline` and `:feature:frame` compile independently.
- [x] Timeline rendering and frame mode functional without regression.
- [x] Module `AGENTS.md` created for both.
