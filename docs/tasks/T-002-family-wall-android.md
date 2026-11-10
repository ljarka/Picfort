# T-002 — Family Wall: Android screen

**Status:** ready  
**Component:** Android app (Kotlin, Jetpack Compose)  
**Depends on:** T-001 (posts with `author_id`)

## Summary

A Family Wall screen in the app. The family reads each other's posts and writes new ones
(text, a photo, or both). Posts live on ImageFrameServer (ADR 0002), so the frame and every
phone see the same wall. Family members are the Immich accounts on the server.

## Why

The app is the input surface for the Family Wall. Without it, only someone with direct
API access could post.

## Approved design

Mockup: [`docs/wireframes/family-wall-approved-v4.png`](../wireframes/family-wall-approved-v4.png)
(approved 2026-11-10). It sets the look: avatar row, message cards, photo grid. The
mockup's bottom bar and status bar are not part of the design (the app has no bottom
navigation).

```
┌─────────────────────────────┐
│ ←  Family Wall              │  TopAppBar
│ (◯ Mama)(◯ Tata)(◯ Ola) …   │  AvatarRow: fixed, outside the grid
├─────────────────────────────┤
│ [💬 Mama: "Had fun!"][💬 …→ │  MessageCarousel (FullLine item)
│ ┌───────────────────────┐   │
│ │  first photo post     │   │  FullLine hero item
│ ├───────────┬───────────┤   │
│ │  photo    │  photo    │   │  photo posts, 2 columns
│ └───────────┴───────────┘   │
│                       (+)   │  FAB: new post
└─────────────────────────────┘
```

The mockup shows two photos side by side under the cards; the hero item is what we build
(first photo post full width), since the newest family photo is the point of the screen.

## Family members

Members are the **users of the Immich server**, not face-recognition people. A member can
write, so authors and avatars are the same set.

- Source: the sync stream (T-015). Add `UsersV1` to the requested types and store the
  lines in a new Room table `users` (id, name, avatarColor, hasProfileImage,
  profileChangedAt, deletedAt). No migration: the schema change wipes the database and the
  next sync rebuilds it.
- Avatar: `GET /api/users/{id}/profile-image` through the existing authenticated Coil
  loader; key the cache on `profileChangedAt`. Without a profile image, show the initials
  on the user's `avatarColor`.
- Ring color: map Immich's `avatarColor` (`primary`, `pink`, `red`, `yellow`, `blue`,
  `green`, `purple`, `orange`, `gray`, `amber`) to fixed colors in `ui/theme`. The same
  color tints the member's cards. This does not depend on T-013.
- Order: the logged-in user first, then by name.
- **Check live before relying on it:** that `UsersV1` lists every user of the server for a
  non-admin session (not only partners), the exact field names, and whether the stream
  sends deleted users as `UserDeleteV1`.

## Screen

**Route:** `Screen.FamilyWall` in the existing `NavHost` in `MainActivity`.  
**Entry point:** an icon in the Timeline top bar, next to Albums.  
**Files:** `ui/wall/` — `FamilyWallScreen.kt`, `FamilyWallViewModel.kt`,
`WallRepository.kt`, `WallApi.kt`, components below.

### Layout

One scrolling container: `LazyVerticalStaggeredGrid(StaggeredGridCells.Fixed(2))`.
- `AvatarRow` sits **above** the grid in a `Column`, so it stays at the top without
  `stickyHeader` (the staggered grid has none).
- The carousel is the grid's first item with `StaggeredGridItemSpan.FullLine`.
- The first photo post is a `FullLine` item, the rest are regular items.
- Never put a vertical lazy list inside another vertical lazy list (it crashes at runtime).

### State

```kotlin
data class FamilyWallUiState(
    val members: List<FamilyMember>,
    val selectedMemberId: String?,   // null = everyone
    val messages: List<WallPost>,    // posts without a photo, newest first
    val photos: List<WallPost>,      // posts with a photo, newest first
    val isLoading: Boolean,
    val error: WallError?,           // NoFrameAddress, Offline
)

data class FamilyMember(
    val id: String,              // Immich user id
    val name: String,
    val avatarUrl: String?,      // null → initials
    val color: Color,            // from avatarColor
)

data class WallPost(
    val id: Int,
    val authorId: String,        // Immich user id
    val authorName: String,      // name at the time of posting (fallback for unknown ids)
    val text: String?,
    val photoUrl: String?,
    val pinned: Boolean,
    val created: Instant,
)
```

