# feature:timeline — AGENTS.md

Immich photo timeline, gallery, full-screen viewer, collections, and media sync vertical slice.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/timeline/TimelineScreen.kt` | UI: `TimelineScreen`, `TimeLineContent`, month groupings, fast scroll; selection sheet padded above the floating tab bar |
| `ui/timeline/TimelineSettingsScreen.kt` | Settings -> Timeline: "Hide screenshots & documents" and "Show best of the month" (T-094) switches + Immich tag list & delete (`DELETE /api/tags/{id}`, T-092) |
| `ui/timeline/TimelineGridRows.kt` | Timeline grid row/tile rendering; `BestOfMonthStrip` for `BestRow` (T-094) |
| `ui/timeline/TimelineViewModel.kt` | ViewModel: layout engine, viewports, selection mode, asset actions |
| `core/ui/src/main/java/com/github/ljarka/picfort/core/ui/layout/JustifiedLayout.kt` | Shared justified row computation (`justify`) used by timeline layout |
| `ui/timeline/layout/TimelineRows.kt` | Row measurement and tile placement; `BestRow` after a month header when `TimelineMonth.best` (3..12, `bestOfMonth`) is set (T-094) |
| `ui/timeline/TimelineRepository.kt` | Merged index; `phoneOnServer` + `loadPhoneMediaIfNeeded()` for the backup count in Settings (T-063) |
| `ui/timeline/LocalDuplicates.kt` | `localIdsOnServer`: phone media already in Immich (backup link, SHA-1, or file name + time taken ± zone offset) is hidden, shown once as the Immich copy |
| `ui/timeline/SyncTimelineSource.kt` | Local copy of Immich assets + EXIF (`AssetExifV1`) + daily OCR document ids (T-088) and `ai/best/frame-worthy` ids (T-094); photo AI labels from `/photo-insights/labels` into `ai_labels` (`aiLabels()`, T-098/T-099) |
| `ui/image/ImageDetailsScreen.kt` | Full-screen photo/video viewer, shared element transition |
| `ui/image/AssetDetailsSheet.kt`, `assets/AssetDetails.kt` | "Details" sheet: tag chips on top (`assetTagLabels`, T-097), then date, file, camera, place, people |
| `ui/image/ImageDetailsViewModel.kt` | Viewer ViewModel: zoom, EXIF metadata, pushing photo to frame via `FrameSender` |
| `collections/CollectionsScreens.kt` | Albums list (with Suggested albums row, T-098), album view, search screen (text + Immich people, T-057; All/Photos/Videos `MediaFilter`, T-071; Immich tag chips, T-090) |
| `collections/CollectionsRepository.kt` | Album contents via `timeline/bucket?albumId`; durations text or ms (`parseSearchDuration`, T-061); `deleteAlbum` (T-070) |
| `collections/CollectionsService.kt` | Immich albums/search API; `AlbumDto.ownerId` (albumUsers role "owner"), `canDelete(myId)`: ⋮ → "Delete album" for the owner only (photos stay) |
| `collections/SearchPeople.kt` | `peopleRow` (first 12 in Immich's order stay put; picks from "All people" appended and scrolled to, T-060), `filterPeople` (case/accents), `PeoplePickerContent` ("All people" sheet) |
| `sync/SyncEngine.kt` | Immich delta sync engine |
| `local/LocalImagesProvider.kt` | Device MediaStore photo reader |
| `local/MediaPermission.kt` | Media permissions + `ACCESS_MEDIA_LOCATION` (`hasOriginalAccess`, backup reads originals, T-067) |
| `ui/people/PeopleFilterViewModel.kt`, `PersonPhotosContent.kt` | Family carousel on Photos, photos of one person via `search/metadata` (T-052) |
| `ui/timeline/DayPhotosRepository.kt` | `DayPhotosProvider` (bound in `TimelineBindingsModule`): photos of given days from the local copy; registers the viewer list under `source` (T-055) |
| `memories/MemoriesModel.kt`, `MemoriesViewModel.kt`, `MemoriesScreen.kt` | Immich memories (`GET /api/memories`): sections Today/Saved/Earlier (`memorySections`), cover + thumbnails, viewer list `memory:<id>` (T-096) |
| `cleanup/PhotoInsightsClient.kt`, `CleanupViewModel.kt`, `CleanupScreen.kt` | AI photo cleanup screen & `"Clean up (N)"` chip on Timeline backed by `/photo-insights/*` (ADR 0019, T-089) |
| `contract/ImmichV3ContractTest.kt` | Real Immich 3.3 responses (`test/resources/immich-v3/`) read with the app's DTOs; refresh on a new major (T-062) |
| `ui/timeline/TimelineScreenshotTest.kt` | Paparazzi snapshots: timeline layouts, empty state, album, search, details, family carousel |

## Public Contract / Navigation Entry Points

- `TimelineScreen(onImageClick, onSearchClick, onAlbumsClick, onSettingsClick, onPersonAssetClick, onEditMember, onOpenFamily, scrollToDate)`; `onOpenFamily` = the "+ Family" circle
  at the end of the carousel; `onEditMember`/`onOpenFamily` null without a Picfort server (no way into Family settings); `scrollToDate` (T-056) scrolls once to `TimelineLayout.rowIndexOf(day)`
  (newest photo of the day, or its month header; flag kept in `rememberSaveable`)
- `ImageDetailsScreen(sharedTransitionScope, animatedVisibilityScope, onDismissRequest, assetId, assetType, imageIndex, frameSettingsDialog)`
- `AlbumsScreen(onAlbumClick, onBack)`
- `AlbumsScreen` gives `onAlbumClick(id, name, canDelete)`; `AlbumRoute(id, name, canDelete)`
- `AlbumScreen(onBack, onAssetClick)`: closes itself after a delete; the albums list drops it (`deletedAlbums`)
- `SearchScreen(onBack, onAssetClick)`
- `MemoriesScreen(onBack, onAssetClick)` (T-096), opened from the Wall via `:app`

## Dependencies & Boundaries

- Depends on `:core:model` (`FrameSender`, `FrameResult`, `localImageUri`, `FamilyMemberProvider` with `myPersonIds`).
- Depends on `:core:ui` (`PicfortTheme`, `MemberCarousel`, `aboveFloatingTabBar`, `justify`).
- Depends on `:core:database` (`ImagesDatabase`, `RemoteAssetEntity`, `BackupDao`).
- Depends on `:core:network` (`AccessTokenStore`, `ServerUrlStore`).
- **NEVER** depends directly on other feature modules (`:feature:frame`, `:feature:wall`, `:feature:shopping`).
- Frame actions use `FrameSender` interface (bound in `:app` from `:feature:frame`).

## Verification

```bash
./gradlew :feature:timeline:testDebugUnitTest
./gradlew :feature:timeline:verifyPaparazziDebug
./gradlew :feature:timeline:recordPaparazziDebug # on intentional UI changes
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/timeline-selection.yaml  # sheet above the tab bar
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/search-people.yaml       # T-057, tags test people; T-071 Photos/Videos
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/album-delete.yaml        # T-070, test account album
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/best-and-tags.yaml       # T-094/T-097/T-100: Best strip, Details tags, tag search
```
