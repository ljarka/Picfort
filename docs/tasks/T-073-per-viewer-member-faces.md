# T-073 — Members' faces from each viewer's own Immich

**Status:** in-progress (code done, not committed)  
**Component:** Android app (`:feature:family`)  
**Builds on:** [T-052](T-052-photos-family-carousel.md) (per-viewer link member → Immich person)

## Summary

Owner report (2026-11-15): to search photos by person, every Immich user links each family member
to a person in their own Immich. Picking the face also uploaded its thumbnail as the member's
**shared** photo, so everyone saw the face from the library of whoever linked last. Wanted: each
Immich user sees the faces from their own photos.

## Design

- `mergeFamilyUsers(myPersonIds)`: avatar = my linked face (`<immich>/api/people/<id>/thumbnail`,
  loaded with my Immich auth) ?: shared photo ?: Immich account picture (unless `no_photo`) ?: initials.
  Every screen gets it through `FamilyMemberProvider` (Wall, Shopping, Events, School).
- Picking a face: always link it; upload the shared photo only when the member has none
  (the fallback for viewers who haven't linked, and for server-side `photo_url`s).
- "Clear" / "Profile picture": unlink my face (`DELETE /family/<id>/person`, already on the server)
  and delete the shared photo, as before. Unlinking also drops the member from my Photos filter.
- No server change.

## Tests

- `FamilyRepositoryTest`: `each viewer sees the member's face from their own Immich people`, updated
  expectations for picking, re-picking and clearing.

## Left for later

- A shared photo uploaded before this change stays as the fallback; "Clear" + pick again resets it.
