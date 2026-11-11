# T-017 — App shell: Family Wall as home, bottom navigation

**Status:** ready  
**Component:** Android app (Compose Navigation)  
**Depends on:** T-002 (Family Wall screen; its code is merged). Supersedes T-002's "the app
has no bottom navigation", its back arrow and its entry point in the Timeline top bar.

## Summary

The app opens on the Family Wall. A bottom navigation bar switches between five tabs:
**Wall**, **Shopping**, **Events**, **School**, **Photos**. Photos is today's timeline with
albums and search. Shopping, Events and School show a placeholder until T-019, T-023 and
T-029 land.

## Why

Picfort becomes a family hub; the wall is what the family checks daily. Photos stay one tap
away.

## Design

```
┌─────────────────────────────┐
│ Family Wall          [⚙]    │  each tab has its own TopAppBar
│ …                           │
│                       (+)   │  FAB belongs to the tab
├─────────────────────────────┤
│ [💬Wall][🛒Shopping][📅Events][🎒School][🖼Photos] │  NavigationBar (M3, 5 = its maximum)
└─────────────────────────────┘
```

- Labels (English, inline like the rest of the app): Wall, Shopping, Events, School, Photos,
  in that order (daily family things first, the photo library last). One line each, no
  wrapping or ellipsis at 360 dp width and font scale 1.3. Labels always visible; icons have `contentDescription = null` (the label is read by TalkBack).
- Icons: forum, shopping_cart, event, school, photo_library. The app has only `material-icons-core`,
  which lacks most of them: add the five as vector drawables in `res/drawable`. Don't add
  `material-icons-extended`.
- **Bottom bar visible** only on the five tab roots. Hidden everywhere else (albums, album,
  search, image details, settings, frame, login, init, server setup, and later list detail,
  receipt, spending, event detail and form, timetable editing).
- **Settings** is reachable from every tab: ⚙ in each tab root's top bar (today it is only in
  the timeline bar).
- Family Wall top bar: remove the back arrow (`onBack`), add ⚙.
- Timeline top bar: remove the Family Wall icon (`onWallClick`); keep Search, Albums, Settings.

## Navigation

- Five nested graphs in the existing `NavHost` (`MainActivity`): `WallGraph` (FamilyWall),
  `ShoppingGraph`, `EventsGraph`, `SchoolGraph` (placeholders), `PhotosGraph` (Timeline,
  Albums, Album, Search). Screens used from several tabs stay top-level destinations:
  ImageDetails, Settings, Frame.
- Tab switch: `navigate(graph) { popUpTo(WallGraph) { saveState = true }; launchSingleTop =
  true; restoreState = true }`, so each tab keeps its own back stack and state.
- After `Init` / `PicfortServerSetup`, navigate to `WallGraph` (instead of `Timeline`). The
  logged-out handler (`sessionManager.loggedOut`) keeps popping everything back to `Init`.
- Back: inside a tab pops that tab's stack; on a tab root other than Wall goes to Wall; on
  Wall leaves the app.
- Reselecting the current tab pops it to its root; on Photos, already at the root, it scrolls
  the timeline to the top.
- The selected tab is the graph that contains the current destination; on a top-level screen
  (ImageDetails, Settings) the bar is hidden, so nothing needs highlighting.
- Layout: one outer `Scaffold` with the `NavigationBar` around the `NavHost`, inside the
  existing `SharedTransitionLayout`. Tab roots get the bar's height as bottom padding, so
  FABs, snackbars and the timeline's fast scroller sit above the bar. The bar hides without
  animation when ImageDetails opens, so the timeline → photo shared-element transition and
  the edge-to-edge viewer work as today.
- Only the visible tab is in composition, so the wall's 60 s poll (`repeatOnLifecycle`) stops
  on other tabs by itself. Check it.

## No Picfort server

Wall, Shopping, Events and School need the Picfort server. Move the wall's empty state (`Centered(…)`
in `FamilyWallScreen.kt`, "Connect your Picfort server to use the Family Wall.") into a shared
`ServerRequiredState(message, onSaved)` that opens `FrameSettingsDialog`, and use it on the
wall and on the three placeholders. Photos works without a server.

First-run step (`PicfortServerSetupScreen`): title "Picfort server" (drop "(optional)"),
text "The Picfort server keeps your family's wall, shopping lists, events and school timetables, and runs the
picture frame. You can also set it later in Settings." Skip stays.

## Placeholders

Shopping, Events and School: top bar with the title and ⚙, body "Shopping lists are coming
soon." / "Events are coming soon." / "School timetables are coming soon." (or
`ServerRequiredState` without a server). T-019, T-023 and T-029 replace them.

## Acceptance criteria

- [ ] After login (and the server step), the app opens on the Wall tab; a cold start of a
      logged-in user also opens on Wall.
- [ ] Five tabs switch without losing each tab's state (timeline scroll position, wall
      member filter, an album opened in Photos).
- [ ] Back behaves as above; reselect pops to the root / scrolls the timeline up.
- [ ] Bottom bar only on the five roots; the photo viewer is edge to edge and its open/close
      transition from the timeline is unchanged.
- [ ] FAB, snackbars and the last timeline row are not covered by the bar; the bar respects
      gesture and 3-button navigation insets.
- [ ] ⚙ on every tab root opens Settings; the wall has no back arrow; the timeline has no
      wall icon.
- [ ] Without a server address, Wall/Shopping/Events/School show the shared empty state, whose
      button opens the server dialog; after Save the wall loads. Photos works.
- [ ] A rejected Immich token still returns to login from any tab.
- [ ] TalkBack reads each tab as "<label>, tab, N of 5" with its selected state.
- [ ] The five labels fit on a 360 dp wide screen at font scale 1.3.
- [ ] Paparazzi: each tab root with the bar (light; one dark), the bar at 360 dp / 1.3, no-server state, updated
      first-run step. Existing screenshots re-recorded where the bar or top bar changed.
- [ ] README: screenshots table, Features and "Using it from the app" updated.
- [ ] CI passes (`verifyPaparazziDebug lintDebug assembleDebug`).

## Out of scope

- Badges on tabs (new posts, unchecked items).
- Tablet navigation rail and frame mode (T-003 decides how the shell looks on the tablet).
- Choosing the start tab in Settings.
