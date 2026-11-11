# T-030 — Children from Immich people: face photo and the child's photos

**Status:** ready (first step: check the People API on the owner's server; mockup of the picker)  
**Component:** Android app  
**Depends on:** T-029 (School tab, child sheet), T-028 (photo and per-parent person links)

## Summary

When adding a child, pick them from the people Immich recognised by face: the name and the
face photo come from Immich. Each parent can link the child to the person in **their own**
Immich library, and then open **Photos of Ola** from the School tab.

## Why per parent

Immich face recognition runs per user: Mama's library and Tata's library each have their own
"Ola" with a different person id, and a person id from one library can't be read with the
other user's token. So the server stores one link per (child, parent) (T-028 `child_people`)
and returns only the caller's (`my_person_id`). The face photo is copied to the Picfort server
once, so every parent sees it whether or not they linked the child.

## Check first (owner's server, Immich v3.3.5)

The public API has these; confirm the shapes with a real token before coding and note the
result here:
- `GET /api/people?withHidden=false` → `{people: [{id, name, isHidden, …}], …}` (paged?).
- `GET /api/people/{id}/thumbnail` → the face crop (JPEG).
- Photos of a person: `POST /api/search/metadata` with `personIds: [id]`, `page`, `size`,
  newest first (the same endpoint the slideshow uses on the server).

## Screens

### Add child / child sheet (T-029) gets "From Immich"

```
┌─────────────────────────────┐
│ Add child                   │
│ [ From Immich people ]      │  opens the picker
│ or type a name: [         ] │  as in T-029
└─────────────────────────────┘

People picker (bottom sheet, grid of faces):
│ (face) Ola   (face) Staś   (face) Mama … │  named, not hidden people of MY library
│ [Search names]                          │
```

- Picking a person on **Add child**: name prefilled (editable), the face thumbnail is
  downloaded with my token, resized to 256 px, uploaded with `PUT …/photo`, and my link is
  saved with `PUT …/person`. Color: default from the name (as now), editable.
- On an **existing child** the sheet shows:
  - **Link to my Immich person** when `my_person_id` is null (other parent's child): same
    picker; saves only my link, and offers "Use this face as Ola's photo" when the child has
    no photo.
  - **Change** / **Unlink** when linked; **Update photo from Immich** to re-copy the face.
- Unnamed people are not listed (a face without a name is not useful as a child); a hint says
  "Name people in Immich to see them here".

### Photos of Ola

- Entry: on the School tab, the selected child's chip menu (long-press) and an icon in the
  summary card: **Photos of Ola**, shown only when `my_person_id` is set.
- Screen: top bar "Ola", the existing `AssetGrid` (as albums/search), paged from
  `search/metadata` with `personIds`, newest first; tap → image details with a new `source`
  so swiping stays within these photos.
- Person no longer exists in my library (`400/404`): "Ola isn't in your Immich people any more"
  with **Link again**.

## Data

- `PeopleApi` on the Immich Retrofit client (`/api/people`, thumbnail via OkHttp bytes) and
  `PeoplePickerSheet`, shared with T-032 (whichever lands first builds them).
  Reuse it in T-008 (People screen) later.
- `ChildPhotosPagingSource` on `search/metadata` (person filter); the grid model as albums.
- School repository: `uploadPhoto`, `linkPerson`, `unlinkPerson` (T-028 endpoints).

## Acceptance criteria

- [ ] The People API check above is done and recorded.
- [ ] Adding a child from Immich fills the name and shows the face on the chip, for both parents.
- [ ] The second parent links the same child to their own person; each parent's link is
      independent (unlinking on one phone doesn't affect the other).
- [ ] Photos of Ola shows the person's photos from my library, newest first, paged; opening
      one and swiping stays within them.
- [ ] Not linked → no Photos entry; removed person → the message with Link again.
- [ ] Typing a name (T-029) still works without Immich people.
- [ ] Paparazzi: picker (people, empty hint), child sheet linked/unlinked, Photos of Ola, dark.
- [ ] README Features; CI passes.

## Out of scope

- Automatic matching between parents' libraries (Immich has no shared people).
- Children's photos on the Family Wall or the today panel.
- The People screen (T-008), which can reuse `PeopleApi`.
