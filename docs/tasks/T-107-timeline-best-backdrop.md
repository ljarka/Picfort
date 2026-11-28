# T-107 — Timeline tinted by the month's first Best photo

**Status:** done (`6ceb820`)  
**Component:** Android (`:feature:timeline`, `:core:ui`)  
**Depends on:** T-094 (Best strip), T-106 (Wall backdrop)

## Summary

The same effect as the Family Wall (T-106) on the Photos timeline: the first photo of the "Best of
the month" strip of the month at the top of the screen, blurred, fills the background. Scrolling
into another month crossfades to its photo; a month without a strip shows the plain background.

## Design

- `PhotoBackdrop(url)` moved to `:core:ui` (`core/ui/component/PhotoBackdrop.kt`) and shared by the
  Wall and the timeline: stretched photo, `blur(40.dp)` on Android 12+, 62% theme veil, 900 ms crossfade.
- `TimelineLayout.backdropUrlAt(firstRow)`: the month marker at or above the first visible row,
  the first tile of its `BestRow` (the grid thumbnail, already loaded for the strip).
- `TimelineScreen`: `derivedStateOf` on `listState.firstVisibleItemIndex` (minus the family
  carousel); none while a person's photos are shown. `BottomSheetScaffold` and `TimelineAppBar`
  (`transparent`) let it show through only while there is a backdrop.

## Acceptance criteria

- [x] Unit test: backdrop per month, none for a month without a strip, empty layout.
- [x] Paparazzi `timeline_best_backdrop`.
- [x] All checks green.
- [ ] Owner: on the phone, scrolling through months changes the tint.
