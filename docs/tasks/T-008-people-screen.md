# T-008 — People screen (browse by face)

**Status:** draft  
**Component:** Android app  
**Depends on:** —

## Summary

Add a People screen that lets users browse photos by recognized person (Immich face recognition).

## Why

Immich does face recognition and already returns `people` in asset details. A dedicated
People screen (like Google Photos "People & Pets") lets users find all photos of a family
member instantly.

## Design notes (to be refined)

- Grid of person thumbnails with names; tapping opens an asset grid for that person
- API: `GET /api/people` for list, `GET /api/people/{id}/assets` for their photos
- "Send all from [person] to frame" shortcut: shuffle their photos as a slideshow
- Person name editing (Immich supports `PUT /api/people/{id}`)

## Acceptance criteria (to be defined during implementation)

- People list visible, tappable
- Person photo grid opens correctly
- Photos open in viewer
- Empty state when Immich has no recognized people
