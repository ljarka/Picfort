# T-096 — Memories screen (Immich memories) opened from the frame card on the Family Wall

**Status:** done (`6edba0f`)  
**Component:** Android (`:feature:timeline`, `:feature:wall`, `:app`)  
**Depends on:** T-091 (frame card on the Wall)

## Summary

The frame card on the Family Wall gets a **"Memories"** button in its top-right corner. It opens
a full Memories screen showing Immich's memories (`GET /api/memories`, Immich 3): "On this day"
and birthdays, each as a large cover photo with "N years ago", the date and the photo count, and
a row of its other photos. Tapping a photo opens the normal full-screen viewer, paging through
that memory.

## Design

- **Data:** `CollectionsService.memories(isUpcoming = false, isTrashed = false)` with the user's
  Immich session (`MemoryDto`, only the fields used; `ignoreUnknownKeys`).
- **Sections** (`memorySections`, pure): *Today* (memories for today, most recent year first),
  *Saved* (Immich's saved memories from other days), *Earlier* (the rest, newest day first).
  Memories without photos are left out. Titles: "1 year ago", "N years ago", "Ola's birthday".
  Dates formatted with the locale read in the ViewModel (no `Locale.getDefault()` in composables).
- **Viewer:** each memory's photos are registered as `memory:<id>` with
  `CollectionsRepository.putList`; `ImageDetailsRoute(…, source)` pages through them.
- **Screen** (`memories/MemoriesScreen.kt`): `LargeTopAppBar` "Memories" with refresh; 4:3 cover
  with gradient, title and subtitle, a heart badge for saved memories; 92 dp thumbnails (play icon
  on videos). Empty state explains that Immich builds memories every night.
- **Wall:** `FramePhotoCard(onMemories)` draws a translucent "Memories" pill top-right;
  `FamilyWallScreen(onOpenMemories)`. `:app` navigates to `Screen.Memories` (top level, like
  `Screen.Frame`), so the Wall stays the selected tab. No feature-to-feature dependency.

## Acceptance criteria

- [x] Unit tests: section order and titles, birthdays, videos, empty memories, Immich 3 JSON.
- [x] Paparazzi: `memories`, `memories_empty` (timeline), `wall_frame_photo` (wall).
- [x] `testDebugUnitTest`, `verifyPaparazziDebug`, `ktlintCheck`, `lintDebug`, boundaries green.
- [ ] Owner: on the phone, the button opens today's memories; photos open in the viewer.

## Notes

The button sits on the frame card, so it shows only while the frame shows a photo (T-091).
