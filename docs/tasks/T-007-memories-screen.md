# T-007 — Memories screen ("On This Day")

**Status:** draft  
**Component:** Android app  
**Depends on:** —

## Summary

Add a dedicated Memories screen showing photos taken on today's date in previous years.
Immich already has a `/api/memories` endpoint for this.

## Why

"On This Day" is one of the most emotionally resonant features in photo apps (Google Photos,
iCloud). Immich supports it natively. Surfacing it prominently adds a reason to open Picfort daily.

## Design notes (to be refined)

- New bottom nav item or home screen section (above the timeline)
- Horizontal pager: one card per year ("3 years ago", "5 years ago")
- Each card: photo grid for that day
- "Send to frame" shortcut on each memory card
- In Frame Mode (T-003): memories can be a slideshow segment on the anniversary date

## Open questions for implementing agent

- Should memories be a separate tab or a section at the top of the Timeline screen?
- How many years back does the Immich API return?
- Fallback when no memories for today: show a random "this week N years ago" selection?

## Acceptance criteria (to be defined during implementation)

- Memories visible and tappable
- Photos open in full-screen viewer
- "Send to frame" works from memory viewer
- Graceful empty state when no memories exist
