# T-009 — Map view

**Status:** draft  
**Component:** Android app  
**Depends on:** —

## Summary

Add a map view that shows photo locations as clusters. Tapping a cluster opens
the photos taken at or near that location.

## Why

Immich stores GPS coordinates for photos. A map view is a natural way to browse
travel photos and memories by place.

## Design notes (to be refined)

- Library: MapLibre (open-source, no API key required) or Google Maps SDK
- Cluster pins: tap to zoom in; tap leaf cluster to open asset grid
- Photo location data: already in Immich asset detail response (`exifInfo.latitude/longitude`)
- Filter to only assets with location data

## Open questions for implementing agent

- MapLibre vs Google Maps — MapLibre preferred (no API key, offline tiles possible)
- Tile source: OpenStreetMap tiles (free) or Mapbox (requires key)?
- Should the map be a tab or accessible from asset details only?

## Acceptance criteria (to be defined during implementation)

- Map renders with photo location pins
- Clusters group nearby photos
- Tapping cluster → photo grid
- Photos without location not shown on map (but accessible in timeline)
