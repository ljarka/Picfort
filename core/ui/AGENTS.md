# :core:ui

Design tokens, Theme, MemberAvatar, and reusable Compose components.

## Scope
- Theme definitions: `PicfortTheme`, navy/sky/coral palette taken from the app icon (cream light, navy-night dark; all `surfaceContainer*` roles set), light & dark tokens (`com.github.ljarka.picfort.core.ui.theme`).
- Tab accents: `BrandAccent` (aperture blades: Coral, Amber, Mint, Sky, Periwinkle), `AccentTheme(accent)` recolors primary/selection roles (no-op with wallpaper colors); `BrandAccent.forKey(id)` gives items a stable blade (shopping list cards).
- Reusable UI elements: `MemberAvatar`, `initials()`, `avatarColor(name, fallbackIndex)` (Immich color names tuned to the aperture; members with no picked color — null or Immich default "primary" — get 5 distinct blades in id order, see `mergeFamilyUsers`) (`com.github.ljarka.picfort.core.ui.avatar`).
- Floating tab bar size: `FloatingTabBar` (height, margins, `Clearance`) used by `:app`'s `TabBar`; tab roots lift their FAB with `Modifier.aboveFloatingTabBar()` (`com.github.ljarka.picfort.core.ui.component`).
- App logo: `R.drawable.icon` (flat house + aperture, same as the launcher icon); shown only on the init/login screen.
- UI guidelines: Never hardcode hex color literals in feature screens; consume from `MaterialTheme.colorScheme`.

## Verification
```bash
./gradlew :core:ui:build
```
