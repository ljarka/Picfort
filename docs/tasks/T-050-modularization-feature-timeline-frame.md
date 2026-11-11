# T-050 — Modularization: :feature:timeline and :feature:frame

**Status:** ready  
**Component:** Android feature modules  
**Depends on:** T-043 (`:core:model`), T-044 (`:core:ui`), T-045 (`:core:network`, `:core:database`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract photo timeline viewing and ambient e-paper frame mode into dedicated feature modules:
`:feature:timeline` and `:feature:frame`.

## Scope

1. **`:feature:timeline`**:
   - `TimelineScreen.kt`, `TimelineViewModel.kt`
   - `JustifiedTimelineLayout.kt`
   - Photo detail viewer, zoom, metadata sheet
   - `feature/timeline/AGENTS.md`
2. **`:feature:frame`**:
   - `FrameActivity.kt` / `FrameScreen.kt`, `FrameViewModel.kt`
   - Ambient slideshow, pairing QR/flow, frame status indicators
   - `feature/frame/AGENTS.md`
3. **Paparazzi tests**:
   - Migrate timeline and frame screenshot tests to their respective modules.

## Acceptance criteria

- [ ] `:feature:timeline` and `:feature:frame` compile independently.
- [ ] Timeline rendering and frame mode functional without regression.
- [ ] Module `AGENTS.md` created for both.
