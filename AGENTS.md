# Picfort — AGENTS.md

Android family photo ecosystem: unified Immich + local photo timeline, e-paper frame mode,
shared Family Wall, shopping lists, recurring family events, and member profiles.

This file is the index. Keep it short (< 150 lines); details live where the table below says.

## Start here (first time in this repo)

1. Read this file, then [docs/tasks/README.md](docs/tasks/README.md).
2. Run `./scripts/check-modular-boundaries.sh` and `./gradlew testDebugUnitTest`. Must be green before any change.
3. Before touching an area, read its module `AGENTS.md` and the ADRs it links ([ADR 0011](docs/decisions/0011-modular-architecture.md)).
4. Finish with the Definition of Done below; leave the next task specified.

## Where knowledge lives

| Need | Read |
|---|---|
| **What to do next, exactly** | [docs/tasks/](docs/tasks/README.md) (task queue) |
| Architecture & Modularization | [ADR 0011](docs/decisions/0011-modular-architecture.md) |
| Architecture decisions | [docs/decisions/](docs/decisions/README.md) (ADRs) |
| Roadmap & Status | [docs/STATUS.md](docs/STATUS.md) |
| Module scope & details | `<module>/AGENTS.md` |

Do not load the whole project. Each module's `AGENTS.md` tells you what to read.

## Module map

| Module | Scope | Status |
|---|---|---|
| `:core:model` | Pure domain models (`FamilyMember`, `ShoppingList`, `FamilyEvent`, provider interfaces) | done |
| `:core:ui` | Design tokens, `PicfortTheme`, `MemberAvatar`, `ServerRequiredContent`, shared drawables | done |
| `:core:database` | Room DB v22, DAOs (`ShoppingDao`, `UsersDao`, `EventsDao`), Entities | done |
| `:core:network` | `PicfortServerClient`, `ServerUrlStore`, `AccessTokenStore`, `FrameSettingsStore` | done |
| `:feature:wall` | Family Wall posts feed, photo posting, reactions, today summary panel | done |
| `:feature:shopping` | Shared shopping lists, checklist items, categories, receipts & wishlist | done |
| `:feature:events` | Family events, birthdays, recurring countdowns, detail & form | done |
| `:feature:family` | Family members management, nickname/color editing, hidden toggles | done |
| `:feature:frame` | Ambient e-paper frame control, photo push via `FrameSender`, pairing setup | done |
| `:feature:timeline` | Photo timeline, justified layout, full-screen viewer, albums, search, sync | done |
| `:app` | Thin shell: `MainActivity`, root navigation graph, `AppShell`, Hilt assembly | done |

## Architecture rules

1. **Strict Feature Isolation** — Features **NEVER** depend directly on each other ([ADR 0011](docs/decisions/0011-modular-architecture.md)).
2. **Cross-Feature Contracts in `:core:model`** — Interfaces like `FamilyMemberProvider`, `FamilyEventsProvider`, `FrameSender`, and `ImmichUsersSync` bridge features via Hilt in `:app`.
3. **Core Layer Boundaries** — `:core:model` has zero UI/network/db dependencies; `:core:ui` has no db/network dependencies.
4. **Fast Isolated Tests** — Run tests per module: `./gradlew :feature:<name>:testDebugUnitTest :feature:<name>:verifyPaparazziDebug` (takes ~2s).
5. **Enforced Boundaries** — Verified by `./scripts/check-modular-boundaries.sh`.

## Definition of Done (every task)

- [ ] `./scripts/check-modular-boundaries.sh` green.
- [ ] `./gradlew testDebugUnitTest` and relevant Paparazzi tests green.
- [ ] Module `AGENTS.md` updated for every module touched (< 80 lines).
- [ ] New decision → ADR; task queue updated: task `done` with commit hash.

## Environment

- JDK 21, Android SDK (API 37 compileSdk, 26 minSdk).
- ImageFrameServer (Python) in `ImageFrameServer/`.
