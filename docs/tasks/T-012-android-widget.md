# T-012 — Home screen widget (Glance API)

**Status:** ready  
**Component:** Android app  
**Depends on:** —

## Summary

Add an Android home screen widget that shows a random photo from Immich, refreshing once per day.
Built with Jetpack Glance.

## Why

Keeps family photos visible even without opening the app. A natural extension of the
"photos around you" philosophy.

## Design

### Widget sizes and layouts

**2×2 — Single photo**
- Full-bleed photo (no padding)
- Tap opens the asset in Picfort viewer

**4×2 — Photo + info**
- Left 2/3: full-bleed photo
- Right 1/3: dark scrim overlay with:
  - Date taken (day + month)
  - Location name from `exifInfo.city` (if available)
- Tap opens the asset in Picfort viewer

Both sizes use a single `AppWidgetProvider` class; the `GlanceAppWidget` renders the layout
based on the available size from `LocalSize.current`.

### Photo selection
On each refresh: pick a random asset from the user's Immich library using
`GET /api/assets/random?count=1`. Download and cache the thumbnail with `Coil`
(write to `Context.cacheDir/widget_photo.jpg`). Store the `assetId` in `GlanceStateDefinition`
so the tap action can open the correct asset.

### Refresh schedule
`WorkManager` periodic task (`WidgetRefreshWorker`) with `PeriodicWorkRequest` of **24 hours**.
Enqueued on first widget add (in `AppWidgetProvider.onEnabled`), cancelled on last remove
(`onDisabled`).

### Configuration
No configuration activity in v1. Future: album source picker (all / specific album) in v2.

### New files
- `ui/widget/PicfortWidget.kt` — `GlanceAppWidget` with 2×2 and 4×2 layouts
- `ui/widget/PicfortWidgetReceiver.kt` — `GlanceAppWidgetReceiver` + `AppWidgetProvider` lifecycle
- `ui/widget/WidgetRefreshWorker.kt` — downloads random photo and triggers widget update
- `res/xml/picfort_widget_info.xml` — widget metadata (minWidth/minHeight, updatePeriodMillis = 0,
  previewImage, description)

### Changes to existing files
- `AndroidManifest.xml` — declare `PicfortWidgetReceiver` with `APPWIDGET_UPDATE` intent filter
- `app/build.gradle.kts` — add Glance dependency:
  ```kotlin
  implementation("androidx.glance:glance-appwidget:1.1.1")
  ```

## Acceptance criteria

- [ ] Widget appears in the Android widget picker under "Picfort"
- [ ] 2×2 widget shows a full-bleed photo
- [ ] 4×2 widget shows the photo with date and city overlay
- [ ] Tapping either widget size opens the correct asset in the Picfort viewer
- [ ] Photo changes after ~24 hours (WorkManager periodic refresh)
- [ ] Widget handles "no credentials configured" gracefully (shows Picfort logo placeholder)
- [ ] Widget handles network error gracefully (keeps previous photo or shows placeholder)
- [ ] All existing app screenshot tests pass

## Notes for implementing agent

- Use `androidx.glance:glance-appwidget:1.1.1` (stable). Do not use alpha versions.
- `GET /api/assets/random?count=1` is the Immich endpoint for a random asset. If unavailable,
  fall back to `GET /api/timeline/buckets` + pick first bucket + `GET /api/timeline/bucket`.
- Glance does not support `Coil` directly; download the image to a file in `cacheDir` and load
  it with `GlanceImage` via `ImageProvider.fromFile(file)`.
- The tap action should use `actionStartActivity(Intent(context, MainActivity::class.java).also
  { it.putExtra("assetId", assetId) })`.
- Widget screenshot tests with Paparazzi are not supported for Glance; skip the screenshot test
  for the widget composable. Verify by installing on a device or emulator.
