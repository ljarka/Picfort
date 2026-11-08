# T-010 — Bidirectional frame status

**Status:** draft  
**Component:** Android app + ImageFrameServer  
**Depends on:** T-001

## Summary

The app currently sends photos to the frame but has no feedback about what the frame
is currently showing. Add a live "Now on frame" section in the app.

## Why

Users want to know what their family sees on the display without walking to the room.
The `GET /status` endpoint already returns `currentAsset` — the app just doesn't surface it.

## Design notes (to be refined)

- New "Frame" section in Settings or a mini status widget in the Wall tab
- Shows: current photo thumbnail, slideshow mode, next change time
- "Send to frame" shortcut directly from the status widget
- History: last N photos shown on frame (requires new ImageFrameServer endpoint or log)

## Acceptance criteria (to be defined during implementation)

- App shows what's currently on the frame
- Thumbnail matches the actual displayed photo
- Status updates within 60 seconds of a change
