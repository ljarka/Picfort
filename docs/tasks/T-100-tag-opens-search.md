# T-100 — Tapping a tag in Details opens Search filtered by it

**Status:** in-progress  
**Component:** Android (`:feature:timeline`, `:app`)  
**Depends on:** T-090 (tag chips in Search), T-097 (tags in Details)

## Summary

A tag chip on top of a photo's Details opens Search with that tag picked, listing its photos.

## Design

- `AssetTagInfo.id`; `AssetDetails.tags: List<DetailTag(id, label)>` (`detailTags`, `tagLabel`).
- `ImageDetailsScreen(onTagClick(tagId, label))`; `:app` navigates to
  `Screen.Search(tagId, tagLabel)` (was an object). `SearchViewModel` reads both from its
  `SavedStateHandle`, picks the tag and searches; a tag Search doesn't list as a chip (e.g.
  "Blurry") is added in front so it can be dropped.

## Acceptance criteria

- [x] Unit test: detail tags carry their Immich ids.
- [x] Maestro `maestro/best-and-tags.yaml` (T-101): Details → "Best" → Search with the 3 tagged photos.
- [x] All checks green.
