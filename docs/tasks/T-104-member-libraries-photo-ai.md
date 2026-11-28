# T-104 — Photo AI for every family member's own library

**Status:** in-progress  
**Component:** Server (`photo_ai.py`, `app.py`, `auth.py`, `immich.py`, `dashboard`), Android (`:feature:timeline`)  
**Depends on:** T-089, T-094, T-102; decision: [ADR 0020](../decisions/0020-member-immich-keys-for-photo-ai.md)

## Summary

Each family member turns the photo AI on for their own Immich library with one tap in
Settings → Timeline ("Photo AI for my photos"). Their photos are then analyzed and tagged in their
own Immich, so the Best strip, tag chips and Search tags work for them too.

## Design (see ADR 0020)

- **Server:** `photo_ai.db` schema 5: `photo_insights.owner_id`, `library_keys`. Endpoints
  `GET/PUT/DELETE /photo-insights/my-library` (Immich token; the key must be the caller's; never
  returned). Worker: `libraries()` = server + members, one per batch (`_turn`); a member library
  only picks its owner's assets (`ownerId`); tagging with the owner's tagger (`_tagger_for`);
  `_tag_backlog` tags a member's photos analyzed earlier through a partner's key; rows from before
  learn their owner while scanning (`fill_owners`). Per-library scan cursors. `/photo-insights/labels`
  filtered to the caller and their Immich partners (`immich_partners`, cached 10 min). Dashboard
  lists libraries (names, counts, last errors).
- **App:** `CollectionsService.createApiKey/deleteApiKey`; `PhotoInsightsClient.myLibrary/
  addMyLibrary/removeMyLibrary`; `TimelineSettingsViewModel.enableMyLibrary` (creates the key,
  hands it over, deletes it again if the server refuses) and `disableMyLibrary`. States: off
  ("Turn on"), on ("N of M photos analyzed", last problem, "Turn off"), covered by the server's key.

## Acceptance criteria

- [x] Server tests (`test_member_libraries.py`): add/remove key, key must be the caller's, server
      owner covered without a key, libraries in turn with owner-only tagging and backlog, labels
      per owner and partners, schema 4 → 5 migration.
- [x] Paparazzi `timeline_settings_my_library_off/on`.
- [x] All checks green.
- [ ] Device: the enable flow needs a test server with this build (the test server runs an old
      image); owner checks it on the family setup with a second account.
