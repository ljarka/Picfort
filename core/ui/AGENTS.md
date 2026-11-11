# :core:ui

Design tokens, Theme, MemberAvatar, and reusable Compose components.

## Scope
- Theme definitions: `PicfortTheme`, terracotta/slate palette, dark & light mode tokens (`com.github.ljarka.picfort.core.ui.theme`).
- Reusable UI elements: `MemberAvatar`, `initials()`, `avatarColor()` (`com.github.ljarka.picfort.core.ui.avatar`).
- UI guidelines: Never hardcode hex color literals in feature screens; consume from `MaterialTheme.colorScheme`.

## Verification
```bash
./gradlew :core:ui:build
```
