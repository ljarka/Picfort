# T-058 — First start on one screen; https:// by default

**Status:** done (bca44dd; mockup `docs/wireframes/setup-one-screen-v1.png` approved 2026-11-13)  
**Component:** Android app (`:app`, `:feature:frame`, `:core:network`)  
**Depends on:** T-002 (Picfort server step), ADR 0011

## Summary

A fresh install asked three times: Immich URL, then login, then the Picfort server. Now one page
asks for all of it with one Connect. A Picfort address typed without a scheme got http://; it now
gets https:// unless it is on the local network, the same rule as the Immich address.

## Design

- `SetupScreen` (`:app ui/init`): Immich address, email, password; "Picfort server · optional" with
  address, "Add API key" and, for a local Immich address, "Same computer: <host>:5000" (the
  server's default port, `docker/example.env`). Each address shows "Will connect to <url>".
- Connect: Immich ping → login → Picfort check (`PicfortServerCheck`, `:feature:frame`: `/status`,
  then the wall with my login). Errors under their field. Picfort failed: Immich shows "Signed in as …
  (Change)", Try again or "Skip the Picfort server for now". Saved or skipped marks the old
  one-time Picfort step as done, so it now shows only for installs from before.
- Logged out later: the same page, Immich address filled in, no Picfort section if one is set.
- `defaultSchemeFor` (`:core:network`): http:// for local network hosts (private IPv4/IPv6,
  `*.local`/`.lan`/…, single names, Tailscale), https:// otherwise (public IPs too: plain http
  would be refused there anyway). Used by `normalizeServerUrl` and `normalizeFrameUrl`.
- Old `LoginContent`/`LoginViewModel` and `ServerConfiguration*` removed.

## Acceptance criteria

- [x] Fresh install: one page, one Connect, straight to the Wall with Immich and Picfort set.
- [x] Wrong login and an unreachable Picfort server show their errors; Skip works.
- [x] "picfort.example.com" → https://; "192.168.0.105:5000", "nas:5000" → http:// (unit tests).
- [x] Paparazzi: setup empty, filled, connecting, Picfort failed, dark; README, AGENTS.md.

## Device check (2026-11-13, emulator, test account, test server)

- `maestro/smoke.yaml` (login subflow now the one page) and `maestro/setup-errors.yaml`
  (wrong email → "Wrong email or password"; restart keeps the Immich address; "picfort.invalid" →
  "Will connect to https://picfort.invalid", can't be reached → Signed in + Skip). All 6 flows pass.
