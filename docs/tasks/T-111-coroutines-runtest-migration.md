# T-111 — Migrate coroutine unit tests from `runBlocking` to `runTest` (P3)

**Status:** draft  
**Component:** Android (`:core:model`, `:feature:*`, `:app`)  
**Origin:** Code review of 2026-11-28.

## Summary

Several unit tests (including `PollingTest`, `EventsRepositoryTest`, `FamilyRepositoryTest`,
`ShoppingRepositoryTest`, `WishlistRepositoryTest`, `SyncEngineTest`, `PicfortAssistantHandlerTest`)
use `runBlocking` instead of `kotlinx.coroutines.test.runTest`.

Migrating them to `runTest` gives virtual time control (no real `delay()` waits in CI) and reduces
timing flakiness.
