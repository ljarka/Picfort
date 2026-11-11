# 0011 — Modular Architecture for Agentic Development

**Status:** accepted (owner request 2026-11-11)  
**Deciders:** owner, Antigravity agent  
**Date:** 2026-11-11  

## Context

Picfort has grown from an initial photo timeline and e-paper controller into a full Family Hub:
Family Wall, Shopping Lists (with offline caching, Keep-style checklists, receipts and wishlists),
Family Events (recurring events, birthdays, album linking), School Timetable (M4), Family Member
Profiles, and Background Backup.

Currently, all Android code resides in a single monolithic `:app` Gradle module:
- Every agent invocation on a specific feature requires navigating hundreds of classes across
  navigation, database entities, Hilt modules, and unrelated screens.
- `ScreenshotTest.kt` contains 67+ Paparazzi snapshot tests in a single file; running or recording
  visual tests recompiles and renders every screen in the application.
- Unintentional cross-feature dependencies can easily creep in (e.g. Shopping directly referencing
  Events UI classes).
- Large contexts increase the risk of hallucinations, token waste, and slowed execution loops for AI coding agents.

## Decision

Migrate the Android application to a multi-module Gradle architecture optimized for **AI Agent Pair Programming**
and modern Android standards (Now in Android pattern).

### Structure

```
                                  :app (Thin assembler: MainActivity, Navigation root, Hilt root)
                                    |
          +---------------+---------+---------+---------------+
          |               |                   |               |
     :feature:wall   :feature:shopping   :feature:events  :feature:timeline ...
          \               |                   /               /
           +--------------+---------+---------+--------------+
                                    |
                               :core:ui (Design tokens, Theme, MemberAvatar, UI utilities)
                                    |
                             :core:model (Data classes: FamilyMember, BirthDate, Asset, etc.)
                                    |
                             :core:network (PicfortServerClient, ImmichClient, OkHttp)
                                    |
                             :core:database (Room database, Entities, DAOs)
```

### Module Types & Responsibilities

1. **`:core:model`**: Pure Kotlin/Android data structures without Compose or UI dependencies
   (`FamilyMember`, `BirthDate`, `ImmichAsset`, etc.). Highly stable.
2. **`:core:network`**: OkHttp, Retrofit/Kotlinx Serialization, token storage, API clients (`PicfortServerClient`, `ImmichClient`).
3. **`:core:database`**: Room database, DAOs (`UsersDao`, `ImagesDao`), entities.
4. **`:core:ui`**: Theme (`PicfortTheme`, `Color`, `Theme`), design system components (`MemberAvatar`, `FloatingNavigationBar`, error/loading states).
5. **`:feature:<name>`** (`wall`, `shopping`, `events`, `family`, `timeline`, `frame`, `backup`, and upcoming `school`, `receipts`):
   - Fully isolated vertical slice: UI (Screens, ViewModels), repository, and local Paparazzi screenshot tests.
   - **Features NEVER depend directly on each other.**
   - Navigation between features is handled via navigation routes/interfaces or top-level `:app` orchestration.
6. **`:app`**:
   - Thin application orchestrator: `MainActivity`, `PicfortApplication`, root bottom bar / `AppShell`, and Hilt dependency graph assembly.

### Agentic Best Practices Enforced

1. **Per-Module `AGENTS.md`**:
   - Every module contains a short `<module>/AGENTS.md` (< 80 lines) stating:
     - Scope and responsibilities.
     - Public API / navigation contract.
     - Fast verification commands (e.g. `./gradlew :feature:shopping:testDebugUnitTest`).
   - An agent working on a feature only reads that module's `AGENTS.md` and code, keeping the context window small and focused.
2. **Isolated Test Execution**:
   - Each feature owns its own Paparazzi snapshot tests (e.g. `ShoppingScreenshotTest.kt`).
   - Testing a feature change takes 2–3 seconds rather than running the full app suite.
3. **Convention Plugins (`build-logic`)**:
   - Shared Gradle configuration plugins (`picfort.android.feature`, `picfort.android.library`) ensure zero build script boilerplate when adding future features (e.g. School, Receipts).
4. **Incremental Migration**:
   - Done in sequential, test-verified steps with zero regression on CI or existing features.
