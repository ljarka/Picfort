# T-034 — Family settings: add people, Immich badge, birth date (Android)

**Status:** done  
**Component:** Android app  
**Depends on:** T-032 (Family screen, `FamilyRepository`), T-033 (API)

## Summary

In Settings → Family the owner can **add a person who isn't an Immich user**, sees which rows
**are** Immich users, and can give every member a **birth date**.

## Mockup first

`docs/wireframes/family-members-v2.png`: the Family list with the new "+" action and Immich badges;
the member sheet with the birth date row; the add-person sheet. It extends `family-settings-v1.png`.

## Design

- **List:** each row shows the avatar, the name and, for Immich users, a small Immich badge
  (label "Immich", with the Immich account name when it differs from the shown name). Manual members
  have no badge. A birthday ("17 May", "17 May · 36") appears under the name when set.
- **Add person:** an "Add person" action (top bar or FAB) opens a sheet: name (required), birth date
  (optional), photo from the phone (gallery or camera; Immich people aren't available, there is no
  account). Save → `POST /family/members`.
- **Member sheet** (T-032) gains **Birthday**: a date picker with a "Don't show the year" switch
  (sends `--MM-DD`) and "Remove". Same sheet for Immich and manual members.
- **Delete** (manual members only): in the member sheet, with a confirmation: "Delete Babcia? Her
  birthday event is deleted too." Immich users have no delete.
- **Hidden** works for both kinds.

## Members, in one place (`FamilyRepository`)

- `FamilyMember` gains `isImmichUser` and `birthDate` (`MonthDay` + optional year). Manual members
  have `isImmichUser = false`, `avatarUrl` from the profile photo.
- Posts come only from Immich users (manual members can't post). The wall's avatar row, Shopping, Events
  and the pickers show everyone via a `FamilyRepository.allMembers` flow (avatar row changed 2026-11-12
  at the owner's request: a manual member there just has no posts).
- The last copy is kept on disk (as T-032), so birthdays show offline.

## Acceptance criteria

- [ ] Adding "Babcia" with a birth date makes her appear on the other phone within 60 s.
- [ ] Immich users are marked; manual ones are not; manual ones can be deleted after a confirmation.
- [ ] The birth date can be set without a year, changed and removed, for both kinds of member.
- [ ] A manual member never shows as a post author (since 2026-11-12 it does show on the avatar row).
- [ ] Old server (`404` on `/family/members`): no "Add person" action.
- [ ] Unit tests: member merge (Immich + manual, birth date with and without year, age).
- [ ] Paparazzi: list with badges, add sheet, member sheet with birthday, dark.
- [ ] README Features; CI passes.

## Out of scope

- Changing Immich itself.

## Amendment (2026-11-12, owner's request)

- A manual member's photo comes from **Immich people** too: the picker searches the logged-in user's
  library, so the member needs no account. Both the Add person sheet and the member sheet have
  "Choose from Immich people"; a manual member's sheet says "Remove photo" instead of "Use Immich picture".
  (Upload from the phone was never built.)
