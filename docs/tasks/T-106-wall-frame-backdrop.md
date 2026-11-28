# T-106 — Family Wall tinted by the frame's photo (blurred backdrop)

**Status:** in-progress  
**Component:** Android (`:feature:wall`)  
**Depends on:** T-091 (frame card), T-105 (unique frame versions)

## Summary

Like album art behind a music player, the photo on the frame, heavily blurred, fills the Family
Wall's background, so the Wall takes the colours of what hangs on the wall.

## Design

- `WallApi.framePhoto()` returns `FramePhoto(url, backdropUrl)`: the card's 1080×720 copy and a
  128×128 one (`/frame/photo?w=128&h=128&v=…`, the server's smallest size) for the backdrop.
- `PhotoBackdrop` (`:core:ui` since T-107): the tiny copy stretched over the screen (a soft blur on any Android) plus
  `Modifier.blur(40.dp)` (Android 12+), under a 62% veil of the theme background for readable text
  in light and dark mode; crossfades (900 ms) when the frame changes photo. The Scaffold and top
  bar are transparent only while there is a backdrop.

## Acceptance criteria

- [x] Paparazzi `wall_frame_backdrop` (light) and `wall_frame_backdrop_dark`.
- [x] All checks green.
- [ ] Owner: on the phone the Wall's background follows the frame within a minute.
