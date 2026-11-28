# ADR 0020 — Family members' own Immich API keys for the photo AI

**Status:** accepted (2026-11-28, T-104)

## Context

The photo AI (ADR 0019) works with the server's single `IMMICH_API_KEY`. Immich search returns
that user's photos and their partners' (shared into their timeline), but Immich tags belong to one
user, and only an asset's owner may tag it. So other family members got no `ai/*` tags: no Best
strip, no tag chips, and photos not shared with the key's owner were never analyzed.

## Decision

1. **One key per member, added from the app.** Settings → Timeline → "Photo AI for my photos"
   creates an Immich API key with the member's own session (`POST /api/api-keys`), with only
   `asset.read`, `asset.view`, `asset.statistics`, `tag.read`, `tag.create`, `tag.asset`, `user.read`,
   and sends it to `PUT /photo-insights/my-library`. "Turn off" deletes it on the server and in Immich.
2. **The server checks and keeps it.** The key must belong to the caller (`GET /api/users/me`), is
   stored in `photo_ai.db` (`library_keys`) next to the existing `.env` key, and is never returned
   by any endpoint. The dashboard shows names and counts only.
3. **Libraries take turns.** The worker serves the server library and each member library in turn;
   a member library picks only that member's photos. Each photo is analyzed once (`owner_id`
   recorded); tags are applied with the owner's key, and photos analyzed earlier through a
   partner's key are tagged when their owner's key arrives (no second analysis).
4. **Labels follow access.** `/photo-insights/labels` returns the caller's photos and those of
   Immich partners sharing with them (looked up with the caller's token, cached 10 min).
5. `IMMICH_API_KEY` stays: the frame, albums, health check and the server's own library need it.

## Consequences

- Every member gets tags in their own Immich and the app features built on them.
- More photos for one Ollama: libraries are covered in turn, so a full pass takes longer.
- The server holds more keys; they are narrow (no download, no delete, no albums) and revocable
  in Immich (Settings → API keys, "Picfort photo AI").
