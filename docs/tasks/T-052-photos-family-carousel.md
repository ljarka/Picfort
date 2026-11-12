# T-052 — Photos: the family carousel, photos of one person

**Status:** in-progress (mockup approved 2026-11-12: `docs/wireframes/photos-people-v1.png`, frames 1–3;
the "In my photos" section of frames 4–5 was dropped: choosing a face already links)  
**Component:** Android app + server (`family.db` v4)  
**Depends on:** T-032/T-034 (members, Immich people picker), ADR 0010

## Summary

The Family Wall's avatar row also sits at the top of Photos. Tapping a member shows only the photos
with that person (newest first); tapping again goes back to the whole timeline.

## Decisions (owner, 2026-11-12)

- **Link only in Family settings.** Immich face recognition is per library, so "Ola" has a different
  person id for each parent. Choosing a member's face with **Choose from Immich** saves *my* link
  (member → my person); each parent links on their own phone. No matching by name.
- **One member at a time** (as on the Wall).
- Members without my link are faded; a tap says "Kuba isn't linked to your Immich people" with
  **Link** → the member's sheet in Family settings.

## Design

- Server: `member_people (member_id, viewer_id, person_id)`; `GET /family/people` → `{people: {member_id:
  person_id}}` for the caller only; `PUT /family/<id>/person {person_id}`; `DELETE /family/<id>/person`.
  Deleting a manual member drops its links.
- App: `FamilyMemberProvider.myPersonIds` (`:core:model`); `FamilyRepository` links when a face is picked
  (edit or add) and reloads the links on refresh.
- Photos: `MemberCarousel` (moved from the Wall to `:core:ui`) as the timeline's first list item;
  `PeopleFilterViewModel` pages `POST /api/search/metadata {personIds, page, size: 100, order: desc}`;
  the viewer pages through source `person:<id>`. Phone-only photos aren't in the person view (no faces).
  Back or reselecting the tab returns to the timeline.

## Acceptance criteria

- [x] Server: links per viewer, replace/unlink, removed with a manual member (tests).
- [x] Picking a face links the member (repository test); the carousel shows on Photos, faded when not linked.
- [x] Not linked → snackbar above the tab bar → Link opens the member's sheet (checked on the emulator).
- [x] Request shape of `search/metadata` accepted by Immich (checked on the test account).
- [ ] A linked member shows their photos on a device (needs named people; the test account has none).
- [x] Paparazzi: Photos with the carousel, a person's photos, not linked.
