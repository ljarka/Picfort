# TASK: Family Wall Screen

**Status:** Ready for implementation  
**Designer approval:** 2026-11-10  
**Assigned to:** Piotr (Android Engineer)

## Approved design

Mockup: `docs/wireframes/family-wall-phone-mockup-v4.png`

Three vertical sections, all scroll together as one `LazyColumn`:

```
┌─────────────────────────────┐
│  [👤 Mama] [👤 Tata] [👤...] │  ← AvatarRow (sticky header)
├─────────────────────────────┤
│  [💬 "Had fun!"] [💬 ...] →  │  ← MessageCarousel (LazyRow)
├─────────────────────────────┤
│  ┌───────────────────────┐  │
│  │   wide photo          │  │
│  ├───────────┬───────────┤  │  ← PhotoGrid (StaggeredGrid)
│  │  photo    │  photo    │  │
│  ├───────────┴───────────┤  │
│  │   wide photo          │  │
│  └───────────────────────┘  │
└─────────────────────────────┘
```

## Screen: FamilyWallScreen

**Route:** `Screen.FamilyWall`  
**ViewModel:** `FamilyWallViewModel`

### State

```kotlin
data class FamilyWallUiState(
    val members: List<FamilyMember>,
    val selectedMemberId: String?,   // null = show all
    val messages: List<FamilyMessage>,
    val photos: List<MediaItem>,
    val isLoading: Boolean
)
```

### FamilyMember model

```kotlin
data class FamilyMember(
    val id: String,
    val displayName: String,
    val avatarUrl: String?,
    val color: Color  // Material You seed color for ring
)
```

### FamilyMessage model

```kotlin
data class FamilyMessage(
    val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val timestamp: Instant
)
```

---

## Component: AvatarRow

**Behavior:**
- Sticky at top of screen (use `stickyHeader` in `LazyColumn`)
- Horizontal `LazyRow`, items don't wrap
- Each item: circular `AsyncImage` (Coil) with colored ring border + name below
- Tap = set `selectedMemberId`; tap same again = clear (show all)
- Selected avatar: ring border becomes thicker + tinted background chip

**Composable signature:**
```kotlin
@Composable
fun AvatarRow(
    members: List<FamilyMember>,
    selectedId: String?,
    onSelect: (String?) -> Unit
)
```

**Visual:**
- Avatar circle: 56dp diameter
- Ring border: 2dp unselected, 3dp selected; color from `FamilyMember.color`
- Name: `MaterialTheme.typography.labelSmall`, single line, maxWidth = avatar width
- Item padding: 8dp horizontal

---

## Component: MessageCarousel

**Behavior:**
- Horizontal `LazyRow` below `AvatarRow`
- 2 cards visible, 3rd peeks (use `contentPadding end = 32.dp`)
- Tap on card = show full message dialog (simple `AlertDialog`)

**Card layout:**
```
┌─────────────────────────────┐
│ [avatar 32dp] Name   time   │
│ Message text (2 lines max)  │
└─────────────────────────────┘
```

**Composable signature:**
```kotlin
@Composable
fun MessageCarousel(
    messages: List<FamilyMessage>,
    onMessageClick: (FamilyMessage) -> Unit
)
```

**Visual:**
- Card size: `width = 200.dp, height = 90.dp`
- Background: `MaterialTheme.colorScheme.surfaceVariant` tinted with author color (alpha 0.15f)
- Corner radius: 16dp
- Card padding: `12.dp`
- Row `contentPadding`: `start = 16.dp, end = 32.dp`; `horizontalArrangement = 8.dp`

---

## Component: PhotoGrid

**Behavior:**
- Filtered by `selectedMemberId` when non-null
- Staggered/masonry layout: use `LazyVerticalStaggeredGrid(columns = StaggeredGridCells.Fixed(2))`
- First item spans full width: use `StaggeredGridItemSpan.FullLine`
- Tap = open full-screen viewer (existing `MediaViewerScreen` if available)

**Composable signature:**
```kotlin
@Composable
fun PhotoGrid(
    photos: List<MediaItem>,
    onPhotoClick: (MediaItem) -> Unit
)
```

**Visual:**
- Corner radius: 12dp
- Item spacing: 4dp
- Full-span hero item height: 220dp
- Regular item: aspect ratio from actual image (Coil `ContentScale.Crop`, fixed width)
- No borders, no shadows

---

## ViewModel logic

```kotlin
// Filter photos by selected member
val visiblePhotos = uiState.photos.filter { photo ->
    uiState.selectedMemberId == null ||
    photo.personIds.contains(uiState.selectedMemberId)
}
```

Data source: Immich REST API — use existing `ImmichRepository`.  
For `FamilyMember` list: use Immich `/api/people` endpoint (face recognition results).  
For `FamilyMessage`: new local Room entity `FamilyMessageEntity` (messages stored locally, not in Immich).

---

## Room: FamilyMessageEntity

```kotlin
@Entity(tableName = "family_messages")
data class FamilyMessageEntity(
    @PrimaryKey val id: String,
    val authorId: String,
    val authorName: String,
    val authorAvatarUrl: String?,
    val text: String,
    val timestampMs: Long
)
```

Migration: add new table, no existing data affected.

---

## Navigation

Add to `NavGraph`:

```kotlin
composable(Screen.FamilyWall.route) {
    FamilyWallScreen(viewModel = hiltViewModel())
}
```

Add entry point from main navigation (bottom nav or drawer — TBD with Lukasz).

---

## Acceptance criteria

- [ ] AvatarRow shows all Immich people with recognized faces
- [ ] Tapping an avatar filters the photo grid to photos containing that person
- [ ] Tapping same avatar again shows all photos
- [ ] MessageCarousel shows last 10 family messages, newest first
- [ ] Tapping a message card opens full text in a dialog
- [ ] PhotoGrid uses staggered layout, first photo spans full width
- [ ] All images load via Coil with placeholder and error states
- [ ] Screen passes `./scripts/verify.sh`
- [ ] No hardcoded strings — use `strings.xml`

---

## Out of scope (v1)

- Sending new messages from this screen (read-only)
- Push notifications for new messages
- Offline support for messages (online-only v1)
