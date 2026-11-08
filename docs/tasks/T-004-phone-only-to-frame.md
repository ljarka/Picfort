# T-004 — Send phone-only photos to frame

**Status:** ready  
**Component:** Android app + ImageFrameServer  
**Depends on:** —

## Summary

Currently only Immich assets can be sent to the frame. This task enables sending
photos that exist only on the phone (not yet backed up to Immich).

## Why

A common case: take a photo, want to show it on the frame immediately, before backup runs.
Listed in README as a known limitation.

## Design

### Android app

When the user taps "Send to frame" on a local (MediaStore-only) asset:
1. Read the full-resolution bitmap from MediaStore via `ContentResolver`
2. Compress to JPEG (max 1920px longest edge, quality 85)
3. `POST /run/upload` with multipart body: `photo` file

The existing "Send" button already checks `assetType`. Extend the branch for `LOCAL` assets.

### ImageFrameServer

New endpoint: `POST /run/upload`
- Accepts `multipart/form-data` with a `photo` field (JPEG/PNG)
- Saves to a temp file, renders it to the display using existing `frame.py` logic
- Returns 202 when rendering starts; same behavior as `GET /run?assetId=X`

Reuse `frame.render(image_path)` — no new rendering logic needed.

## Acceptance criteria

- [ ] "Send to frame" button visible and enabled for phone-only photos
- [ ] Photo appears on frame within the normal rendering time (~19s for e-paper)
- [ ] `GET /status` shows the uploaded photo as `currentAsset: null` (no Immich ID)
- [ ] Oversized photos (>10 MB) are compressed before upload
- [ ] 409 (busy) handled same as existing flow: shown as snackbar

## Notes for implementing agent

- Read MediaStore URI: `contentResolver.openInputStream(uri)` → `BitmapFactory.decodeStream`
- Avoid loading full 50 MP bitmap into memory; use `BitmapFactory.Options.inSampleSize`
- Temp file on ImageFrameServer: `tempfile.NamedTemporaryFile(suffix='.jpg', delete=False)`; delete after render
