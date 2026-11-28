# T-097 — Photo details: Immich tags on top

**Status:** done (`719dfe1`)  
**Component:** Android (`:feature:timeline`)  
**Depends on:** T-090 (tag labels), T-089 (`ai/*` tags)

## Summary

The photo's "Details" sheet shows its Immich tags first, as chips under the title, before the
date, file, camera and place rows.

## Design

- Data: `GET /api/assets/{id}` already returns `tags` (Immich 3); `AssetInfo.tags`
  (`AssetTagInfo(name, value)`), no extra request. Phone-only photos have no tags.
- Labels (`assetTagLabels`, pure): Picfort's `ai/` tags by their friendly names in the Search
  order ("Best", "Nature", "Kids' art", …), clean-up tags by name ("Blurry", "Low quality", …),
  other `ai/` tags by their last segment; then user tags by their full value, alphabetically;
  duplicates dropped. `CURATED_AI_TAG_LABELS` is shared with the Search chips.
- UI: `FlowRow` of small `secondaryContainer` chips, aligned with the list items (16 dp).
  Read-only (tapping a tag does nothing yet).

## Acceptance criteria

- [x] Unit tests: label order and names, no tags.
- [x] Paparazzi `details` re-recorded with tags.
- [x] `testDebugUnitTest`, `verifyPaparazziDebug`, `ktlintCheck`, `lintDebug`, boundaries green.
- [ ] Owner: an analyzed photo shows its `ai/*` tags on top of Details.

## Possible follow-up

Tapping a tag could open Search filtered by it (the chips there already filter by tag id).
