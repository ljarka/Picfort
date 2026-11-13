# T-057 — Photos: search by Immich people

**Status:** done (60d882a; mockup `docs/wireframes/search-people-v2.png` approved 2026-11-13)  
**Component:** Android app (`:feature:timeline`, `:core:ui`)  
**Depends on:** T-052 (metadata search by person), Immich v2+ (`/api/people`, `personIds` in search)

## Summary

Search finds photos by the people Immich recognised, alone or with the text query. A library can
have hundreds of people, so the row shows a few and "All people" holds everyone, findable by name.

## Design

- People: every named, visible person in my Immich library (`GET /api/people`, all pages, up to
  5000), in Immich's order (favourites, then most photos). Not the Picfort family: anyone named in
  Immich. Faces: `/api/people/<id>/thumbnail`. Loading fails quietly: no People section.
- "People" under the field: picked people first, then the others, 12 in all; "All people (N) ›"
  when there are more. The sheet: "Find a person" (case and accents ignored), grid of faces,
  tap picks or drops (results behind update at once), Done.
- Search: several people = photos with all of them. People only → `search/metadata` (newest
  first); text + people → `search/smart` with `personIds`. Nothing found: "No photos of Ola and
  Kuba together", "Nothing found for "beach" with Ola".
- `MemberCarousel` (`:core:ui`) got `selectedIds` and a nullable `onEditMember` (no menu).

## Acceptance criteria

- [x] Picking people searches at once; text and people combine; the viewer pages through results.
- [x] "All people" with a name filter; picked people stay at the front of the row.
- [x] Unit tests: people (pages, hidden/unnamed), request routing, row, filter, texts.
- [x] Paparazzi: row, all people, picked, with text, nothing; README, module AGENTS.md.

## Device check (2026-11-13, emulator, test Immich, test account)

- The test library has no recognised faces: `maestro/scripts/test-data.js` (PEOPLE=1) names 14 people
  "Osoba 01".."14" and tags faces by hand (`POST /api/faces`).
- `maestro/search-people.yaml`: Osoba 01 → 2 photos; All people (14) → "SOBA 02" filters → picked →
  Done → their shared photo; + Osoba 03 → "No photos of Osoba 01, Osoba 02 and Osoba 03 together". Passed.
