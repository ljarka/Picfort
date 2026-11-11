# T-032 — Family settings: name, photo from Immich people, hide (Android)

**Status:** in-progress (code and tests done; check on a device). Mockup: `docs/wireframes/family-settings-v1.png` and `ScreenshotTest_family_*` / `people_picker`, approved by the owner 2026-11-11.  
**Component:** Android app  
**Depends on:** T-031 (API). Shares the Immich people picker with T-030: whichever lands
first builds `PeopleApi` and `PeoplePickerSheet`, the other reuses them.

## Summary

Settings → **Family** lists the Immich users of the server. For each one, anyone in the family
can set the name the app shows, a photo picked from the faces Immich recognised, and whether
the account is hidden (test accounts). The result is used everywhere the app shows a family
member.

## Screen

```
┌─────────────────────────────┐
│ ←  Family                   │
│ (◯) Mama        Anna K.     │  shown name · Immich name (small) when different
│ (◯) Tata                    │
│ (◯) LukaszTest   Hidden     │  hidden accounts last, dimmed
└─────────────────────────────┘

Member sheet:
│ Name    [ Mama            ] │  empty = Immich name
│ Photo   (◯) [Choose from Immich people] [Use Immich picture] │
│ [x] Show in the family      │  off = hidden
│                     [Save]  │
```

- Entry: Settings → **Family** (new row); also long-press an avatar on the wall → **Edit**.
- **Choose from Immich people:** the people picker (named, not hidden people of **my** Immich
  library, as in T-030). The face thumbnail is downloaded with my token, cropped square,
  resized to 256 px and uploaded with `PUT /family/<id>/photo`. "Use Immich picture" deletes it.
- **Show in the family** reflects the effective state (see below); changing it saves
  `hidden` explicitly.
- Save sends `PATCH` (and the photo calls). Not optimistic; an error keeps the sheet open
  with a snackbar.

## How members are built (one place)

Replace `WallRepository.members` and `testAccountIds` (PR #37) with a `FamilyRepository`
used by every feature (wall, shopping "checked by", events "Added by", receipts "Paid by",
event albums T-026, today panel):

- name = `display_name` ?: Immich name
- avatar = profile photo ?: Immich profile picture ?: initials on the Immich avatar color
- hidden = `hidden` ?: `isTestAccount(Immich name)` (the PR #37 rule stays as the default for
  users without a setting); the logged-in user is never hidden
- profiles load with the wall and every 60 s while a family screen is visible; the last copy
  is kept on disk (as T-019's lists) so names and photos survive a cold start offline.
- Without a Picfort server or with an old one (`404` on `/family`): Immich names and
  pictures, test-account rule, no Family row in Settings.

Posts keep `author_name` from the time of posting on the server; the app shows the profile
name for known authors, so a rename applies to old posts too.

## Acceptance criteria

- [ ] Rename Mama on one phone → the other phone shows the new name on the wall within 60 s,
      including on old posts.
- [ ] A face picked from my Immich people becomes the avatar for everyone; "Use Immich
      picture" reverts.
- [ ] Hiding an account removes it and its posts from the wall; unhiding brings them back;
      `LukaszTest` is hidden by default and can be shown.
- [ ] I can't hide myself (the switch is disabled on my own row).
- [ ] Offline cold start keeps names and photos.
- [ ] Unit tests: the merge rules above (name, avatar, hidden default and override, me).
- [ ] Paparazzi: Family list (with hidden), member sheet, picker, dark.
- [ ] README Features; CI passes.

## Out of scope

- Changing Immich itself (names, profile pictures).
- Different names per viewer.
- Adding people who aren't Immich users (children are in T-028).
