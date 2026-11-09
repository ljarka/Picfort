# T-007 — Memories screen ("On This Day")

**Status:** ready  
**Component:** Android app  
**Depends on:** —

## Summary

Add a Memories section at the top of the Timeline screen showing photos taken on today's date in
previous years. Uses Immich's `/api/memories` endpoint.

## Why

"On This Day" is one of the most emotionally resonant features in photo apps (Google Photos,
iCloud). Immich supports it natively. Surfacing it prominently adds a reason to open Picfort daily.

## Design

### Placement
A horizontal `LazyRow` of year cards inserted **above the monthly grid** in the Timeline screen,
below the top bar. It does not add a new bottom-nav tab. The section is hidden when the API
returns zero memories for today (no empty-state clutter in the main timeline).

### Year card
Each `MemoryCard` composable shows:
- Year label: "X lat temu" (computed from `assets[0].localDateTime.year`)
- Photo count chip: "12 zdjęć"
- 3-photo preview grid (first three assets, `crossfade`, `ContentScale.Crop`)

Tapping the card opens the full-screen `ImageViewer` pager at the first photo of that memory.
"Send to frame" action in the viewer works without any changes (existing action).

### New files
- `ui/memories/MemoryRepository.kt` — calls `GET /api/memories`, maps response to `Memory` data class
- `ui/memories/MemoryViewModel.kt` — `StateFlow<MemoriesUiState>`
- `ui/memories/MemoryCard.kt` — composable for one year card
- `ui/memories/MemoriesRow.kt` — `LazyRow` composable, hidden when list is empty

### Changes to existing files
- `ui/timeline/TimelineScreen.kt` — insert `MemoriesRow` above the main `LazyVerticalGrid`
- `network/ImmichApi.kt` — add `suspend fun getMemories(): List<MemoryDto>`
- `network/dto/MemoryDto.kt` — `id`, `createdAt`, `assets: List<AssetDto>`

### API
`GET /api/memories` — returns a JSON array of Memory objects. Each has `id`, `createdAt`, and
`assets` (same schema as the asset list endpoint). The number of years returned is whatever
Immich provides; there is no client-side limit.

Fallback: if the endpoint returns an empty list or a network error, the section is simply hidden.
No "This week N years ago" fallback is needed in v1 — keep it simple.

## Acceptance criteria

- [ ] Memories row appears at the top of Timeline when today has memories
- [ ] Each card shows the year label, photo count, and up to 3 photo thumbnails
- [ ] Tapping a card opens the full-screen viewer at the first photo in that memory
- [ ] "Send to frame" works from inside the memory viewer
- [ ] Row is hidden (not "empty state") when no memories exist for today
- [ ] Row is hidden when the memories API returns an error
- [ ] Existing timeline scrolls and fast-scroll are unaffected
- [ ] All existing screenshot tests pass (`./gradlew verifyPaparazziDebug`)

## Notes for implementing agent

- Use Hilt to inject `MemoryRepository` into `MemoryViewModel`.
- Reuse `AssetDto` from the existing network layer; `MemoryDto` wraps it.
- Do NOT add a new bottom-nav destination. The section lives inside the Timeline screen.
- Add a screenshot test for `MemoriesRow` with 2 fake memory cards.
- Keep the section loading independently from the timeline (separate `LazyRow` with its own state).
