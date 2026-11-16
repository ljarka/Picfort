# T-075 — Schema migrations everywhere (no more "start empty")

**Status:** ready  
**Component:** Picfort server, Android (`:core:database`)  
**Depends on:** ADR 0016. **Before:** T-041 (first `shopping.db` bump, which holds receipts).

## Summary

Make every schema change keep the data, as ADR 0016 decides.

## Server

1. `store.py`: remove the "no migration → start empty" path. A missing or failing migration
   logs an error and leaves the file untouched; the store's endpoints answer `503`.
   Keep the `.v<old>.bak` copy before migrating.
2. `wall.py` onto `SqliteStore` (same schema, `SCHEMA_VERSION` 1, no data change).
3. Test: every store has a migration for each version from 1 to `SCHEMA_VERSION - 1`.
4. Test helper: create a file of version N with sample rows, open it with the current store,
   check the rows. Use it for the existing `family.db` and `events.db` migrations.
5. Update the docstrings of `store.py`, `wall.py`, `school.py`.

## Android

1. In `core/database/AGENTS.md`, list each Room table as **cache** (rebuilt from the server) or
   **state** (must survive).
2. If any table is state: replace `fallbackToDestructiveMigration` with explicit `Migration`s
   for it (export the schema, `MigrationTestHelper` test). If all are caches, keep the fallback
   and write that down.

## Done when

- Server tests green, including the new migration checks.
- `./gradlew testDebugUnitTest` green; DoD in `AGENTS.md`.
- README: no "starts empty" left.
