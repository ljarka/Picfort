# T-003 — Frame mode (kiosk display on Android tablet)

**Status:** ready  
**Component:** Android app  
**Depends on:** T-001, T-002

## Summary

Add a "Frame Mode" to the Picfort app that turns an Android tablet into a wall display.
The screen shows a rotating presentation: photo slideshow → Family Wall → photo slideshow.
No navigation chrome visible; touch wakes an interactive overlay.

## Why

E-paper has fundamental color limitations for photos (ADR 0003). An Android tablet
mounted on the wall is cheaper, brighter, and supports the full Family Wall experience
including live updates. Frame Mode makes Picfort the display app — no separate software needed.

## Design

### Entry point

Settings screen → "Frame mode" toggle.
When enabled, app restarts into `FrameActivity` and locks there until toggled off
(requires a swipe-up gesture or PIN to exit, to prevent accidental exit).

### FrameActivity

Full-screen, no status bar, no navigation bar (`WindowInsetsController`).
Wakelock: `PowerManager.SCREEN_BRIGHT_WAKE_LOCK` (or `PARTIAL_WAKE_LOCK` + `FLAG_KEEP_SCREEN_ON`).
Brightness: configurable (default: 50%), with auto-dim schedule (22:00–07:00 → 10%).

### Display loop

```
┌──────────────────────────────────────────────────┐
│  Slideshow (2 min)  →  Wall feed (30 s)  → loop  │
└──────────────────────────────────────────────────┘
```

Both durations configurable in Settings.

**Slideshow segment:**
- Full-screen photo, pan-and-zoom Ken Burns effect (slow animated `graphicsLayer` scale + translate)
- Photo source: Immich slideshow album (same as configured in ImageFrameServer settings),
  or random from all Immich assets
- Advances every 30 seconds (configurable)
- Shows photo date + location in bottom overlay (subtle, fades after 3 s)

**Wall segment:**
- Shows last 5 posts as cards, animating in from bottom
- If no posts: skip Wall segment and continue slideshow
- Polls `GET /wall` every 60 s in the background; shows new-post indicator dot on FAB
  when a post arrives during slideshow

### Touch interaction

- Single tap → show bottom overlay with controls: exit frame mode, go to timeline,
  force-refresh Wall
- Overlay auto-hides after 5 seconds of no interaction

### Exit

- Overlay exit button → confirms with PIN (if set) or swipe-up gesture
- Developer option: long-press back button 3× to force exit

## Acceptance criteria

- [ ] Frame Mode toggle in Settings
- [ ] FrameActivity opens full-screen with no system UI
- [ ] Screen stays on; does not turn off during frame mode
- [ ] Slideshow advances through Immich photos with Ken Burns effect
- [ ] After 2 minutes, transitions to Wall feed for 30 seconds
- [ ] Wall feed shows latest posts; loops back to slideshow
- [ ] If no posts exist, Wall segment is skipped
- [ ] Single tap shows overlay with controls
- [ ] Overlay auto-hides after 5 seconds
- [ ] Exit from frame mode requires confirmation
- [ ] Auto-dim works between 22:00 and 07:00
- [ ] Durations configurable in Settings

## Notes for implementing agent

- `WindowInsetsController` for hiding system bars: use `hide(WindowInsetsCompat.Type.systemBars())`.
- Keep wakelock as a resource that is acquired in `onResume` and released in `onPause`.
- Ken Burns: use `Animatable` for scale (1.0→1.1) and translate (random direction),
  with a 30-second duration, `EaseInOut` easing. Reset on each photo change.
- Photo preloading: load next photo while current is displaying to avoid black flash.
- PIN storage: use `EncryptedSharedPreferences` (same pattern as token storage in `ui/preferences/`).
- If Immich is unreachable, fall back to Room-cached asset list.
