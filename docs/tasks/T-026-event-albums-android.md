# T-026 — Events: link or create an Immich album (Android)

**Status:** ready (mockup: the album parts are added to `events-v1.png` from T-023)  
**Component:** Android app  
**Depends on:** T-023 (event screens), T-022 (`album_id`)

## Summary

An event can point to an Immich album: pick an existing one, or create a new album shared
with the whole family. The event list and detail show the album's cover; the detail opens
the album.

## Immich API

Checked against Immich's OpenAPI spec for **v3.3.5** (2026-11-11), not yet against the
owner's server. The app's login token is used (session tokens have no permission scopes).

| Call | Use |
|---|---|
| `GET /api/albums` | Already in `CollectionsService.albums()`. Each album has `id`, `albumName`, `albumThumbnailAssetId` (nullable), `assetCount`, `shared`, and `albumUsers`: `[{role, user: {id, name, …}}]`, where **the first entry is the owner** (role `owner`). Optional filters `isOwned`, `isShared`. |
| `GET /api/albums/{id}` | One album, same shape. New in `CollectionsService`. |
| `POST /api/albums` `{albumName, albumUsers: [{userId, role}]}` | Create and share in one call. `role` is required here: `editor`. Extend `CreateAlbumRequest`. |
| `PUT /api/albums/{id}/users` `{albumUsers: [{userId, role}]}` | Share an existing album with more users (at least one). Returns the album. New. |

Add `albumUsers` and `shared` to `AlbumDto` (with defaults, so other callers don't change).

Check on the owner's server before building the UI, and note the results here:
- `GET /api/albums` without filters returns both my albums and albums shared with me.
- What `PUT …/users` answers for a user who is already in the album, and for a caller who is
  not the owner.
- The status of `GET /api/albums/{id}` for an album the user can't see (expected `400`).

If one of these differs, adjust this task before coding; don't work around it in code.

## The family

"The family" = every user in the Room `users` table (from the sync stream, T-002) except the
logged-in one. An album is **shared with the family** when every one of them is in its
`albumUsers`.

## Form (T-023's add / edit screen)

A row **Album** below the note, opening a bottom sheet:

- **No album** (default).
- **Create album "<event title>"**: on Save of the event, `POST /api/albums` with the title
  and all family members as `editor`, then the event with the new `album_id`. Renaming the
  event later does not rename the album.
- The user's albums from `GET /api/albums` (own and shared with them), by name, with cover and
  photo count, and a search field when there are more than 10. Loading and error (Retry)
  states.

Order on Save: album first, then the event. If creating the album fails, nothing is saved
and the form shows a snackbar. If the event fails after the album was created, keep the
album id in the form so a second Save doesn't create another album.

## Sharing an existing album

After choosing an album that is not shared with the family:
- I am the owner (`albumUsers.first().user.id` is me): a checkbox under the Album row,
  "Share with the family (N people)", on by default. On Save, `PUT …/users` with the missing
  members as `editor`. A failure doesn't block saving the event: snackbar "Saved, but the
  album couldn't be shared".
- I am not the owner: a note "Only <owner name> can share this album with everyone."

## Showing the album

- Event row (Upcoming and Past): the album cover as a 56 dp thumbnail at the end of the row
  (`albumThumbnailAssetId` through the existing Immich thumbnail loader). Past events show it
  larger (it's a memory); exact size from the mockup.
- Event detail: a card with the cover, the album name and "N photos"; tap opens the existing
  album screen (`Screen.Album(id, name)`), from where photos open in the viewer as today.
- Album data for the visible events comes from one `GET /api/albums` per load of the events
  list (a map by id), not one request per event.
- The event has an `album_id` this user can't see (not in the list, or `GET /api/albums/{id}`
  fails with `400`/`403`/`404`): the row shows no cover, the detail shows "Album not
  available to you" without an error. Editing keeps the `album_id` unless the user picks
  something else.
- Immich unreachable: events still show, without covers.

## Acceptance criteria

- [ ] The three checks above are done on the owner's server and written into this task.
- [ ] Link an existing album; the cover shows in the list and the detail; tapping opens the
      album and its photos.
- [ ] "Create album" makes an album named after the event that another family member can
      open and add photos to from their phone.
- [ ] Choosing my own unshared album with the checkbox on shares it with everyone; another
      member then sees the cover.
- [ ] Someone else's unshared album shows the note, and other members see "Album not
      available to you".
- [ ] Changing the album and removing it ("No album") work; the Immich album itself is never
      deleted or renamed.
- [ ] A failed album creation saves nothing; retrying doesn't create two albums.
- [ ] Unit tests: "shared with the family" and "missing members" logic, save ordering with
      failures.
- [ ] Paparazzi: album sheet, form with the share checkbox and with the owner note, detail
      with album and with "not available", list with covers.
- [ ] README Features; CI passes.

## Out of scope

- Adding photos to the album from the event (use the album screen / Add to album).
- Auto-adding photos taken during the event (v2 candidate).
- Unsharing, changing roles, deleting albums.
- Storing the album on the Picfort server beyond its id.
