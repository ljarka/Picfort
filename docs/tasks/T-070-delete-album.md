# T-070 — Delete an Immich album

**Status:** done (9588713)  
**Component:** Android app (`:feature:timeline`, `:app` navigation)  
**Depends on:** T-062 (Immich 3 contract)

## Summary

Owner request (2026-11-14): albums could only be viewed; there was no way to delete one. The album
view now has a ⋮ menu with "Delete album", behind a confirmation. Only the album goes; its photos
and videos stay in Immich and on the timeline.

## Design

- `DELETE /api/albums/{id}` (`CollectionsService.deleteAlbum`, 204). Immich lets only the owner
  delete; `AlbumDto.ownerId` comes from `albumUsers` (role "owner", Immich 3 has no `ownerId`).
- The menu shows when `canDelete(myId)` (`FamilyMemberProvider.myId`): the owner, or when either id
  is unknown. Then a refusal is shown in the dialog ("Only the album's owner can delete it" on 403).
- `AlbumRoute.canDelete` carries the decision from the list. After a delete the album screen closes
  and the list drops the album (`CollectionsRepository.deletedAlbums`), no reload.
- Dialog: "Delete album “X”?", "Its photos and videos stay in Immich and on the timeline. A shared
  album goes for everyone it's shared with.", Cancel / Delete ("Deleting…" while it runs).

## Acceptance criteria

- [x] `maestro/album-delete.yaml` (test account): Cancel keeps the album; Delete → back on Albums,
  album gone there and in Immich (checked by `test-data.js`), its photo still in Immich.
- [x] Unit tests: `DeleteAlbumTest` (who gets the menu, list told, 403 text), owner from the Immich 3
  album fixture (`ImmichV3ContractTest`).
- [x] Paparazzi `album_delete`; unit tests, ktlint, lint, boundaries green.

## Follow-ups

- Rename an album, remove photos from an album (not asked yet).
