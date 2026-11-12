# T-053 — Events: who the event is for

**Status:** done (606eca1; mockup `docs/wireframes/events-who-v1.png` approved 2026-11-13; checked on the emulator)  
**Component:** Android app + server (`events.db` v4)  
**Depends on:** T-022/T-023 (events), T-034 (members, manual members), ADR 0010

## Summary

An event can name the family members it is about ("Swimming lessons" → Ola, Staś). The list and
the detail show their avatars; the form has a "Who is it for" row of chips.

## Design

- **Several members per event**, in the order chosen; none is fine (a family event).
- Server: `events.members` (JSON list of member ids), `members` in create/PATCH and in every event
  JSON. Only the id format is checked (Immich users aren't all in `family.db`); a member deleted
  later is just not shown.
  Birthday events are about their member: the app adds `birthdayOf` to the list (`FamilyEvent.who`).
- List row: overlapping avatars on the right, at most 3 ("+N" for the rest); TalkBack reads the names.
- Detail: avatars and "Ola, Staś" under the date.
- Form: "Who is it for" under the title, one `FilterChip` per member (Immich users and manual
  members, hidden ones left out); tap toggles.

## Acceptance criteria

- [x] Choosing Ola and Staś saves both; the list and the detail show them; editing keeps them.
- [x] A birthday shows its member's avatar.
- [x] A deleted member disappears from the events quietly.
- [x] Server tests: save, validate, migrate; app: unit tests for `membersOf`/`who`, Paparazzi list,
      detail, form, dark.
- [x] README Features, ADR 0010 amendment, module AGENTS.md.

## Device check (2026-11-13, test server on a local image of this commit)

- `events.db` migrated 3 → 4 on start (backup `events.db.v3.bak`).
- "Basen T037": chose Ola and Kuba, saved; the detail shows "Ola, Kuba", the list the two avatars,
  still after restarting the app. Ola's birthday shows her avatar.
