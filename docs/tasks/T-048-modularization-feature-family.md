# T-048 — Modularization: :feature:family

**Status:** ready  
**Component:** Android feature module  
**Depends on:** T-043 (`:core:model`), T-044 (`:core:ui`), T-045 (`:core:network`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract Family settings, member management, and profile coordination into an independent
`:feature:family` module with its own isolated tests.

## Scope

1. **Source migration**:
   - `ui/family/FamilySettingsScreen.kt`, `FamilySettingsViewModel.kt`
   - `FamilyRepository.kt`, `FamilyApi.kt`
   - Sheet and dialog components (`AddMemberSheet`, `DeleteMemberConfirmationDialog`, `BirthDatePicker`)
2. **Paparazzi tests**:
   - Move family snapshot tests into `:feature:family/src/test/.../FamilyScreenshotTest.kt`
   - Fast isolated test cycle: `./gradlew :feature:family:recordPaparazziDebug` / `verifyPaparazziDebug`
3. **Module `AGENTS.md`**:
   - Create `feature/family/AGENTS.md` specifying:
     - Scope (family members, birth dates, profiles, Immich linking)
     - Entry points / navigation route
     - Test commands
     - Rules: no direct dependencies on sibling features (`:feature:events`, `:feature:shopping`)

## Acceptance criteria

- [ ] `:feature:family` compiles as an independent Android library module using `picfort.android.feature`.
- [ ] Isolated Paparazzi test suite passes in `:feature:family`.
- [ ] `:app` wires navigation to `:feature:family`.
- [ ] `feature/family/AGENTS.md` created.
