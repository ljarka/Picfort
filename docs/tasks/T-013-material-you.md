# T-013 — Material You / dynamic color

**Status:** ready  
**Component:** Android app  
**Depends on:** —

## Summary

Apply Android 12+ dynamic color (Material You) so the app's color scheme adapts to the user's
wallpaper. Static fallback for Android < 12.

## Why

Standard Android 12+ feature. Immich's official app supports it. Makes Picfort feel polished
and native on modern Android.

## Design

### Theme setup
In `ui/theme/Theme.kt` (or wherever `MaterialTheme` is called):

```kotlin
@Composable
fun PicfortTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme      // existing static scheme
        else      -> LightColorScheme     // existing static scheme
    }
    MaterialTheme(colorScheme = colorScheme, ..., content = content)
}
```

### Color audit
Review every hardcoded color in the codebase. Replace with Material color role tokens:
- `Color.Black` / `Color.White` → `MaterialTheme.colorScheme.onSurface` / `.surface`
- Hardcoded brand blue → `MaterialTheme.colorScheme.primary`
- Hardcoded background grays → `MaterialTheme.colorScheme.surfaceVariant` / `.background`

The only hardcoded colors allowed after this task:
- Photo overlay scrims (semi-transparent black, `Color(0x80000000)`)
- Status bar / nav bar system UI colors

### Edge-to-edge + tonal elevation
Material You uses tonal elevation (surfaces tinted with primary color) instead of shadows.
Ensure elevated surfaces (bottom sheet, top bar when scrolled) use `Surface(tonalElevation = X.dp)`
and not custom shadow modifiers.

### Screenshot tests
Dynamic color produces different output per device; Paparazzi runs in a fixed environment.
In screenshot tests, disable dynamic color by passing a fake SDK version (`Build.VERSION_INT = 30`)
or by injecting the static `LightColorScheme`/`DarkColorScheme` directly into the test composable.

## Acceptance criteria

- [ ] On Android 12+ (API 31+), app colors adapt to system wallpaper in both light and dark mode
- [ ] On Android < 12, static color scheme is used (existing behavior, no regression)
- [ ] No hardcoded colors outside the allowed exceptions listed above
- [ ] Dark mode looks correct with dynamic color (legibility checks: contrast ratio ≥ 4.5:1 for text)
- [ ] All existing screenshot tests pass (static scheme in tests, unaffected by dynamic color)
- [ ] No visual regressions in the full-screen viewer overlay and bottom bar

## Notes for implementing agent

- `dynamicLightColorScheme` / `dynamicDarkColorScheme` require `androidx.compose.material3:material3`
  which should already be in the project.
- Run `./gradlew lintDebug` after the color audit; Lint flags hardcoded colors in XML drawables too.
- If `minSdk` is below 31, the `Build.VERSION.SDK_INT >= Build.VERSION_CODES.S` guard is mandatory
  (it already is — `minSdk 26` per README).
- Test manually on a Pixel emulator with Android 12+ by changing the wallpaper and verifying the
  app color scheme updates on restart.
