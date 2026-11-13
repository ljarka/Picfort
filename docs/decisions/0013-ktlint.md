# 0013 — ktlint for Kotlin code style

**Status:** accepted (owner request 2026-11-13)  
**Deciders:** owner, Claude  
**Date:** 2026-11-13  

## Context

Code is written by people and by AI agents in many small sessions. Without a formatter the style
drifted: import order, wrapping, `if`/`else` without braces, trailing commas sometimes yes,
sometimes no. Review time went to noise, and diffs mixed formatting with real changes.

## Decision

- Gradle plugin `org.jlleitschuh.gradle.ktlint` (14.2.0, ktlint 1.8.0) is applied to every
  subproject from the root `build.gradle.kts`; the ktlint version is `ktlint` in
  `gradle/libs.versions.toml`. Generated code under `build/` is excluded.
- Rules live in `.editorconfig`, so the IDE formats the same way:
  - `ktlint_code_style = android_studio`, 120 columns, 4 spaces.
  - Trailing commas allowed on declarations and calls (the house style; cleaner diffs).
  - `@Composable` functions may be PascalCase.
- CI runs `./gradlew ktlintCheck` with the unit tests; it is in the Definition of Done.
  `./gradlew ktlintFormat` fixes almost everything; what it can't (mostly long lines) is fixed by
  hand. A file-level `@Suppress("ktlint:standard:<rule>")` is allowed only with a reason, e.g. test
  data kept one record per line.
- A versioned pre-commit hook (`scripts/git-hooks/pre-commit`, installed per clone with
  `./scripts/install-git-hooks.sh`) runs `ktlintFormat` on the staged `.kt`/`.kts` files only,
  stages the result and keeps unstaged changes aside; what it can't fix stops the commit
  (`--no-verify` skips it). CI stays the gate for clones without the hook.
- The one-time reformat is a separate commit, listed in `.git-blame-ignore-revs`.

## Consequences

- One style everywhere; formatting is no longer discussed in review or mixed into feature diffs.
- One more step before pushing (`ktlintFormat`). A ktlint upgrade may bring new rules: upgrade
  on purpose, run `ktlintFormat`, commit that separately.
- `git blame` needs `git config blame.ignoreRevsFile .git-blame-ignore-revs` (GitHub uses the
  file automatically).
