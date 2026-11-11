# T-038 — Brand identity, app icon & visual redesign

**Status:** done (v0.5.0)  
**Component:** Android app  
**Depends on:** T-013, T-017

## Summary

Complete brand refresh for Picfort ("The Lens & Hearth", Concept 2): modern adaptive app icon,
warm terracotta & slate palette, M3 Expressive floating capsule navigation bar with frosted
glassmorphism feel, and updated Paparazzi goldens.

## Design

- **App Icon:** Modern camera aperture framing a cozy hearth/house in warm sunset terracotta and
  deep slate petrol tones. Master 1024×1024 PNG in `res/drawable/icon.png`, adaptive icons across
  all mipmap densities, and monochrome vector for Android 13+ Themed Icons.
- **Color Theme:** Dedicated warm palette (`TerracottaLight`/`Dark`, `SlateLight`/`Dark`,
  `AmberLight`/`Dark`, warm cream background `#FCF9F6`, deep warm dark background `#131315`), with
  Material You Dynamic Color support preserved on Android 12+.
- **Floating Navigation Bar:** Detached capsule hovering with 16 dp margins, 32 dp rounded corners,
  elevated tonal shadow, and smooth animated active selection indicator (`animateColorAsState`).
- **Wireframes:** `docs/wireframes/picfort-app-icon-concepts.png`, `picfort-screens-redesign-v1.png`,
  `picfort-floating-nav-spec.png`.

## Acceptance criteria

- [x] High-resolution adaptive icon across all densities and themed icon monochrome vector.
- [x] Warm brand palette implemented in `Color.kt` and `Theme.kt`.
- [x] Floating navigation bar implemented in `AppShell.kt`.
- [x] Unit tests and Paparazzi screenshot tests re-recorded and passing.
