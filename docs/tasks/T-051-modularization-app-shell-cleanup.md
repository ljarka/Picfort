# T-051 — Modularization: Thin :app shell, root navigation & architecture guard

**Status:** done  
**Component:** Android app shell & architecture guard  
**Depends on:** T-043 through T-050, [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Complete the modularization by slimming `:app` into a thin application shell and adding an
architecture guard to permanently enforce module boundary rules for future agents.

## Scope

1. **Thin `:app` shell**:
   - `MainActivity.kt` and `PicfortApplication.kt`
   - Root navigation graph wiring feature routes
   - Root floating bottom navigation bar
2. **Architecture Guard**:
   - Added `scripts/check-modular-boundaries.sh` verifying:
     - No `:feature:*` module depends on another `:feature:*` module.
     - Modules only declare necessary dependencies on `:core:*`.
     - Every module has an up-to-date `<module>/AGENTS.md` (< 80 lines).
3. **Documentation**:
   - Created root `AGENTS.md` with multi-module navigation, module map, and fast sub-module verification commands.
   - Updated `docs/STATUS.md`.

## Acceptance criteria

- [x] `:app` contains only application lifecycle, root navigation assembly, and Hilt root bindings.
- [x] `./scripts/check-modular-boundaries.sh` passes on CI and locally.
- [x] Root `AGENTS.md` and `docs/STATUS.md` reflect the modular architecture.
- [x] Full app builds, unit tests, and all feature screenshot tests pass cleanly.
