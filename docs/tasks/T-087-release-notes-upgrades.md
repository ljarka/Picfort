# T-087 — Release notes & upgrades

**Status:** done  
**Component:** CI/CD (`.github/workflows/release.yml`, `.github/release-upgrade-guide.md`)  
**Depends on:** T-085 (INSTALL.md update path, step 9), T-075 (schema migrations)

## Summary

Each GitHub Release shows what changed (grouped by `feat:` / `fix:` commit prefix) and a standard
"How to update" section so a non-technical family member can follow the upgrade in under a minute.

## Why

The previous release workflow used `--generate-notes`, which produces a raw list of all commits
(including docs, chores, internal refactors). That list is confusing for a family installing an
update. The upgrade path (`docker compose pull && docker compose up -d`) was documented in
INSTALL.md step 9 but not linked from the release page itself.

## Design

### `.github/release-upgrade-guide.md`

A short Markdown snippet committed to the repo:
- **Server:** `docker compose pull && docker compose up -d` (schema auto-migrated, `.bak` saved).
- **Android app:** download the APK from the release, install over the old one.
- Link to INSTALL.md step 9 for troubleshooting.

This file is committed (not generated) so it can be edited without touching the workflow.

### `release.yml` — "Build release notes" step (before "Publish release")

Script that:
1. Finds the previous version tag (`git describe --tags --abbrev=0 <tag>^`).
2. Collects commits since that tag, groups them:
   - `## ✨ New` — lines starting with `feat`
   - `## 🐛 Fixed` — lines starting with `fix`
   - Other prefixes (docs, chores) are omitted — not useful to end users.
3. Appends `.github/release-upgrade-guide.md`.
4. Writes the result to `release-notes.md`.

The "Publish release" step now uses `--notes-file release-notes.md` instead of `--generate-notes`.

### Schema migration tests in CI

`test_migrations.py` already verifies that every SQLite store has a migration for every previous
schema version and that the upgrade succeeds from any version. This runs in CI on every push
(Python test matrix in `ci.yml`). No new test infrastructure is needed.

## Acceptance criteria

- [x] `release.yml` groups `feat:` commits under "✨ New" and `fix:` commits under "🐛 Fixed".
- [x] Every release page ends with the "How to update" section from `.github/release-upgrade-guide.md`.
- [x] Schema migration tests (`test_migrations.py`) pass in CI — upgrade path is covered.
- [x] Commits with other prefixes (docs, chores) are omitted from the release notes.
