# T-008 — People screen (browse by face)

**Status:** ready  
**Component:** Android app  
**Depends on:** —

## Summary

Add a People tab inside the Collections screen (alongside Albums and Search) that lets users
browse photos by recognized person from Immich face recognition.

## Why

Immich does face recognition and already returns `people` in asset details. A dedicated People
view (like Google Photos "People & Pets") lets a user find all photos of a family member instantly.

## Design

### Placement
New tab "Osoby" in the existing Collections screen's `TabRow`, after "Albumy" and before "Szukaj".
No new bottom-nav item.

### People list screen
- `LazyVerticalGrid` (2 columns)
- Each cell: circular avatar thumbnail (first face thumbnail from `thumbnailPath`) + person's name
  below (editable name from Immich, or "Nieznana osoba" if empty)
- Tapping a cell opens `PersonAssetsScreen`

### Person assets screen
- Reuses the existing aspect-ratio `AssetGrid` composable
- Top bar: person's name, back arrow
- Assets ordered newest-first (Immich default)
- "Send all to frame as slideshow" action in the top-bar overflow menu:
  sets the frame slideshow to `mode: "album"` using the person's album ID
  (Immich auto-creates albums per person; use `GET /api/people/{id}` to get `albumId` if available;
  otherwise show a toast "Funkcja niedostępna dla tej osoby")

### New files
- `collections/people/PeopleRepository.kt` — calls `GET /api/people` and `GET /api/people/{id}/assets`
- `collections/people/PeopleViewModel.kt`
- `collections/people/PersonAssetsViewModel.kt`
- `collections/people/PeopleScreen.kt` — grid of person cells
- `collections/people/PersonAssetsScreen.kt` — asset grid for one person
- `network/dto/PersonDto.kt` — `id`, `name`, `thumbnailPath`, `assetCount`

### Changes to existing files
- `collections/CollectionsScreen.kt` — add "Osoby" tab, wire `PeopleScreen`
- `network/ImmichApi.kt` — add `suspend fun getPeople(): List<PersonDto>` and
  `suspend fun getPersonAssets(personId: String): List<AssetDto>`

### API
- `GET /api/people` → list of persons with thumbnail paths
- `GET /api/people/{id}/assets` → list of assets for that person (same schema as other asset lists)
- `PUT /api/people/{id}` → update person name (optional, for name editing)

## Acceptance criteria

- [ ] "Osoby" tab appears in Collections screen
- [ ] Person grid shows circular thumbnails and names
- [ ] Tapping a person opens their photo grid
- [ ] Photos open in full-screen viewer
- [ ] "Send to frame as slideshow" action is present in the overflow menu
- [ ] Empty state shown when Immich has no recognized people
- [ ] All existing screenshot tests pass

## Notes for implementing agent

- Person thumbnail URL: prefix the `thumbnailPath` value with the Immich base URL the same way
  other asset thumbnails are constructed.
- Do NOT implement person name editing in v1 (out of scope); just display the name.
- Add a screenshot test for `PeopleScreen` with 4 fake persons.
- Reuse the existing `AssetGrid` composable in `PersonAssetsScreen` without duplication.
