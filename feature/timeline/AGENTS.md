# feature:timeline — AGENTS.md

Immich photo timeline, gallery, full-screen viewer, collections, and media sync vertical slice.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/timeline/TimelineScreen.kt` | UI: `TimelineScreen`, `TimeLineContent`, month groupings, fast scroll |
| `ui/timeline/TimelineViewModel.kt` | ViewModel: layout engine, viewports, selection mode, asset actions |
| `ui/timeline/layout/JustifiedLayout.kt` | Justified photo grid layout computation |
| `ui/timeline/layout/TimelineRows.kt` | Row measurement and tile placement |
| `ui/timeline/SyncTimelineSource.kt` | Local copy of Immich assets kept fresh via sync stream |
| `ui/image/ImageDetailsScreen.kt` | Full-screen photo/video viewer, shared element transition |
| `ui/image/ImageDetailsViewModel.kt` | Viewer ViewModel: zoom, EXIF metadata, pushing photo to frame via `FrameSender` |
| `collections/CollectionsScreens.kt` | Albums list, album view, search screen |
| `sync/SyncEngine.kt` | Immich delta sync engine |
| `local/LocalImagesProvider.kt` | Device MediaStore photo reader |
| `ui/people/PeopleFilterViewModel.kt`, `PersonPhotosContent.kt` | Family carousel on Photos, photos of one person via `search/metadata` (T-052) |
| `ui/timeline/TimelineScreenshotTest.kt` | Paparazzi snapshots: timeline layouts, empty state, album, search, details, family carousel |

## Public Contract / Navigation Entry Points

- `TimelineScreen(onImageClick, onSearchClick, onAlbumsClick, onSettingsClick, onPersonAssetClick, onEditMember)`
- `ImageDetailsScreen(sharedTransitionScope, animatedVisibilityScope, onDismissRequest, assetId, assetType, imageIndex, frameSettingsDialog)`
- `AlbumsScreen(onAlbumClick, onBack)`
- `AlbumScreen(onBack, onAssetClick)`
- `SearchScreen(onBack, onAssetClick)`

## Dependencies & Boundaries

- Depends on `:core:model` (`FrameSender`, `FrameResult`, `localImageUri`, `FamilyMemberProvider` with `myPersonIds`).
- Depends on `:core:ui` (`PicfortTheme`, `MemberCarousel`, `aboveFloatingTabBar`).
- Depends on `:core:database` (`ImagesDatabase`, `RemoteAssetEntity`, `BackupDao`).
- Depends on `:core:network` (`AccessTokenStore`, `ServerUrlStore`).
- **NEVER** depends directly on other feature modules (`:feature:frame`, `:feature:wall`, `:feature:shopping`).
- Frame actions use `FrameSender` interface (bound in `:app` from `:feature:frame`).

## Verification

```bash
./gradlew :feature:timeline:testDebugUnitTest
./gradlew :feature:timeline:verifyPaparazziDebug
./gradlew :feature:timeline:recordPaparazziDebug # on intentional UI changes
```
