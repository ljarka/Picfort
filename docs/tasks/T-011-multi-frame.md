# T-011 — Multi-frame support

**Status:** ready  
**Component:** Android app  
**Depends on:** T-001 (Family Wall backend), T-010 (frame status)

## Summary

Allow multiple named frames (e.g. "Salon", "Sypialnia", "Babcia"). Each frame is an independent
ImageFrameServer instance. The app lets the user pick which frame to send a photo to.

## Why

Power users may have multiple displays. Family members in different locations (grandparents)
could each have a frame the family controls remotely via VPN.

## Design decisions

- **Each frame is independent.** Each ImageFrameServer has its own Wall and slideshow. There is no
  "shared wall" across frames in v1 — keep it simple.
- **Default frame.** The first frame in the list is the default for quick-send. The user can
  reorder or mark a different one as default in Settings.

### Storage
Frame list stored in `DataStore<Preferences>`:
- `frames_json` — JSON array of `FrameEntry(id: UUID, name: String, address: String, isDefault: Boolean)`
  persisted as a single string preference (simple, no Room dependency for settings).

### Settings — Frame list screen
New screen `FrameListScreen` replaces the current single-address input in Settings:
- Shows a list of configured frames with name, address, and a "default" star icon
- FAB or top-bar "+" button opens `AddFrameDialog` (name + address fields + "Test" button)
- Swipe-to-delete removes a frame
- Tap to edit name/address

### Send to frame
When exactly one frame is configured: existing "Wyślij do ramki" button sends to it directly.
When multiple frames are configured: "Wyślij do ramki" opens a bottom sheet `FramePickerSheet`
listing all frames by name; tapping one sends and dismisses the sheet.

### Family Wall (T-002)
If T-002 is implemented: the Wall tab is scoped to the **default frame**. A frame selector chip
at the top of the Wall tab lets the user switch which frame's wall they view.

### New files
- `ui/frame/FrameRepository.kt` — DataStore read/write, `StateFlow<List<FrameEntry>>`
- `ui/frame/FrameListScreen.kt` — list of frames + add/edit/delete
- `ui/frame/FramePickerSheet.kt` — bottom sheet for multi-frame send

### Changes to existing files
- `ui/settings/SettingsScreen.kt` — replace single-address input with link to `FrameListScreen`
- `ui/image/ImageDetailsScreen.kt` — update "Send to frame" button to use `FramePickerSheet`
  when multiple frames exist
- `ui/frame/FrameApi.kt` — `FrameApi(address: String)` already parameterised by address;
  callers pass the selected frame's address
- `ui/preferences/PreferencesModule.kt` — inject `FrameRepository`

## Acceptance criteria

- [ ] User can add, edit, and delete multiple named frames in Settings
- [ ] Default frame is marked with a star; user can change the default
- [ ] With one frame: "Wyślij do ramki" sends directly (existing behavior)
- [ ] With multiple frames: "Wyślij do ramki" opens a frame picker bottom sheet
- [ ] "Test" button in add/edit dialog verifies connectivity (`GET /status` → shows latency or error)
- [ ] Frame Mode (T-003), if implemented, uses the default frame's wall
- [ ] Live status card (T-010) shows the default frame's status; a dropdown switches frame
- [ ] Removing the last frame hides the "Send to frame" button
- [ ] All existing screenshot tests pass

## Notes for implementing agent

- `FrameEntry.id` is a client-side UUID; the server has no concept of frame IDs.
- Do NOT implement remote frame management (adding a frame on the server) — the app only manages
  which addresses it connects to.
- Reorder in Settings can be deferred to v2; just show frames in creation order.
- Keep the `FramePickerSheet` lightweight: name + address per row, no thumbnails.
