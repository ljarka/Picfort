# T-046 — Modularization: :feature:shopping

**Status:** ready  
**Component:** Android feature module  
**Depends on:** T-043 (`:core:model`), T-044 (`:core:ui`), T-045 (`:core:network`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract all shopping list functionality, offline cache, checklist logic, and the upcoming wishlist carousel
into an independent `:feature:shopping` module.

## Scope

1. **Move files into `:feature:shopping`**:
   - `ui/shopping/ShoppingScreens.kt`
   - `ui/shopping/ShoppingViewModels.kt`
   - `ui/shopping/ShoppingRepository.kt`
   - `ui/shopping/ShoppingApi.kt`
   - Shopping snapshot tests moved from `ScreenshotTest.kt` into `ShoppingScreenshotTest.kt`.
2. **Navigation Entry Point**:
   - Exposed composable `ShoppingTab(onOpenServerSetup: () -> Unit)`.
3. **Module `AGENTS.md`**:
   - `feature/shopping/AGENTS.md` specifying internal files, API endpoints, test commands, and wishlist roadmap.

## Acceptance criteria

- [ ] `./gradlew :feature:shopping:testDebugUnitTest` passes isolated from `:app`.
- [ ] `./gradlew :feature:shopping:verifyPaparazziDebug` verifies shopping snapshots in seconds.
- [ ] `:app` consumes `ShoppingTab` without directly touching internal shopping classes.
- [ ] `feature/shopping/AGENTS.md` created (< 80 lines).
