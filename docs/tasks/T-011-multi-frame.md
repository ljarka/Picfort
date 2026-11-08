# T-011 — Multi-frame support

**Status:** draft  
**Component:** Android app  
**Depends on:** T-001

## Summary

Currently the app supports exactly one frame address. Allow multiple named frames
(e.g. "Living room", "Bedroom", "Grandma's house").

## Why

Power users may have multiple displays. Family members in different locations
(grandparents) could each have a frame that the family controls remotely via VPN.

## Design notes (to be refined)

- Settings: list of frames, each with name + address
- "Send to frame" becomes "Send to..." with a frame picker
- Family Wall is per-frame (each ImageFrameServer instance has its own wall)
  OR shared wall (all frames show the same posts from a designated primary)
- Default frame for quick-send

## Acceptance criteria (to be defined during implementation)

- Multiple frames configurable
- Send to any frame from asset viewer
- Frame Mode (T-003) selects which frame's wall to display
