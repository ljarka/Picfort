# T-002 — Family Wall: Android screen

**Status:** ready  
**Component:** Android app (Kotlin, Jetpack Compose)  
**Depends on:** T-001

## Summary

Add a Family Wall tab to the Android app. Users can read posts from the family and
compose new ones (text note, optional photo from gallery or camera).

## Why

The app is the input surface for the Family Wall. Without it, only someone with direct
API access could post. The Wall tab makes posting natural and immediate.

## Design

### Navigation

Add a new bottom navigation item: **Wall** (icon: `Icons.Default.Forum` or similar).
Position: second tab, between Timeline and Albums (or last — owner decides during impl).

### WallScreen

`ui/wall/WallScreen.kt`

- `LazyColumn` of `WallPostCard` items
- Pull-to-refresh
- FAB: compose new post (bottom sheet)
- Empty state: "Brak postów. Napisz pierwszą wiadomość!"
- Error state: "Nie można połączyć z ramką. Sprawdź adres w ustawieniach."

### WallPostCard

Each card shows:
- Author initials in a colored circle (color derived from author name hash)
- Author name + relative timestamp ("2h temu", "wczoraj")
- Text (if present)
- Photo thumbnail (if present) — tappable to open full-screen viewer
- Pin icon (filled if pinned)
- Long-press or swipe: delete option (confirmation dialog)

### Compose bottom sheet

Fields:
- Author name text field (pre-filled from last used name, stored in DataStore)
- Text input (multiline, optional)
- Photo button: opens photo picker (MediaStore) or camera
- Photo preview thumbnail with remove button
- Send button (disabled if both text and photo are empty)

On send: `POST /wall` multipart. Optimistic insert while request is in flight;
roll back on error with snackbar.

### Data layer

`ui/wall/WallRepository.kt`:
- `GET /wall` — mapped to `WallPost` data class
- `POST /wall` — multipart upload
- `DELETE /wall/<id>`
- `PUT /wall/<id>/pin`
- Poll interval: 60 seconds via `repeatOnLifecycle` + `delay`; also refresh on app foreground

`WallPost` data class:
```kotlin
data class WallPost(
  val id: Int,
  val author: String,
  val text: String?,
  val photoUrl: String?,
  val pinned: Boolean,
  val created: Instant
)
```

Base URL for ImageFrameServer: reuse the existing frame address from settings
(`ui/preferences/`). If no frame address is configured, Wall tab shows a prompt
to set it up.

### Frame address

Wall uses the same host:port as the picture frame (`FRAME_HOST` preference).
No separate configuration needed.

## Acceptance criteria

- [ ] Wall tab appears in bottom navigation
- [ ] Posts load on screen open; loading indicator shown while fetching
- [ ] Pull-to-refresh fetches latest posts
- [ ] Posts refresh automatically every 60 seconds while screen is visible
- [ ] Compose bottom sheet opens on FAB tap
- [ ] Author name persisted across app restarts
- [ ] Text-only post created and appears in list
- [ ] Photo-only post created with thumbnail visible
- [ ] Text + photo post created correctly
- [ ] Tapping photo opens full-screen viewer
- [ ] Long-press on own post shows delete option; confirmation required
- [ ] Pinned posts appear at top of list
- [ ] No frame address configured → helpful empty state with link to settings
- [ ] Network error → snackbar, cached posts still visible

## Notes for implementing agent

- Photo upload: compress to max 1920px longest edge, JPEG 85%, before upload to avoid
  slow transfers on home Wi-Fi. Use `BitmapFactory` + `Bitmap.compress`.
- Author color: `hashCode() % MaterialTheme.colorScheme.X` — pick 6 distinct surface colors.
- Timestamp formatting: use `DateUtils.getRelativeTimeSpanString` for "2h temu" style.
- Do not add a new Retrofit instance; extend the existing `network/` module with a
  `WallApiService` interface that uses the frame base URL.
- Hilt module for `WallRepository` follows the same pattern as other repositories in the app.
