# T-043 — Modularization foundation: build-logic & :core:model

**Status:** done  
**Component:** Android build & architecture  
**Depends on:** [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Set up Gradle build conventions (`build-logic` / convention plugins) and extract the pure domain models
into an independent `:core:model` library module.

## Scope

1. **Build conventions (`build-logic`)**:
   - `picfort.android.library`: base configuration for Android library modules (compileSdk, minSdk, Kotlin, viewmodel/compose flags).
   - `picfort.android.feature`: convention plugin for feature modules (enables Compose, Hilt, Paparazzi).
2. **`:core:model` module**:
   - Domain data models shared across modules:
     - `FamilyMember`
     - `BirthDate`
     - `ImmichAsset` / photo metadata models
     - Common enums and value classes
   - Pure Kotlin/Android library with NO Compose or UI dependencies.
3. **Module `AGENTS.md`**:
   - Create `core/model/AGENTS.md` describing model contracts and immutability rules.

## Acceptance criteria

- [ ] `:core:model` compiles independently with `./gradlew :core:model:build`.
- [ ] `:app` depends on `:core:model` and existing unit tests and Paparazzi pass without regression.
- [ ] `core/model/AGENTS.md` created.
