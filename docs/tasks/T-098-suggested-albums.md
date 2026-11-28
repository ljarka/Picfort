# T-098 — Suggested albums from location/date clusters & Ollama naming

**Status:** done (`7f830aa`)  
**Component:** Picfort server (`immich.py`, `photo_ai.py`, `app.py`), Android (`:feature:timeline`)  
**Depends on:** T-089

## Summary

Automatically suggest photo albums from unassigned Immich photos grouped by location and date proximity, named via local Ollama, and displayed at the top of the **Albums** screen for one-tap creation or dismissal.

## Design

- **Server (`picfort-server`):**
  - `ImmichClient.unassigned_images()` queries `POST /api/search/metadata` with `isNotInAlbum: True, withExif: True`.
  - `cluster_album_candidates()` groups camera photos (skipping screenshots/non-EXIF) by city + date gap (≤ 3 days) or single-day bursts (≥ 12 photos) with deterministic `cluster_key`.
  - `OllamaAnalyzer.name_album()` runs a fast text-only `/api/chat` call (using location, dates, photo count, and any cached `scene` descriptions) to suggest a warm album title and filter mundane clutter (`worth_album`).
  - `PhotoAiStore` schema v4 adds `album_suggestions` (`MIGRATIONS[3]`, ADR 0016), picks the best cover photo (`frame_worthy`, highest `quality`), and persists `pending` / `created` / `dismissed` state.
  - Endpoints: `GET /photo-insights/album-suggestions`, `POST /photo-insights/album-suggestions/<cluster_key>/dismiss`, `POST /photo-insights/album-suggestions/<cluster_key>/created`.
- **Android (`:feature:timeline`):**
  - `PhotoInsightsClient` fetches pending suggestions and posts dismiss/created actions.
  - `AlbumsScreen` renders a **"Suggested albums"** horizontal row above the album grid (hidden when empty) with editable title dialog, **Create** (creates real Immich album via `CollectionsRepository.createAlbum`), and **Dismiss**.
