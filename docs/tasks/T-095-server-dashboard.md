# T-095 — Server dashboard: `/dashboard` web page with stats and health

**Status:** done (`0c38387`)  
**Component:** Server (`picfort-server`), release workflow  
**Depends on:** T-072 (browser frame page and key handling), T-093 (photo AI worker state)

## Summary

A web page on the Picfort server, `http://<server>:<port>/dashboard`, for a quick look from a
computer: is everything healthy, what version runs, how far the photo AI is, how big the data
is, and what went wrong recently. Read-only; it changes nothing.

## Access (same pattern as `/frame`, T-072)

- `GET /dashboard` (the HTML) is an open path like `/frame`: it holds no data.
- The data comes from `GET /dashboard/data` with the `X-Picfort-Key` header (`require_api_key`).
  No Immich token: it is the server owner's view, not a family feature.
- **One key for both pages:** the dashboard reads and writes the same `localStorage` entry as the
  frame page (`picfortFrameKey`). Both pages share an origin, so a key entered on `/frame` opens
  `/dashboard` and vice versa; "Forget key" on either forgets it for both. No cookies (they
  would add CSRF handling for no gain). A different address (LAN IP vs tunnel domain) is another
  origin: enter the key once per address.
- Wrong key -> 401 -> the key form again; 429 -> "too many wrong keys" message (as `/frame`).
- Without `PICFORT_API_KEY` the server is open on the home network anyway (ADR 0004), and so is
  the dashboard.

## Content of `/dashboard/data` (JSON) and the page

1. **Server:** version and git revision, start time and uptime, Python version, free disk on
   `/data`, size of `/data`.
   - Version: `ARG PICFORT_VERSION` / `ARG PICFORT_REVISION` in the `Dockerfile` -> `ENV`;
     `release.yml` passes `build-args` from the tag and `github.sha`. Local builds show `dev`.
2. **Immich:** `immich_health()` state (ok / unreachable / not_immich / key_rejected) with the
   check's latency, Immich version (`GET /api/server/version`), library size
   (`GET /api/assets/statistics`: images, videos).
3. **Frame:** slideshow settings (mode, album, interval, quiet hours, filter, best only), next
   change, `last_error`, whether the frame is busy, browser frame on/off, and a thumbnail of
   what it shows (`/current`, fetched with the key as a blob).
4. **Photo AI** (when configured): enabled, model, running, active hours, last error, analyzed
   count and **coverage** of the library (analyzed / Immich images), cleanup candidates,
   frame-worthy, sensitive, failures skipped for good (`attempts >= MAX_ATTEMPTS`), deep-scan
   page, counts per category.
5. **Data stores:** for each `SqliteStore` (wall, shopping, family, events, school, photo_ai):
   file size, schema version, `migration_error`, and row counts per table, plus the `*.bak`
   copies in `/data` (name, size, date). Generic `SqliteStore.summary()` from `sqlite_master`,
   so new stores show up without dashboard code. Counts only: no names, texts or amounts.
6. **Recent problems:** the last 50 log records at WARNING and above, kept in memory by a
   ring-buffer `logging.Handler` installed at startup (time, logger, message). Cleared on
   restart; nothing written to disk.

The page: one static `dashboard_page.html` (vanilla JS, no build step, like `frame_page.html`),
cards per section with a green / amber / red dot (Immich not ok, a migration error or an AI
error -> red/amber), refreshes every 30 s and on focus, light and dark mode, readable on a phone.

## Files

- `picfort-server/dashboard.py`: `collect_dashboard(...)`, `RecentLogHandler`,
  `register_dashboard(app, ...)`.
- `picfort-server/dashboard_page.html`.
- `store.py`: `SqliteStore.summary()`; `photo_ai.py`: `PhotoAiStore.dashboard_stats()`.
- `immich.py`: `server_version()`, `asset_statistics()` (short timeout, failures -> null).
- `app.py`: open path `/dashboard`, wiring in `create_app` and `__main__`, start time.
- `Dockerfile`, `.github/workflows/release.yml`: version build args.
- Tests: `tests/test_dashboard.py` (key required for data, page open, JSON shape with fake stores,
  Immich down -> section degrades, log ring buffer keeps 50), `test_api_contract.py` routes.
- Docs: `docs/STATUS.md`, README (Server dashboard section).

## Acceptance criteria

- [x] `/dashboard` opens without a key prompt when `/frame` already has the key (and vice versa).
- [x] Data endpoint answers 401 without/with a wrong key; never exposes names, texts, amounts or tokens.
- [x] A failing Immich or a store with a migration error shows in red; the rest of the page still renders.
- [x] Server tests green (`python -m unittest discover -s tests -t tests`).
- [ ] Owner: open `/dashboard` on the family server after the release.

## Implementation notes

- Rendering checked by running the page script in JavaScriptCore with a minimal DOM on real
  `/dashboard/data` JSON (all sections render; log messages are escaped). Not yet opened in a
  real browser.

## Out of scope

Actions (restart, re-run AI, clean backups), charts over time, Android screen.
