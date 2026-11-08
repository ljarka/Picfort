# T-012 — Android home screen widget

**Status:** draft  
**Component:** Android app  
**Depends on:** —

## Summary

Add an Android home screen widget that shows a random photo from Immich,
refreshing once per day (or on tap).

## Why

Keeps family photos visible even without opening the app. A natural extension of
the "photos around me" philosophy.

## Design notes (to be refined)

- Use Jetpack Glance API (modern widget API)
- Sizes: 2×2 (single photo), 4×2 (photo + date/location)
- Refresh: WorkManager periodic task, once per day
- Tap: opens the asset in Picfort viewer
- Configuration: album source (all / specific album)

## Acceptance criteria (to be defined during implementation)

- Widget installable from home screen
- Photo updates daily
- Tap opens photo in app