Selecting a member filters **both** the cards and the photos to posts by that member.
Posts from an `authorId` that is not (or no longer) an Immich user show `authorName` with
initials on a gray ring and are not selectable in the row.

### AvatarRow

- `LazyRow`, 56 dp circles, name below in `labelSmall` (one line, avatar width).
- Ring 2 dp, selected 3 dp plus a tinted background chip. Item padding 8 dp horizontal.
- Tap selects; tapping the selected member again clears the filter.

### MessageCarousel

- `LazyRow`, `contentPadding(start = 16.dp, end = 32.dp)`, spacing 8 dp, so the next card
  peeks.
- Card 200 × 90 dp, corner 16 dp, padding 12 dp, background `surfaceVariant` with the
  author's color at alpha 0.15.
- Card content: 32 dp avatar, name, relative time, text (2 lines max).
- Pinned posts first, then newest.
- Tap opens the full text in an `AlertDialog`; long-press on your own post offers Delete
  and Pin.
- Hidden when there are no text posts (for the current filter).

### Photo posts

- Corner 12 dp, spacing 4 dp, no borders or shadows. Hero item 220 dp high, crop.
- Regular items use the photo's aspect ratio. T-001 returns `photo_width`/`photo_height`,
  so the grid doesn't jump while images load.
- A post with text shows it as a one-line caption at the bottom of the photo.
- Tap opens the photo full screen; long-press on your own post offers Delete and Pin.

### New post (FAB → bottom sheet)

- The author is the logged-in user: send their Immich id and name. There is no author
  field.
- Text (multiline, optional), photo from the photo picker or camera, preview with remove.
- Send is disabled while both text and photo are empty.
- Compress the photo before upload: longest edge 1920 px, JPEG 85%.
- `POST /wall` multipart. Insert optimistically; on error, roll back and show a snackbar.

### Loading, refresh, errors

- Load on open; poll every 60 s while the screen is visible (`repeatOnLifecycle` +
  `delay`); pull-to-refresh.
- Keep the last fetched posts in memory for the session. Offline → snackbar, the posts
  already loaded stay visible. (No Room cache of posts in v1.)
- No frame address set → an empty state with a button to the frame settings.
- No posts → "No posts yet. Write the first one!" with the FAB visible.

## Data layer

- `WallApi` (Retrofit, base URL = the frame address from `FrameSettingsStore`, same
  pattern as `FrameDashboardApi`): `GET /wall`, `POST /wall` (multipart), `DELETE /wall/{id}`,
  `PUT /wall/{id}/pin`. Photo URLs are relative to the frame address.
- `WallRepository` joins posts with members from Room by `authorId`.
- Hilt module next to `FrameModule`.

## Acceptance criteria

- [ ] Wall icon in the Timeline top bar opens the Family Wall.
- [ ] The avatar row lists every Immich user of the server with profile picture or initials.
- [ ] Tapping a member shows only their cards and photos; tapping again shows everyone.
- [ ] Text posts appear as cards, newest first, pinned first; the next card peeks.
- [ ] Tapping a card shows the full text in a dialog.
- [ ] Photo posts appear in two columns, the newest one full width; no jump when images load.
- [ ] A text post, a photo post and a text + photo post can be created and show up
      immediately, with the logged-in user as author.
- [ ] Delete and pin are offered only on your own posts; delete asks for confirmation.
- [ ] Another phone's post appears within 60 s while the screen is open.
- [ ] No frame address → empty state linking to frame settings.
- [ ] Offline → snackbar; already loaded posts stay.
- [ ] All strings in `strings.xml`.
- [ ] Paparazzi screenshots: populated, filtered, empty, no frame address.
- [ ] CI passes (`verifyPaparazziDebug lintDebug assembleDebug`).

## Out of scope (v1)

- Push notifications for new posts.
- Offline cache of posts in Room.
- Editing posts.
- Authentication on ImageFrameServer (ADR 0004: trusted local network; `author_id` is not
  verified by the server).
