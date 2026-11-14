# T-066 — School: children are family members

**Status:** done (8e15159)  
**Component:** Picfort server (`school.py`), Android app (`:feature:school`, `:core:ui`)  
**Depends on:** T-029, T-032/T-034 (family members, `FamilyMemberProvider`)

## Summary

Owner feedback on T-029: children were typed in by name with a color, unlike every other screen.
Now the School tab works like the Wall's row: faces in circles, a last "+" in the same style. At
first there is only "+"; it lists the family, and you pick who goes to school (not everyone does).

## Design

- Server: `POST /school/children` takes `immich_user_id`, the family member's id (an Immich user
  or a manual member, both UUIDs from `/family`). The column existed (T-028, "reserved").
- App: `Child.memberId`; the ViewModel shows the member's current name and face
  (`FamilyMemberProvider.allMembers`); `candidates` = shown members not in School yet.
- Row: `MemberCarousel` (`:core:ui`), its "+" circle now takes a label (`addLabel` "Add").
- Child sheet: bell schedule and "Remove from School" (the member stays in the family); name and
  color only for an older child added by name.
- T-030 still adds the face from Immich people for children who aren't family members, and
  "Photos of Ola".

## Acceptance criteria

- [x] Empty School shows only "+"; it lists the family; picking adds them (server test, Maestro).
- [x] "+" leaves out members already in School (`maestro/school-family.yaml`).
- [x] Names and faces follow the family; removing from School keeps the family member.
- [x] Server tests, unit tests, Paparazzi (row, picker, child sheet), ktlint, lint green.
- [x] Emulator, test server: `school.yaml`, `school-family.yaml`, `school-offline.yaml` pass.
