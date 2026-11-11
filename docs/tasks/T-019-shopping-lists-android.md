# T-019 — Shopping lists: Android screens (Keep-like)

**Status:** in-progress (code done; check on a device). Mockup: the Paparazzi screenshots `ScreenshotTest_shopping_*`, approved by the owner 2026-11-11.  
**Component:** Android app  
**Depends on:** T-017 (Shopping tab), T-018 (API)

## Summary

The Shopping tab shows the family's lists as cards. A list opens as a checklist like Google
Keep: tap to check, checked items drop into a collapsed section, add items with the keyboard
staying open. The last loaded lists stay readable without a connection (in the shop).

## Mockup first

Save a mockup of both screens as `docs/wireframes/shopping-lists-v1.png` and get the owner's
approval before coding, as for the wall (`family-wall-approved-v4.png`). The sketches below
fix the content, not the look.

## Screens

### Lists (tab root)

```
┌─────────────────────────────┐
│ Shopping            [⋮] [⚙] │  ⋮ → "Archived lists"
│ ┌──────────┐ ┌──────────┐   │
│ │Biedronka │ │Weekend   │   │  2-column staggered grid of cards
│ │□ milk    │ │□ coal    │   │  first 6 unchecked items, "+3 more"
│ │□ bread   │ │□ sausage │   │
│ │ 2/7 done │ │ 0/2 done │   │
│ └──────────┘ └──────────┘   │
│                  (+ List)   │  FAB
└─────────────────────────────┘
```

- FAB → dialog "New list" with a required title → `POST /lists` → opens the list with
  "Add item" focused.
- Cards are read-only previews; tap opens the list. A list with every item checked shows
  "All done".
- "Archived lists" opens the same grid for `?archived=1` (back arrow, no FAB).
- Empty: "No shopping lists yet" with the FAB. Load error with nothing cached: message +
  Retry, as on the wall.

### List detail

```
┌─────────────────────────────┐
│ ←  Biedronka           [⋮]  │  ⋮ rename, uncheck all, delete checked items,
│ □ milk                  ✕   │    archive / unarchive, delete list (own)
│ □ bread                 ✕   │
│ + Add item                  │
│ ▾ 3 checked items           │  collapsed by default
│ ☑ b̶u̶t̶t̶e̶r̶          (◯)  ✕   │  struck through, avatar of who checked it
└─────────────────────────────┘
```

- Tapping the checkbox or the row toggles; tapping the text edits it inline (saved on focus
  loss or Done; an emptied text deletes the item).
- "Add item": the keyboard action adds the item and keeps the focus for the next one; on an
  empty field it closes the keyboard.
- ✕ deletes an item and shows a snackbar with "Undo" (re-creates it with the same text and
  `position`).
- Checked items show `checked_by` as a 24 dp `MemberAvatar` (members from `WallRepository`'s
  users; an unknown id shows no avatar).
- "Delete list" asks for confirmation and is shown only to the creator; "Delete checked items"
  asks too.
- Unchecked and checked items are separate items of one `LazyColumn` (no nested lazy lists).

## Data & sync

- `ShoppingApi` follows `WallApi` (`ui/wall/WallApi.kt`): plain OkHttp on the shared client,
  base URL from `FrameSettingsStore.frameUrl` via `normalizeFrameUrl`, kotlinx.serialization
  DTOs with `@SerialName` for the snake_case fields. It is not Retrofit: the address can
  change at runtime. Move `WallApi.execute` (token header, Picfort-key rejection, error
  mapping, cleartext and IO errors) into a shared `PicfortServerClient` and use it from both;
  T-021, T-023 and T-029 use it too.
- `WallAuthInterceptor` (`network/AuthInterceptor.kt`) signs only `<server>/wall…`. Make it
  cover `/lists`, `/receipts`, `/spending`, `/events` and `/school` as well (same prefixes as the server,
  T-018) and rename it `PicfortAuthInterceptor`. Frame-control calls stay unsigned.
- Errors, as the wall's: `401` → "The Picfort server didn't accept your Immich login" (no
  logout), `502` → "The Picfort server can't reach Immich", `404` on `/lists` itself → "This
  Picfort server has no shopping lists yet, update it", wrong key → the existing message.
- `ShoppingRepository` holds the lists in a `StateFlow`.
  - Every action is applied locally first, then sent. Requests for one list go out one at a
    time, in order. On an error the change is rolled back with a snackbar; a `404` for an item
    or list removes it quietly (someone else deleted it).
  - New items get a temporary negative id until the server answers (as wall posts do).
  - Poll the open list every 10 s with `If-None-Match` (`304` = nothing to do), the lists grid
    every 60 s, both only while visible; pull-to-refresh on both.
  - A poll result is ignored while requests for that list are still in flight, and never
    replaces the text of the item being edited.
- **Offline copy:** after every successful `GET /lists`, write the response to a JSON file in
  the app's files dir. When the server can't be reached, show that copy with a banner
  "Offline. List from 14:32" and disable editing (checkboxes, add, ✕, menu). Not Room: no
  schema to wipe. Clear the file on logout and when the server address changes.

## Acceptance criteria

- [ ] Create, rename, archive, unarchive, delete (own) a list; delete is not offered on
      someone else's list.
- [ ] Add, edit, check, uncheck, delete (with Undo) items; uncheck all; delete checked items;
      the checked section collapses and expands and shows who checked each item.
- [ ] Adding ten items in a row needs no tap outside the keyboard.
- [ ] A second phone sees a checked item within 10 s while the list is open; checking
      different items on two phones at once loses neither.
- [ ] Typing in an item is not interrupted or overwritten by a poll.
- [ ] Server unreachable: the last loaded lists are shown read-only with the offline banner;
      they also show after the app was killed. Back online, editing works again without
      leaving the screen.
- [ ] A failed action rolls back with a snackbar.
- [ ] No server address → the shared empty state from T-017.
- [ ] TalkBack: a row is read as "<text>, checked / not checked"; ✕ as "Delete <text>"; touch
      targets are at least 48 dp.
- [ ] Unit tests for `ShoppingRepository`: optimistic add/check/delete, rollback, poll merge
      during in-flight requests and while editing, offline copy.
- [ ] Paparazzi: lists grid, empty, archived, list detail with the checked section closed and
      open, offline banner, one dark.
- [ ] README: Features and screenshots. CI passes.

## Out of scope

- Checking items off without a connection (offline queue): the first v2 candidate.
- Drag to reorder (the API already has `position`), list colors, pasting several lines as
  several items, suggestions from earlier items, reminders, keeping the screen on in the shop.
- Receipts on a list (T-021).
