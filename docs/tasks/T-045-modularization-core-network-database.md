# T-045 — Modularization: :core:network and :core:database

**Status:** ready  
**Component:** Android core libraries  
**Depends on:** T-043 (`:core:model`), [ADR 0011](../decisions/0011-modular-architecture.md)

## Summary

Extract networking infrastructure (`PicfortServerClient`, `ImmichClient`, OkHttp, token storage)
and local database persistence (Room, `ImagesDatabase`, DAOs) into dedicated core modules.

## Scope

1. **`:core:network`**:
   - `PicfortServerClient`, `PicfortServerException`
   - OkHttp client provider, logging interceptor, auth interceptor
   - `AccessTokenStore`, `ServerUrlStore`
2. **`:core:database`**:
   - `ImagesDatabase` Room configuration
   - `UsersDao`, `UserEntity`
   - Image sync entities
3. **Module `AGENTS.md`**:
   - `core/network/AGENTS.md` and `core/database/AGENTS.md`.

## Acceptance criteria

- [ ] `:core:network` and `:core:database` compile and test independently.
- [ ] Hilt bindings provided cleanly for consumers.
- [ ] `./gradlew testDebugUnitTest` green.
