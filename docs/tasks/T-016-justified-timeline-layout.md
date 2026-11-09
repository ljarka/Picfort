# T-016 — Justified timeline layout from known aspect ratios

**Status:** done  
**Component:** Android app  
**Depends on:** [T-015](T-015-timeline-sync-stream.md) (every asset's size is in Room)

## Summary

Lay out the whole timeline in justified rows, like Google Photos: every row is the full
width, photos keep their own aspect ratio, and row heights vary a little. The layout is
computed ahead of time from the sizes stored in Room, instead of the current 4-column grid
with tiles of span 1, 2 or 4.

## Why

The grid was meant to look interesting, but aspect ratios used to arrive only when a month
was loaded (`/timeline/bucket` → `ratio`). So:
- months not loaded yet are drawn from an estimate (`numberOfRows`, or span 2 for every tile)
  and jump when they load,
- the layout is limited to spans of 1, 2 and 4 out of 4 columns,
- spans that changed after layout caused the crash fixed in PR #25.

After T-015, Room has `width` and `height` for every server asset, and MediaStore has them for
phone media. The whole layout can be computed before anything is drawn.

## Facts to rely on

- `AssetV2.width` / `height` are display dimensions, already swapped for EXIF orientation
  (server `metadata.service.ts`, `isSidewards`, v3.3.5). They can be `null` right after upload
  until metadata extraction runs; the asset is sent again once they are set (T-015 live check).
- MediaStore: `WIDTH`, `HEIGHT` and `ORIENTATION` for images. Swap width and height when the
  orientation is 90 or 270. Videos have no orientation column; use `WIDTH`/`HEIGHT` as stored.

## Design

### Layout algorithm (pure Kotlin, unit-tested)
`ui/timeline/layout/JustifiedLayout.kt`: `layoutMonth(ratios: List<Float>, width: Float,
targetHeight: Float, spacing: Float): List<Row>`, where `Row(items: List<Int>, height: Float)`.
- Greedy: add items to the row while `sum(ratio) * targetHeight + gaps < width`. Then scale the
  row's height so it fills the width exactly.
- Clamp ratios to `[0.5, 2.5]`, so panoramas and very tall screenshots don't make 30-px or
  500-px rows (they're cropped a little instead).
- The last row of a month is not stretched: keep `targetHeight` and leave it ragged.
- Unknown size (`null`): ratio 1.0.
- `targetHeight` depends on the screen width: about 2.5 square photos per row in portrait,
  4.5 in landscape. Pinch-to-zoom between densities can come later.

  Chosen by measurement (scroll benchmark, emulator, Immich test account). At 4 per row
  (Google Photos density) about three times as many tiles were on screen as with the old
  grid. Frame CPU P90 went 17.6 → 32.7 ms, overrun P90 2.0 → 17.8 ms, RSS +68 MB. At 2.5:
  P90 17.9 ms, overrun P90 2.5 ms, RSS +14 MB, i.e. as smooth as the old grid. A real phone
  may handle 4; not measured.

### Precomputed, not computed while scrolling
- Compute the rows of a month in the repository on `Dispatchers.Default` when its assets
  change (a Room `Flow`). Cache them per `(month, width, density)`.
- The UI gets a flat `List<TimelineRow>` (month header, or a row of tiles with their widths).
  It is immutable and part of the composed state, so nothing a tile needs changes after layout.

### UI
- `LazyColumn` of rows, keys `month` / `month_rowIndex`, `contentType` header / row. Each row
  is a `Row` of fixed-size `Box`es: no `LazyVerticalGrid` and no span lambdas.
- The fast scroller maps a month to its first row index directly.
- Keep the ThumbHash placeholder, the selection, the shared-element transition to the viewer
  and the empty state.

## Acceptance criteria

- [ ] Every row except the last row of a month fills the width exactly (unit test, ±1 px).
- [ ] Portrait, landscape and square photos keep their aspect ratio within the clamp.
- [ ] Scrolling to any month (fast scroller) shows its final layout immediately: no jump when
      tiles load.
- [ ] Rotating the device recomputes the layout. The month on screen stays on screen.
- [ ] A photo uploaded without dimensions shows as square, then takes its real shape after
      the next sync.
- [ ] Timeline benchmark: no worse than `main` in an interleaved comparison.
- [ ] Screenshot test of a month with mixed ratios (portrait, landscape, panorama, square).

## Notes for implementing agent

- Never read sizes or spans from a cache that a background thread changes after layout. The
  rows the UI draws must be the rows it was given (see the crash fix in PR #25).
- Use one ratio per asset: server assets from Room, phone-only media from MediaStore.
- Don't animate row reflow on sync; just swap the rows.
