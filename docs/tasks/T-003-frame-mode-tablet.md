# T-003 — Frame mode (kiosk display on Android tablet)

**Status:** ready  
**Component:** Android app  
**Depends on:** T-001, T-002 (done), T-015 (local copy of the library)

## Summary

A "Frame mode" that turns an Android tablet into a wall display: a full-screen photo
slideshow that regularly switches to the Family Wall. No app chrome; a tap shows a small
overlay. The tablet runs the normal Picfort app, logged in to Immich with a parent's
account, and takes its slideshow settings from the Picfort server, so the family controls
it from their phones.

## Why

E-paper can't do color photos well or a live wall (ADR 0003). A tablet on the wall is
cheaper, brighter, and shows the Family Wall as it changes. Without frame mode, the wall
exists only on phones.

## Decisions (2026-11-10)

- **Account:** the tablet is logged in with a **parent's Immich account**, set up once
  like any phone. It sees that account's photos and its partners' (the sync copy, T-015),
  and it reads the wall with that token (ADR 0006). The account has full rights, so frame
  mode locks the UI (exit needs a PIN, see below). Posts made from the tablet are posted
  as that parent.
- **Settings come from the Picfort server:** the tablet reads `GET /slideshow` (mode,
  album, interval, quiet hours), the same settings the e-paper frame uses. They are changed
  on any phone in Settings → Picfort server (the existing frame screen). The tablet stores
  only what is about the device itself: the wall share, brightness and the PIN.

## Design

### Entry and exit

- Settings → Picfort server section → **"Use this device as a frame"** opens
  `Screen.FrameMode` (a route in the existing `NavHost`, not a separate activity).
- A flag `FRAME_MODE` (encrypted preferences) is set while frame mode is on. When the app
  starts with the flag set and the user logged in, `InitScreen` goes straight to frame mode
  (after a crash, an update or a reboot followed by opening the app).
- Exit: overlay → **Exit frame mode** → PIN dialog if a PIN is set, otherwise a confirmation.
  Exiting clears the flag. Back/gestures do nothing in frame mode (`BackHandler`).
- Recommend Android's **screen pinning** in the README (Settings → Security → App pinning).
  It stops someone from leaving with the home gesture, without device-owner setup.
- No start on boot in v1 (Android 10+ blocks starting activities from the background).
  Document "open Picfort after a reboot" instead.

### Screen

- Immersive: hide system bars (`WindowInsetsControllerCompat.hide(systemBars())`,
  `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`) while the route is shown; restore on exit.
- Screen on: `FLAG_KEEP_SCREEN_ON` on the window while the route is shown. No wake lock
  and no extra permission.
- Brightness: `WindowManager.LayoutParams.screenBrightness`, default 0.6. During the
  server's quiet hours (`quiet_start`–`quiet_end`): brightness 0.05, slideshow paused, and a
  small dim clock instead of photos. No quiet hours → always on.
- Works in landscape and portrait (tablets are mounted both ways). Photos are cropped to
  fill (`ContentScale.Crop`) unless the photo's orientation differs from the screen's. Then
  it is shown whole (`Fit`) on a blurred copy of itself.

### Display loop

```
┌─────────────────────────────────────────────────────────────────┐
│ photo → photo → … (wall_every photos) → Family Wall (30 s) → … │
└─────────────────────────────────────────────────────────────────┘
```

- **Photo duration:** the server's `interval_minutes` is meant for e-paper (15 min–24 h).
  On the tablet a photo stays for **30 s** by default (device setting: 15 s / 30 s / 1 min /
  5 min).
- **Wall share:** after every **N photos** (device setting, default 4, "never" possible)
  the wall shows for 30 s, if it has posts. A new post that arrives during the slideshow
  shows the wall right after the current photo, once.
- Server `mode = off` → random photos (the tablet always shows something).

### Photos

Picked by the tablet, following the server's `mode` and `album_id`:

