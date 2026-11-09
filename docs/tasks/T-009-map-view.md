# T-009 — Map view

**Status:** ready  
**Component:** Android app  
**Depends on:** —

## Summary

Add a Map tab inside the Collections screen showing photo locations as clusters. Tapping a cluster
opens the photos taken at or near that location.

## Why

Immich stores GPS coordinates for photos. A map view is a natural way to browse travel photos and
memories by place.

## Design decisions

- **Library:** MapLibre Android SDK (`org.maplibre.gl:android-sdk`) — open-source, no API key
  required. NOT Google Maps (requires paid key and Play Services dependency).
- **Tile source:** OpenStreetMap tiles via `https://tile.openstreetmap.org/{z}/{x}/{y}.png` (free,
  no key; attribution required: "© OpenStreetMap contributors").
- **Clustering:** MapLibre's built-in GeoJSON cluster source (`cluster: true`). No custom library.
- **Placement:** New tab "Mapa" in the Collections screen `TabRow`, after "Albumy", "Osoby", "Szukaj".

### How it works

1. On open, fetch all assets that have GPS data (`exifInfo.latitude != null`).
   Use `GET /api/assets?hasGps=true` or the existing asset list filtered client-side.
2. Build a GeoJSON `FeatureCollection` from those assets (each feature = one photo pin).
3. Load into a MapLibre `GeoJsonSource` with clustering enabled.
4. Cluster pins show the count bubble. Tapping a cluster zooms in.
5. Tapping a leaf pin opens the asset in the full-screen viewer.

### New files
- `collections/map/MapScreen.kt` — Compose wrapper around `MapView` using `AndroidView`
- `collections/map/MapViewModel.kt` — fetches geolocated assets, builds GeoJSON
- `collections/map/GeoJsonBuilder.kt` — `AssetDto → Feature` conversion

### Changes to existing files
- `collections/CollectionsScreen.kt` — add "Mapa" tab, wire `MapScreen`
- `app/build.gradle.kts` — add MapLibre dependency
- `network/ImmichApi.kt` — if needed, add filter param to asset fetch

### Dependency
```kotlin
// app/build.gradle.kts
implementation("org.maplibre.gl:android-sdk:11.8.0")
```

### Attribution
Display "© OpenStreetMap contributors" per OSM tile usage policy. MapLibre shows an attribution
button by default; ensure it is not hidden.

### Assets without location
Not shown on the map. They remain fully accessible in the Timeline. No special indication needed.

## Acceptance criteria

- [ ] "Mapa" tab appears in Collections screen
- [ ] Map renders with OpenStreetMap tiles
- [ ] Photo location pins are shown for assets with GPS data
- [ ] Nearby pins cluster together; cluster shows count
- [ ] Tapping a cluster zooms in
- [ ] Tapping a single-photo pin opens that asset in the viewer
- [ ] Assets without GPS coordinates do not appear on the map
- [ ] "© OpenStreetMap contributors" attribution is visible
- [ ] All existing screenshot tests pass

## Notes for implementing agent

- `MapView` is a View, not a composable; wrap it with `AndroidView` and manage lifecycle events
  (`onStart`, `onResume`, `onPause`, `onStop`, `onDestroy`, `onLowMemory`, `onSaveInstanceState`).
- Do not load all assets in one request if the library count is large; use a bounding-box query or
  paginate and limit to 2000 most recent geolocated assets in v1.
- The screenshot test cannot render a real map (network + GL); add a simple `MapScreenTest` that
  verifies the tab appears in `CollectionsScreen` with MapLibre stubbed out.
- Add `INTERNET` permission to `AndroidManifest.xml` if not already present.
