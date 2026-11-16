# ADR 0016 — Data survives schema changes: migrations for every store

**Status:** accepted  
**Date:** 2026-11-16  
**Supersedes:** the "Schema changes" section of [ADR 0008](0008-family-hub-storage.md) and the
"no migrations" rule of [ADR 0002](0002-family-wall-storage.md). **Generalises** the additive
migrations of [ADR 0010](0010-family-members-birthdays-recurring-events.md) to every database.

## Context

The project started with "no migrations: on a schema change the feature starts empty" (one user,
one server, owner decision 2026-11-09/11). ADR 0010 already made an exception for `family.db`
and `events.db`. Since then the Picfort server also holds receipts and spending history,
school timetables and the wall: data the family typed in and that has lasting value.
Losing it on an update is no longer acceptable (owner decision 2026-11-16).

## Decision

### Picfort server (SQLite)

- Every store (`wall.db`, `shopping.db`, `events.db`, `family.db`, `school.db`) uses
  `SqliteStore` (`store.py`) and registers a migration in `MIGRATIONS` for **every**
  `SCHEMA_VERSION` bump. A bump without a migration is a bug, caught by a test.
- Migrations may do more than ADR 0010's nullable columns (new tables, copy-and-rename for a
  changed column) as long as no row is lost.
- The old file is still copied to `<name>.v<old>.bak` **before** migrating, as a safety net.
- If a migration is missing or fails, the server **does not start empty**: it logs the error and
  the feature answers `503` until fixed. The data stays as it was.
- Each migration has a unit test that opens a file of the previous version with sample rows and
  checks them after the upgrade.

### Android (Room)

- Room tables that hold **user data or state that can't be rebuilt** (backup state, settings,
  reminders) get explicit `Migration`s with Room's migration tests.
- **Caches** that are a copy of the server (timeline from Immich's sync stream, offline copies of
  lists, events, school, family) may still be rebuilt: they reload on the next sync. They are
  listed in `core/database/AGENTS.md`.

## Consequences

- Schema changes cost a little more: a migration and a test per bump.
- `store.py`'s "start empty" path is removed (T-075); `wall.py` moves onto `SqliteStore`.
- Task briefs that change a schema (e.g. T-041, `shopping.db` v2) list the migration.
- README and module `AGENTS.md` no longer say "the feature starts empty".
