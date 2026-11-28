# T-108 — Hardening, privacy isolation & review fixes (P0)

**Status:** done (`c79be94`)  
**Component:** Picfort server (`picfort-server/`), Android (`:core:network`, `:core:ui`, `:feature:wall`, `:feature:timeline`)  
**Origin:** Code review of T-088..T-107 and earlier server/Android foundations (2026-11-28).

## Summary

Fix high-impact security, privacy and correctness issues found during the 2026-11-28 audit before
adding more features.

## Items to address

### 1. Finish `owner_id` isolation in `/photo-insights/*` and frame slideshow (T-104 follow-up)
- `picfort-server/app.py`, `picfort-server/photo_ai.py`, `picfort-server/slideshow.py`:
  - `/photo-insights/labels` is already scoped to `my_owners()`, but `GET /photo-insights/cleanup`,
    `GET /photo-insights/status`, `POST /photo-insights/<id>/dismiss`, `DELETE /photo-insights/<id>`,
    and `/photo-insights/album-suggestions` are still global across all libraries.
  - Scope `cleanup` and `status` to the caller's own assets (`owner_id == g.user.id`, or confirm with
    the owner if Immich partners should be included — default: own photos only for cleanup/dismiss/delete
    since only the owner can trash/untag them in Immich).
  - Verify ownership before `dismiss` and `delete`.
  - Add `owner_id` column (with a SQLite migration per ADR 0016) to `album_suggestions` and filter
    per owner.
  - Scope `PhotoAiStore.frame_worthy_ids()` so the frame's *Best only* mode only picks assets
    accessible by the server's `IMMICH_API_KEY` (server owner + their Immich partners), preventing
    403/404 frame fetch errors on private member libraries.

### 2. SSRF protection in `wishlist_preview.py` (T-041 follow-up)
- `picfort-server/wishlist_preview.py`:
  - `validate_http_url()` currently checks only `http`/`https` scheme and `netloc`, and
    `urllib.request.urlopen()` follows redirects automatically.
  - Resolve hostnames via `socket.getaddrinfo` and reject any address where
    `not ipaddress.ip_address(ip).is_global` (loopback, private LAN, link-local, Docker hostnames).
  - Re-validate every redirect hop (max 3 redirects) instead of blindly following 301/302.

### 3. `SecurePreferencesStore` Keystore recovery & non-blocking writes
- `core/network/src/main/java/com/github/ljarka/picfort/core/network/SecurePreferencesStore.kt`:
  - If Android Keystore loses/invalidates `picfort.preferences.aes.v1` (e.g. after device restore or
    lockscreen reset), `decrypt()` fails permanently and crashes the app on every launch.
  - Add recovery in `init`: log a warning, clear corrupt DataStore entries, recreate the Keystore
    key, and start clean so the user lands on login instead of a crash loop.
  - Avoid blocking the caller (often Main Thread) with `CountDownLatch.await()` inside `putString()`
    — update `cache` immediately and persist asynchronously on `Dispatchers.IO` (or provide a
    `suspend` API).

### 4. Bound token/IP caches and evict removed member keys from RAM
- `picfort-server/auth.py`: `immich_partners` caches raw Bearer tokens as dict keys with no max
  size or eviction. Hash tokens (SHA-256) and bound/evict expired entries like `Authenticator._cache`.
- `picfort-server/app.py` (`require_api_key`): cap the `wrong` IP dictionary size and only trust
  `CF-Connecting-IP` when `request.remote_addr` is a trusted proxy (loopback / private network /
  explicit opt-in).
- `picfort-server/photo_ai.py` & `picfort-server/dashboard.py`: evict the member's entry from
  `PhotoAiWorker._member_clients` and `dashboard.member_stats` when `DELETE /photo-insights/my-library`
  is called.

### 5. Today's UI & worker bug fixes
- `core/ui/.../PhotoBackdrop.kt`, `TimelineScreen.kt`, `FamilyWallScreen.kt`:
  - Move the `BACKDROP_VEIL` overlay inside `Crossfade` (or animate its alpha) so fading `url -> null`
    does not flash the un-veiled photo.
  - Keep `Scaffold(containerColor = Color.Transparent)` unconditionally (since `PhotoBackdrop` already
    draws `MaterialTheme.colorScheme.background`), so the 900 ms fade-out is not cut short.
  - Request a small decode size in Coil (`ImageRequest.Builder(...).size(64)`) so Android 8–11
    devices (where `Modifier.blur` is a no-op) still get a soft backdrop on the Timeline.
- `picfort-server/photo_ai.py` (`PhotoAiWorker.server_user()`):
  - Do not permanently cache `None` on transient network/timeout errors at startup. Cache `None`
    permanently only for 401/403 (missing `user.read`), otherwise retry after a short TTL.

## Acceptance criteria

- [x] Unit tests for `owner_id` isolation in `/photo-insights/cleanup`, `dismiss`, `delete`,
      `album-suggestions`, and `frame_worthy_ids()`.
- [x] Unit tests for SSRF blocking in `test_wishlist.py` (private IPs, loopback, redirects).
- [x] Unit test for `SecurePreferencesStore` recovery from corrupted/undecryptable preferences.
- [x] Unit test for `PhotoAiWorker.server_user()` retrying after transient errors.
- [x] `PhotoBackdrop` fade-out and Android < 12 downscale verified; Paparazzi tests green.
- [x] `./scripts/check-modular-boundaries.sh`, `./gradlew ktlintCheck`, unit tests, and pytest green.
