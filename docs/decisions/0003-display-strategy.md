# ADR 0003 — Display strategy: tablet primary, e-paper secondary

**Status:** accepted  
**Date:** 2026-11-08

## Context

Picfort was originally built with a Waveshare 13.3" Spectra 6 e-paper display as the
target. After use, the owner found that e-paper has significant limitations for photo display:
- Only 6 discrete ink colors; all others dithered
- ~19-second full refresh; unsuitable for the Family Wall (frequent updates)
- No backlight; unreadable in dark rooms
- Very high cost per panel (~$229–$259)

An Android tablet offers full color, instant refresh, touch input, and can run Picfort
directly — meaning no separate ImageFrameServer is needed for display logic.

## Decision

**Primary target: Android tablet in kiosk/frame mode.**

The Android app gains a "Frame Mode" that turns it into a wall display:
- Full-screen, no navigation chrome
- Rotates between: photo slideshow → Family Wall feed → photo slideshow
- Screen-on forced (wakelocks, screen brightness schedule)
- Touch wakes the display for interaction

**Secondary target: e-paper via ImageFrameServer (maintained, not abandoned).**

ImageFrameServer continues to work for e-paper setups. The slideshow and wall rendering
for e-paper are distinct code paths in ImageFrameServer. E-paper is well-suited for
static/slow-changing content (single pinned photo, daily memory); it is not suitable for
the live Family Wall feed.

## Consequences

- T-003 adds Frame Mode to the Android app — a new Activity or fullscreen Composable.
- ImageFrameServer does not need to render the Family Wall for e-paper in real time;
  it may instead show the latest pinned post on each slideshow advance.
- Users with an Android tablet get the full Family Wall experience.
- Users with e-paper get the photo slideshow and the latest pinned Wall post.
