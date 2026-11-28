# T-110 — Split oversized files & minor DI/naming cleanup (P2)

**Status:** draft  
**Component:** Picfort server (`picfort-server/`), Android (`:app`, `:feature:*`)  
**Origin:** Code review of 2026-11-28.

## Summary

Keep files small and focused for both human readability and AI agent context efficiency. Execute
gradually (one PR/commit per area) to keep blast radius small.

## Items to address

### 1. Picfort server
- Split `picfort-server/photo_ai.py` (1266 lines) into a `photo_ai/` package:
  `rules.py`, `albums.py`, `store.py`, `clients.py`, `worker.py` (re-exporting public symbols in
  `__init__.py` so imports stay clean).
- Extract route registrations from `picfort-server/app.py` (1125 lines) into Flask `Blueprint`s per
  feature.
- Rename misleading `browser_frame=bool(api_key)` parameter in `picfort-server/dashboard.py` to
  `api_key_set: bool`.
- Enforce `0600` file permissions on `photo_ai.db` (and its `.bak` copies) since it stores member
  Immich API keys.

### 2. Android
- Extract `PicfortNavHost.kt` from `app/.../MainActivity.kt` (550 lines).
- Add `@Singleton` to `NetworkModule.json()` (`app/.../network/NetworkModule.kt`) and review
  explicit OkHttp timeouts.
- Opportunistically split the largest Compose screen files when next touched:
  - `feature/shopping/.../ShoppingScreens.kt` (934 lines)
  - `feature/events/.../EventsScreens.kt` (840 lines)
  - `feature/wall/.../FamilyWallScreen.kt` (829 lines)
  - `feature/timeline/.../TimelineSettingsScreen.kt` (406 lines — extract `TimelineSettingsViewModel.kt`)
