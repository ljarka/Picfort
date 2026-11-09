# T-010 — Bidirectional frame status

**Status:** ready  
**Component:** Android app  
**Depends on:** T-001 (ImageFrameServer backend)

## Summary

Show live frame status in the app — what photo the frame is currently displaying, the slideshow
mode, and when it will change next.

## Why

Users want to know what their family sees on the display without walking to the room.
The `GET /status` endpoint already returns `currentAsset` — the app just doesn't surface it.

## Design

### Placement
New "Live Preview" card at the top of the **Frame Settings** screen (accessible from the bottom-nav
Settings tab → "Ramka"). The card is shown only when a frame address is configured.

### Card layout
- Photo thumbnail on the left (square, 80 dp), loaded with the same `AsyncImage` / Coil setup
- Right column:
  - Row 1: "Na ramce teraz" label
  - Row 2: `currentAsset.originalFileName` (truncated to 1 line)
  - Row 3: Slideshow mode chip (`off` / `album` / `memories` / `random`)
  - Row 4: "Następna zmiana za X min" (from `slideshow.next_change_at`)
- Bottom: "Wyślij inne zdjęcie" button → navigates to asset picker

### Polling
`FrameStatusViewModel` polls `GET /status` every **60 seconds** while the Settings screen is
visible (Kotlin coroutine with `repeatOnLifecycle(STARTED)` and `delay(60_000)`). The user can also
pull-to-refresh on the Settings screen to trigger an immediate poll.

### New files
- `ui/frame/FrameStatusViewModel.kt` — polling loop, `StateFlow<FrameStatusUiState>`
- `ui/frame/FrameStatusCard.kt` — composable card

### Changes to existing files
- `ui/frame/FrameApi.kt` (or wherever the existing frame HTTP client lives) — add
  `suspend fun getStatus(): FrameStatusDto`
- `ui/settings/SettingsScreen.kt` — insert `FrameStatusCard` above the frame address input
- `network/dto/FrameStatusDto.kt` — maps the JSON from `GET /status`

### DTO
```kotlin
data class FrameStatusDto(
    val status: String,
    val busy: Boolean,
    val currentAsset: FrameAssetDto?,
    val slideshow: SlideshowStatusDto?
)
data class FrameAssetDto(val id: String, val originalFileName: String, val thumbUrl: String)
data class SlideshowStatusDto(val mode: String, val nextChangeAt: String?)
```

## Acceptance criteria

- [ ] "Live Preview" card is shown in Settings when a frame address is configured
- [ ] Card shows the current photo thumbnail (tapping opens that asset in the viewer)
- [ ] Slideshow mode chip shows the correct mode
- [ ] "Next change" time is displayed; hidden when slideshow is `off`
- [ ] Card re-polls every 60 seconds while visible
- [ ] Pull-to-refresh on Settings triggers an immediate status poll
- [ ] Card is hidden (not shown at all) when no frame address is set
- [ ] Network errors show a "Nie można połączyć z ramką" inline message; card stays visible
- [ ] All existing screenshot tests pass

## Notes for implementing agent

- The thumbnail URL for `currentAsset` is the same frame API: `GET /current` returns a PNG of the
  last rendered frame. Use that as the thumbnail (`http://<frame-address>/current`).
- Keep frame HTTP calls on a separate `OkHttpClient` instance without Immich auth headers
  (the frame has no auth — ADR 0004).
- Add a screenshot test for `FrameStatusCard` with a fake status DTO.
