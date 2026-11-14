# T-068 — "Goes to school" in Family settings; "+" without a caption

**Status:** done (cab39d7)  
**Component:** Android app (`:feature:school`, `:feature:family`, `:core:model`, `:core:ui`)  
**Depends on:** T-066

## Summary

Owner decisions (2026-11-14): School's "+" works like on the other screens: it opens Family
settings, and the member sheet has a switch "Goes to school" next to "Show in the family"; switched
on, the member shows on the School tab. The "+" at the end of the avatar rows (Wall, Photos,
School) has no "Family" caption any more, just "+". Replaces T-066's picker sheet.

## Design

- `:core:model` `SchoolMembership` (`memberIds`, `refresh`, `setGoesToSchool`), implemented by
  `:feature:school` (`SchoolMembershipImpl`: a child row per member; off deletes it and the
  timetable), used by `:feature:family` (no feature-to-feature dependency).
- Member sheet: the switch is saved with **Save**; switching off a member who is in School warns
  "Their timetable is deleted when you save". The change is refused offline (the error shows).
- `MemberCarousel`: the "+" circle has no caption, `contentDescription` "Family settings".
- School: "+" → `onOpenFamily` (Family settings); empty state "Tap + and switch on “Goes to school”
  for each child". The child sheet keeps the bell schedule and "Remove from School".

## Acceptance criteria

- [x] `maestro/school.yaml`: "+" → Family → Kuba (added by hand) → Goes to school → Save → Kuba on
  School; … ; switch off (warning shown) → School empty again.
- [x] `school-family.yaml`, `school-offline.yaml`, `smoke.yaml` ("Family settings") pass.
- [x] Paparazzi: Wall/Photos rows with a plain "+", member sheets with the switch and the warning.
- [x] Unit tests, ktlint, lint, boundaries green.
