# T-085 — Install guide: from "I have Immich" to "the app is connected"

**Status:** in-progress (guide `c64a275`; left: a trial by someone on a clean machine, see below)  
**Component:** Docs (README, `INSTALL.md`)  
**Depends on:** —  
**Roadmap:** [Easy self-hosting](../../specs/roadmap.md#1-easy-self-hosting), first step

## Summary

One page, `INSTALL.md` in the repo root, that takes another self-hosting family from a running
Immich to every phone connected to Picfort, in under an hour, without help.

## Why

"Easy self-hosting" is a success criterion ([mission](../../specs/mission.md#what-success-looks-like)).
Everything needed is in the README today, but spread over a long developer-oriented page (build,
CI, releasing, e-paper wiring), and some steps are only implied: which machine, which address
Immich has from inside Docker, how to check the server works, installing an APK, one account per
family member.

## Design

`INSTALL.md`, plain and short, numbered steps, each ending with how to tell it worked:

1. **What you need:** Immich (v2 or newer) reachable at home, an always-on machine with Docker
   (amd64 or arm64), Android 8.0+ phones, ~45 minutes.
2. **Immich API key** with the four read permissions (link to README for why each).
3. **Server:** the release's `docker-compose.yml` + `example.env`, fill in `.env`, `docker compose up -d`.
   Which `IMMICH_URL` to use when Immich runs on the same machine.
4. **Check it:** `docker compose ps`, `curl …/status` → `"status": "ok"`, the log line when the
   Immich settings are missing.
5. **The machine's address** on the home network.
6. **App:** the APK from the latest release, installing from the browser.
7. **First start** (one-page setup, T-058), permissions.
8. **The rest of the family:** one Immich user per person, the same setup on each phone.
9. **Keep it running:** what to back up (`./data`), updating the server and the app.
10. **If something doesn't work:** a table of the common mistakes and their fixes.
11. **Optional next steps:** links to README sections (outside access, browser frame, e-paper frame,
    backup settings) instead of repeating them.

README: a link to `INSTALL.md` at the top and in the server section; the server section keeps the
reference material (variables, API key permissions, tunnel, frame).

## Acceptance criteria

- [x] `INSTALL.md` covers steps 1–11 above; every command matches `docker/` and the release assets.
- [x] Download URLs resolve to the latest release's assets; `docker compose config` accepts the
      downloaded files with a filled-in `.env`.
      _2026-11-23: v0.9.17's files started under another name and port with the test Immich:
      `/status` answered `"status":"ok"`; with an empty key the log showed the line quoted in step 4._
- [x] README links to it at the top and from "Picfort server".
- [x] No real hosts or accounts: `photos.example.com`, `192.168.1.20`, `test@example.com`.
- [ ] *(Owner)* Someone follows it on a clean machine in under an hour. Their notes become fixes
      in the guide or the next self-hosting tasks (first-run checks, release notes).
