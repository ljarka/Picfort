---
name: picfort-new-feature-module
description: Add a new :feature:<name> Gradle module to the Picfort Android app, or connect two features through a shared contract. Use when a task needs a new tab or feature module, or when one feature needs data or actions from another.
---

# New Picfort feature module

Rules come from [ADR 0011](../../../docs/decisions/0011-modular-architecture.md). Read it first if
you haven't.

## 1. Scaffold

```bash
./scripts/new-feature.sh <name>     # lowercase, e.g. school
```

It creates `feature/<name>/` (`build.gradle.kts`, source and test folders, a starter
`AGENTS.md`), adds `include(":feature:<name>")` to `settings.gradle.kts` and the dependency to
`app/build.gradle.kts`.

## 2. Code layout

- Package `com.github.ljarka.picfort.feature.<name>`; UI in `ui/<name>/` (`…Screen.kt`,
  `…ViewModel.kt`, `…Repository.kt`).
- Shared pieces go down, never sideways:
  - domain models and cross-feature interfaces → `:core:model` (no UI, network or database),
  - design tokens and shared composables → `:core:ui` (no network or database),
  - Room entities/DAOs → `:core:database`; server/Immich clients and stores → `:core:network`.
- The feature exposes entry composables only. `:app` owns navigation (`AppNavigation.kt`,
  `MainActivity.kt`) and the tab bar (`ui/shell/AppShell.kt`).

## 3. Talking to another feature

A feature **never** depends on another feature module. Instead:

1. Define an interface in `:core:model` (examples: `FamilyMemberProvider`, `FrameSender`,
   `DayPhotosProvider`).
2. Implement it in the feature that owns the data.
3. Bind it with Hilt in `:app` (`AppModule.kt`) or in the owning feature's bindings module.
4. List the contract in `core/model/AGENTS.md`.

## 4. Tests

- Unit tests in `src/test/`; Paparazzi screenshot tests for each screen state
  (`./gradlew :feature:<name>:recordPaparazziDebug`, then commit the snapshots).
- Composables read the locale with `LocalConfiguration.current.locales[0]`, never
  `Locale.getDefault()` (Android lint fails in CI).
- Device check: the `picfort-device-test` skill.

## 5. Docs

- Fill in `feature/<name>/AGENTS.md` (scope & files, public entry points, dependencies,
  verification; under 80 lines).
- Add the module to the module map in the root `AGENTS.md` and to the module list in
  `docs/ARCHITECTURE.md`.

## 6. Check

```bash
./scripts/check-modular-boundaries.sh
./gradlew :feature:<name>:testDebugUnitTest :feature:<name>:verifyPaparazziDebug
```

Then finish with the `picfort-finish-task` skill.
