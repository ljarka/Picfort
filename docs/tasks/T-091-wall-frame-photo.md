# T-091 — Family Wall: show current frame photo (`/frame/photo`)

**Status:** done (`a9e8b97`)  
**Component:** Android (`:feature:wall`, `:app`)  
**Depends on:** T-072

## Summary

Display the same full-colour photo currently shown on `/frame` as a full-width card on the Family Wall (right under the Today panel when no member filter is active). Tapping the card opens the Frame screen.

## Design

- `WallApi.framePhotoUrl()` polls `GET /frame/state` via `PicfortServerClient`; when `shown == true`, builds `/frame/photo?w=1080&h=720&v=<version>` so Coil automatically refreshes whenever the frame switches photos.
- `FamilyWallViewModel.refresh()` loads `framePhotoUrl` asynchronously alongside `/wall` and `/events`, never blocking or failing the wall if the browser frame is disabled or empty.
- `FamilyWallScreen` renders `FramePhotoCard` with an `"On the frame"` caption; `:app` wires `onOpenFrame = { navController.navigate(Screen.Frame) }`.
- Respects strict feature isolation (`:feature:wall` does not depend on `:feature:frame`).
