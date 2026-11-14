# T-072 — The frame in a web browser

**Status:** done (d9cc26c)  
**Component:** Picfort server (`browser_frame.py`, `frame_page.html`, `app.py`), Android app (`:feature:frame`)  
**Decision:** [ADR 0014](../decisions/0014-browser-frame.md)

## Summary

Owner request (2026-11-14): a page on the Picfort server that shows exactly what the frame shows,
so a tablet (Microsoft Surface) can be a second frame: full screen, never sleeping, reachable at
the server's public https address, behind the same key as the server, remembered by the browser.
Owner choice: full colour (not the e-paper look).

## Design

- `GET /frame` page; `GET /frame/state` (`version`, `shown`, `fit`); `GET /frame/photo?w=&h=`
  (the original from Immich, or the photo sent from the phone, scaled for the screen, JPEG).
  All three only with `PICFORT_API_KEY` (404 otherwise); the page opens without the key.
- The page: key form (kept in `localStorage`, sent as `X-Picfort-Key`), polls every 15 s,
  crossfades on a new version, refetches on resize, "Offline · showing the last photo" after 4
  failed polls, tap → full screen, Screen Wake Lock, buttons hide after 4 s.
- The frame's 30 s refresh limit (e-paper) is untouched: the page only reads.
- `FrameController`: `version` per photo shown, the phone's photo kept, both saved next to the
  preview so a restart keeps them.
- Wrong keys: 10 per address per 15 min, then 429 (`CF-Connecting-IP` behind Cloudflare).
- App: Settings → Picture frame → "Frame in a browser" with the link and "Copy link" (or
  "Needs a server key").

## Acceptance criteria

- [x] Server tests `test_browser_frame.py`: page open, API behind the key, off without a key,
  full colour + cover size + cache, phone photo, restart, wrong-key limit, scaling.
- [x] Live (temporary test container with a key, test Immich): wrong key → "Wrong key"; right
  key → photo; reload → key remembered; the frame changes → the page follows within 15 s.
- [x] Paparazzi `frame_browser_link`; unit tests, ktlint, lint, boundaries green.
- [ ] Owner: Cloudflare public hostname to the Picfort server, a longer key, Surface set up.
