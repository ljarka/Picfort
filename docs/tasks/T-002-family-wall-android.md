# T-002 — Family Wall: Android screen

**Status:** done (all criteria checked on the emulator 2026-11-11)  
**Component:** Android app (Kotlin, Jetpack Compose)  
**Depends on:** T-001 (posts with `author_id`, Immich-token auth per ADR 0006)

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

Later changes to this screen, not in the mockup: T-017 makes the wall a tab (bottom
navigation, no back arrow, ⚙ in the top bar); T-027 adds a "today" panel above the message
carousel and needs its own mockup (`wall-today-panel-v1.png`) before coding.

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
write, so authors and avatars are the same set. Test accounts (a name containing "test",
case-insensitive) are not members: they and their posts are hidden (PR #37); the logged-in
user is never hidden.

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
- Checked live (2026-11-10, Immich v3.3.5): `UsersV1` lists every user of the server for a
  non-admin session (3 users, none of them partners). Not seen yet: `UserDeleteV1`, and a
  user with a profile picture or an avatar color.

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
- Card 200 × 104 dp (90 dp fits only one line of text), corner 16 dp, padding 12 dp, background `surfaceVariant` with the
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

- The author is the logged-in user; the server takes it from the token. There is no author
  field, and the request sends none.
- Text (multiline, optional), photo from the photo picker or camera, preview with remove.
- Send is disabled while both text and photo are empty.
- Compress the photo before upload: longest edge 1920 px, JPEG 85%.
- `POST /wall` multipart. Insert optimistically; on error, roll back and show a snackbar.

### Loading, refresh, errors

- Load on open; poll every 60 s while the screen is visible (`repeatOnLifecycle` +
  `delay`); pull-to-refresh.
- Keep the last fetched posts in memory for the session. Offline → snackbar, the posts
  already loaded stay visible. (No Room cache of posts in v1.)
- No server address set → an empty state ("Connect your Picfort server to use the Family
  Wall") with a button that opens the server dialog **directly** (see below). After Save,
  the wall loads without leaving the screen.
- No posts → "No posts yet. Write the first one!" with the FAB visible.

## Picfort server address

The frame and the Family Wall use the same ImageFrameServer, so the app has one address for
both (`FrameSettingsStore.frameUrl`, unchanged). It's entered the same way as the Immich
address: `192.168.0.105:5000` becomes `http://192.168.0.105:5000`.

### Naming

Call it the **Picfort server** in the UI, since it is no longer only the frame:
- Dialog title "Picfort server", label "Server address", text "The Picfort server on your
  network runs the picture frame and the Family Wall."
- Settings: rename the section "Picture frame" to "Picfort server". The row shows the address
  (or "Not set") and opens the frame screen as today.
- Code names (`FrameSettingsStore`, `FrameSettingsDialog`) may stay; rename only if it's
  cheap.

### Connection test

Extend `FrameRepository.testConnection` (button **Test**) with a second request after
`/status` succeeds: `GET /wall?limit=1` with the Immich token. Show one message:

| `/status` | `/wall` | Message |
|---|---|---|
| fails | — | as today ("Not a Picfort server (HTTP …)", connection errors) |
| ok | 200 | "Connected: frame and Family Wall" |
| ok | 401 | "Connected, but the server didn't accept your Immich login. Check that it uses the same Immich server (IMMICH_URL)." |
| ok | 404 | "Connected. This server has no Family Wall yet, update ImageFrameServer." |
| ok | 502 | "Connected, but the server can't reach Immich." |

**Save** stays enabled for every result that has a valid address, as today. A partial result
is still useful (the frame works).

### One form, three places

Extract the address field, the test message and the Test button into
`PicfortServerForm` (state in `FrameSettingsViewModel`). Use it in:
1. `FrameSettingsDialog` (Settings, ⚙ in the photo viewer): as today.
2. The wall's empty state: opens `FrameSettingsDialog`; `onSaved` reloads the wall.
3. The first-run step below.

### First-run step

Before the timeline, show a full-screen step **once** when no server address is set: after
the first Immich login, or on the first start after this update for someone already logged
in.

```
┌─────────────────────────────┐
│  Picfort server (optional)  │
│                             │
│  Do you have a picture frame│
│  or the Family Wall at home?│
│  Enter the server address.  │
│                             │
│  [ 192.168.0.105:5000     ] │
│  Connected: frame and …     │
│                             │
│  [Test]    [Skip] [Continue]│
└─────────────────────────────┘
```

- `Screen.PicfortServerSetup` in the `NavHost`. `InitScreen` navigates there instead of to
  the timeline when the step is due, then on to the timeline (popping the step).
- Due when: logged in, `frameUrl` empty, and the `PICFORT_SERVER_PROMPTED` flag (encrypted
  preferences, next to `FRAME_URL`) is not set. Skip or Continue sets the flag, so it shows
  once per install. Later changes go through Settings.
- **Continue** saves the address (enabled only for a valid address). **Skip** saves nothing.
- Also add a short line to the README "Using it from the app" section.

## Data layer

- Every `/wall` call (including photos loaded by Coil) sends `Authorization: Bearer <token>`
  with the Immich access token from `AccessTokenStore`, added by an OkHttp interceptor on the
  wall client only. Frame-control calls stay without it (ADR 0006).
- `401` from the wall → a snackbar "Couldn't verify your Immich login with the frame". Don't
  log out (the frame may use another Immich server). `502` → "The frame can't reach Immich".
  `403` on delete/pin → roll back and show a snackbar.

- `WallApi` (Retrofit, base URL = the frame address from `FrameSettingsStore`, same
  pattern as `FrameDashboardApi`): `GET /wall`, `POST /wall` (multipart), `DELETE /wall/{id}`,
  `PUT /wall/{id}/pin`. Photo URLs are relative to the frame address.
- `WallRepository` joins posts with members from Room by `authorId`.
- Hilt module next to `FrameModule`.

## Acceptance criteria

- [x] Wall icon in the Timeline top bar opens the Family Wall.
- [x] The avatar row lists every Immich user of the server with profile picture or initials.
- [x] Tapping a member shows only their cards and photos; tapping again shows everyone.
- [x] Text posts appear as cards, newest first, pinned first; the next card peeks.
- [x] Tapping a card shows the full text in a dialog.
- [x] Photo posts appear in two columns, the newest one full width; no jump when images load.
- [x] A text post, a photo post and a text + photo post can be created and show up
      immediately, with the logged-in user as author.
- [x] Delete and pin are offered only on your own posts; delete asks for confirmation.
- [x] Another phone's post appears within 60 s while the screen is open.
- [x] No server address → empty state whose button opens the server dialog; after Save the
      wall loads.
- [x] The UI calls it "Picfort server" (dialog, Settings section).
- [x] Test reports each case in the table above (check 401 with a server on another Immich,
      404 with an ImageFrameServer without the wall).
- [x] First login with no server address shows the setup step once; Skip and Continue both
      go to the timeline, and the step doesn't come back on the next start.
- [x] Logged-in users who already have an address never see the step.
- [x] Offline → snackbar; already loaded posts stay.
- [x] Strings follow the app's convention: inline in Compose (the app has no `strings.xml`
      strings yet; moving them all is a separate task).
- [x] Paparazzi screenshots: populated, filtered, empty, no server address, server dialog
      with each test message, first-run step.
- [x] CI passes (`verifyPaparazziDebug lintDebug assembleDebug`).

## Out of scope (v1)

- Push notifications for new posts.
- Offline cache of posts in Room.
- Editing posts.
- Authentication for the frame-control endpoints (ADR 0004).