| `mode` | Source |
|---|---|
| `random`, `off` | Random rows from the sync copy (`remote_assets` with the timeline filter, images only). Works offline. |
| `memories` | Sync copy: same month and day as today in earlier years (`strftime('%m-%d', localDateTime/1000, 'unixepoch')`), falling back to random when there are none. Works offline. |
| `album` | Immich `POST /api/search/random` with `albumIds` (as ImageFrameServer's picker does), 50 at a time. Albums aren't in the sync copy (T-015). Offline → random from the sync copy. |

- Avoid repeats: keep the last 50 shown ids in memory and skip them while others exist.
- Images: the Immich preview (`UrlProvider.getPreview`, ~1440 px). Preload the next photo
  with Coil (`imageLoader.enqueue`) while the current one shows; switch only when it's
  loaded (no black flash). Crossfade 1 s.
- Ken Burns: `Animatable` scale 1.0 → 1.08 and a small random translate over the photo's
  duration, `LinearEasing`. Reset on each photo.
- Caption: the date (`localDateTime`, e.g. "14 July 2019") in the bottom corner for 5 s,
  then fade out. No location in v1 (not in the sync copy).
- Videos are skipped.

### Family Wall segment

A display version of the T-002 screen, not the interactive one:
- Large layout: on the left (landscape) or top (portrait), up to 6 newest text posts as big
  cards (author avatar, name, relative time, full text up to 6 lines). Pinned first.
- On the right/bottom, the newest photo post large, with its caption and author.
- Reuses `WallRepository`, `FamilyMember`, `MemberAvatar` and the colors from T-002.
- Polls `GET /wall` every 60 s for the whole time frame mode is on (not only during the
  wall segment), so new posts are noticed.
- No posts, or the Picfort server is unreachable → the wall segment is skipped.

### Overlay (tap anywhere)

Shows for 5 s (each tap restarts the timer):
- Next / previous photo.
- **Family Wall**: opens the normal `FamilyWallScreen` (T-002), where you can post as the
  logged-in parent. Back returns to frame mode.
- **Frame settings** (device): photo duration, wall every N photos, brightness, PIN.
- **Exit frame mode** (PIN if set).
- Small status line: "Slideshow: Memories · from the Picfort server", or the error
  ("Can't reach the Picfort server, showing random photos").

### Settings and state

- Device settings in `FrameModeStore` (encrypted preferences): `FRAME_MODE`,
  `photoSeconds`, `wallEvery`, `brightness`, `pinHash` (SHA-256 of the PIN with a random
  salt, never the PIN itself).
- Server settings: `FrameDashboardApi.status()` / `GET /slideshow`, refreshed every 5 min
  and on resume. Last known settings kept in memory. If the server can't be reached at
  start, use random and no quiet hours.
- `FrameModeViewModel` holds the loop as a small state machine
  (`Photo(asset)`, `Wall`, `Quiet`), driven by a clock that tests can replace.

## Acceptance criteria

- [ ] Settings → "Use this device as a frame" opens frame mode full screen, with no system
      bars, and the screen stays on.
- [ ] Reopening the app (or after a crash) with frame mode on goes straight to frame mode.
- [ ] Photos follow the server's mode: random, memories (today's date in earlier years),
      album. Changing the mode on a phone takes effect on the tablet within 5 min.
- [ ] Random and memories keep working in airplane mode, for photos already in Coil's
      cache (else skip to the next).
- [ ] No black flash between photos; Ken Burns movement; date caption fades out.
- [ ] After N photos the wall shows for 30 s if there are posts; skipped when there are none.
- [ ] A post written on a phone appears on the tablet within ~60 s and brings up the wall.
- [ ] During the server's quiet hours the screen dims and the slideshow pauses; it resumes
      after them.
- [ ] Tap shows the overlay; it hides after 5 s.
- [ ] Back does nothing; exit asks for the PIN when one is set; a wrong PIN doesn't exit.
- [ ] Landscape and portrait both look right (photo fit/crop rule above).
- [ ] Unit tests: the loop state machine (wall every N, new post, quiet hours across
      midnight, mode off), photo picking (no repeats, memories fallback), PIN check.
- [ ] Paparazzi screenshots on a tablet config (landscape): photo with caption, wall
      segment, overlay, quiet clock.

## Out of scope (v1)

- Start on boot / device-owner kiosk.
- Location in the caption (needs Exif in the sync copy).
- Videos in the slideshow.
- Showing on the tablet what the e-paper frame shows (they pick photos independently).

## Notes for implementing agent

- Keep the screen on with `FLAG_KEEP_SCREEN_ON` via a `DisposableEffect` on the
  activity's window; restore brightness and system bars in `onDispose`.
- Random rows: `SELECT … WHERE <timeline filter> AND isVideo = 0 ORDER BY RANDOM() LIMIT 50`
  (reuse `TIMELINE_FILTER` in `RemoteAssetsDao`).
- Quiet hours can cross midnight (22 → 7): quiet when `start > end` and
  `hour >= start || hour < end`, else `start <= hour < end`.
- Don't log out on a 401 from the Picfort server (as in T-002); show it in the status line.
