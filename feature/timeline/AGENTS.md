# feature:timeline — AGENTS.md

Immich photo timeline, gallery, full-screen viewer, collections, and media sync vertical slice.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/timeline/TimelineScreen.kt` | UI: `TimelineScreen`, `TimeLineContent`, month groupings, fast scroll; selection sheet padded above the floating tab bar |
| `ui/timeline/TimelineGridRows.kt` | Extracted timeline grid row/tile rendering helpers used by `TimelineScreen` |
| `ui/timeline/TimelineViewModel.kt` | ViewModel: layout engine, viewports, selection mode, asset actions |
| `core/ui/src/main/java/com/github/ljarka/picfort/core/ui/layout/JustifiedLayout.kt` | Shared justified row computation (`justify`) used by timeline layout |
| `ui/timeline/layout/TimelineRows.kt` | Row measurement and tile placement |
| `ui/timeline/LocalDuplicates.kt` | `localIdsOnServer`: phone media already in Immich (backup link, SHA-1, or file name + time taken ± zone offset) is hidden, shown once as the Immich copy |
| `ui/timeline/SyncTimelineSource.kt` | Local copy of Immich assets kept fresh via sync stream |
| `ui/image/ImageDetailsScreen.kt` | Full-screen photo/video viewer, shared element transition |
| `ui/image/ImageDetailsViewModel.kt` | Viewer ViewModel: zoom, EXIF metadata, pushing photo to frame via `FrameSender` |
| `collections/CollectionsScreens.kt` | Albums list, album view, search screen (text + Immich people, T-057) |
| `collections/SearchPeople.kt` | `peopleRow` (first 12 in Immich's order stay put; picks from "All people" appended, T-060), `filterPeople` (case/accents), `PeoplePickerContent` ("All people" sheet) |
| `sync/SyncEngine.kt` | Immich delta sync engine |
| `local/LocalImagesProvider.kt` | Device MediaStore photo reader |
| `ui/people/PeopleFilterViewModel.kt`, `PersonPhotosContent.kt` | Family carousel on Photos, photos of one person via `search/metadata` (T-052) |
| `ui/timeline/DayPhotosRepository.kt` | `DayPhotosProvider` (bound in `TimelineBindingsModule`): photos of given days from the local copy; registers the viewer list under `source` (T-055) |
| `ui/timeline/TimelineScreenshotTest.kt` | Paparazzi snapshots: timeline layouts, empty state, album, search, details, family carousel |

## Public Contract / Navigation Entry Points

- `TimelineScreen(onImageClick, onSearchClick, onAlbumsClick, onSettingsClick, onPersonAssetClick, onEditMember, onOpenFamily, scrollToDate)`; `onOpenFamily` = the "+ Family" circle
  at the end of the carousel; `onEditMember`/`onOpenFamily` null without a Picfort server (no way into Family settings); `scrollToDate` (T-056) scrolls once to `TimelineLayout.rowIndexOf(day)`
  (newest photo of the day, or its month header; flag kept in `rememberSaveable`)
- `ImageDetailsScreen(sharedTransitionScope, animatedVisibilityScope, onDismissRequest, assetId, assetType, imageIndex, frameSettingsDialog)`
- `AlbumsScreen(onAlbumClick, onBack)`
- `AlbumScreen(onBack, onAssetClick)`
- `SearchScreen(onBack, onAssetClick)`

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
DEVICE=emulator-5556 ./scripts/maestro-smoke.sh maestro/search-people.yaml       # T-057, tags test people
```
