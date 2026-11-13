# T-054 — Events: filter by family member

**Status:** done (4880c43; mockup `docs/wireframes/events-filter-v1.png` approved 2026-11-13)  
**Component:** Android app  
**Depends on:** T-053 (who the event is for), `MemberCarousel` (core:ui)

## Summary

The Events list gets the family's avatar row, as on the Wall and in Photos. Tapping a person shows
only the events they are in; tapping them again shows everyone's.

## Design

- `MemberCarousel` above Upcoming/Past, members from `FamilyMemberProvider.allMembers` (hidden ones
  left out). Shown only when there are members.
- A member's events are those whose `who` contains them: chosen in "Who is it for", or their birthday.
  Events with no one assigned (Christmas Eve) are hidden while a member is selected (owner's choice).
- Works in Upcoming and Past. Filtered in the app, no server change; Past keeps paging as before.
- Empty: "Nothing planned for Ola." / "No past events for Ola."
- The choice lives in the ViewModel: kept while switching tabs, not after the app is closed. A member
  who disappears clears the filter.

## Acceptance criteria

- [x] Tapping Ola shows her events and birthday only; tapping again shows all.
- [x] Works in Past; the empty texts name the member.
- [x] Unit test `forMember`; Paparazzi: no filter, filtered, dark, empty.
- [x] README Features, module AGENTS.md.

## Device check (2026-11-13, test server v0.8.0)

- Ola → "Basen T037" (Ola, Kuba) and "Ola's birthday" only; Past → "No past events for Ola.";
  Kuba → "Basen T037" only; tapping Kuba again shows all 4 upcoming events. No crashes.
