# T-013 — Material You / dynamic color

**Status:** draft  
**Component:** Android app  
**Depends on:** —

## Summary

Apply Android 12+ dynamic color (Material You) so the app's color scheme adapts
to the user's wallpaper.

## Why

Standard Android 12+ feature. Immich's official app supports it. Makes Picfort
feel polished and native on modern Android.

## Design notes (to be refined)

- `dynamicLightColorScheme` / `dynamicDarkColorScheme` in `MaterialTheme` setup
- Fallback to current static color scheme on Android < 12
- Review all custom colors to ensure they derive from the scheme (not hardcoded)

## Acceptance criteria (to be defined during implementation)

- App color scheme matches system wallpaper colors on Android 12+
- Static fallback colors used on Android < 12
- No legibility issues in dark mode
