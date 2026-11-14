# ADR 0014 — The frame in a web browser, behind the server key

**Status:** accepted  
**Date:** 2026-11-14  
**Builds on:** [ADR 0007](0007-server-api-key.md)

## Context

The family wants a tablet (e.g. a Microsoft Surface) to work as a second picture frame: full
screen, never sleeping, showing what the e-paper frame shows. It should be reachable through the
server's public `https://` hostname (Cloudflare Tunnel), so the photos must not be open to anyone
who finds the address. A browser can't send the app's Immich login, and typing a key on every
start is not an option for a frame.

## Decision

- The server serves `GET /frame` (a self-contained page) plus `GET /frame/state` and
  `GET /frame/photo?w=&h=`. The page polls the state every 15 s and fetches the photo only when
  its version changes; it never sends anything to the e-paper frame (its 30 s limit stays).
- **Only with `PICFORT_API_KEY` set**; otherwise all three answer 404. The page itself has no
  secrets and opens without the key; it asks for the key once and keeps it in the browser's
  `localStorage`, sending it as `X-Picfort-Key` on `fetch` calls (images are loaded as blobs, so
  the key is never in a URL, a log or a referrer). "Forget key" removes it.
- **Full colour**: the server downloads the original from Immich (the frame's key) and scales it
  for the screen (cover/contain as `FRAME_FIT`), JPEG, never enlarged; sizes rounded up to 128 px
  and the last 6 results cached in memory. A photo sent from the phone is kept as sent.
- The frame's current photo is saved (`frame_current.json`, `frame_local.img` next to the
  preview), so after a restart the page shows it again, as the panel does.
- **Wrong keys are limited** for every endpoint: 10 wrong keys from one address in 15 min block
  that address (429, even with the right key) until the window passes. Behind Cloudflare the
  address is `CF-Connecting-IP`. Requests without a key don't count.
- Screen on: the page asks for a Screen Wake Lock (works over HTTPS) and enters full screen on a
  tap. For a dedicated tablet: Edge started full screen on its normal profile (so the key
  stays) and the OS power settings.

## Consequences

- Anyone with the key sees the frame's current photo (and could already control the frame).
  The key should be long and random (`openssl rand -hex 32`); a short one is guessable even
  with the limit.
- A key in `localStorage` stays on that browser until "Forget key" or clearing site data.
- The page can't show anything without a key on the server: home-network-only servers without
  a key (ADR 0004) don't get the browser frame.
