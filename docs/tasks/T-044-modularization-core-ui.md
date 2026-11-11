# T-044 — Modularization: :core:ui design system

**Status:** ready  
**Component:** Android core library  
**Depends on:** T-043 (`build-logic`, `:core:model`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract the design system, colors, theme, and reusable common UI components into `:core:ui`.

## Scope

1. **Design System & Theme**:
   - `PicfortTheme`, `Color.kt` (warm terracotta/slate palette), `Theme.kt`, typography and shapes.
2. **Common UI Components**:
   - `MemberAvatar` (avatar rendering, initials, avatar colors).
   - Error states, loading spinners, empty states.
   - Common buttons, badges and pill tags.
3. **Module `AGENTS.md`**:
   - `core/ui/AGENTS.md` explaining design tokens, guidelines, and rules (e.g. never hardcoding colors).

## Acceptance criteria

- [ ] `:core:ui` compiles with Compose and Paparazzi support.
- [ ] `:app` uses theme and avatar from `:core:ui`.
- [ ] `./gradlew testDebugUnitTest verifyPaparazziDebug` green.
- [ ] `core/ui/AGENTS.md` created.
