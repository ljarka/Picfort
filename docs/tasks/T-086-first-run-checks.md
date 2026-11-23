# T-086 — First-run checks: each setup mistake says what to fix

**Status:** ready  
**Component:** Picfort server, Android app (`:core:network`, `:feature:frame`, `:app`)  
**Depends on:** T-058 (one-page setup), T-085 (install guide)  
**Roadmap:** [Easy self-hosting](../../specs/roadmap.md#1-easy-self-hosting), second step

## Summary

The common mistakes of a first install get a plain message with the fix, in the app and in the
server's log, so a family can set up without help.

## Why

Errors named the problem but not the fix ("Not a Picfort server (HTTP 403)", "Can't connect to …").
The most common mistake, `IMMICH_URL=http://localhost:2283` with the server in Docker, wasn't
noticed at all: the setup saved the server and the wall failed later. A wrong Immich API key was
never reported (only the frame silently found no photos).

## Design

**Server:**
- `ImmichClient.check()`: `/api/server/ping` (is it Immich?), then `/api/albums` with the key
  → `ok` / `unreachable` / `not_immich` / `key_rejected`.
- At start: one log line with the result and the fix (`IMMICH_CHECK_LOG`); the server still starts.
- `/status` gains `"immich"` (additive; cached 60 s when fine, 5 s when not, so a fixed `.env`
  shows at the next Test).

**App** (`SetupHints.kt` in `:core:network`, pure functions):
- Picfort address: unreachable → phone on the home network, port, `docker compose ps`; AirPlay
  Receiver on a Mac's :5000 → `PICFORT_PORT=5050`; Immich's port or page → Picfort's own port;
  anything else → "isn't a Picfort server (HTTP n)", check the port.
- The server's `immich` → a `Partial` result naming the `.env` fix (INSTALL.md steps 2/3).
- Immich address: `Server: picfort` → that's Picfort's address, Immich uses 2283; AirPlay; unreachable.
- Setup page: a server that answers with a problem is saved and the fix shown, with "Try again"
  and "Continue anyway" (before, it was saved silently).

## Acceptance criteria

- [ ] Server: `check()` results and `/status` "immich" unit-tested; the API contract test lists the key.
- [ ] Server start log names the problem and the fix for each result (checked against the test Immich).
- [ ] App: `SetupHintsTest` for every message; setup and Settings use them.
- [ ] Setup page: Paparazzi snapshot of a server with an Immich problem ("Continue anyway").
- [ ] INSTALL.md: step 4 explains the log line; the trouble table lists the new app messages.
