# T-090 — Immich tag chips in Search & "Best photos only" frame mode

**Status:** done (`b3efdd7`)  
**Component:** Picfort server (`photo_ai.py`, `slideshow.py`, `app.py`), Android (`:feature:timeline`, `:feature:frame`)  
**Depends on:** T-089

## Summary

Make the AI-generated `ai/*` tags (and user-created Immich tags) directly actionable in the app:
1. **Search (`:feature:timeline`):** quick filter chips below `All / Photos / Videos` backed by `GET /api/tags` and `tagIds` on `/api/search/metadata` and `/api/search/smart` — curated order (`Best`, `Nature`, `Kids' art`, `Receipts`, `Documents`, `Screenshots`, `Memes`, omitting `ai/cleanup/*`) followed by user tags. Combinable with people, text queries, and media type.
2. **Frame (`picfort-server` + `:feature:frame`):** **"Best photos only"** switch (`best_only` in `SlideshowSettings`) in `random` and `memories` modes that picks from AI-curated `frame_worthy_ids()` (falling back to normal filtered random if none are analyzed yet).

## Verification

- `picfort-server`: 189 unit tests pass (`unittest discover -s tests`).
- Android: `./scripts/check-modular-boundaries.sh`, `./gradlew ktlintCheck`, `:feature:timeline:testDebugUnitTest`, `:feature:frame:testDebugUnitTest`, `:feature:timeline:verifyPaparazziDebug`, `:feature:frame:verifyPaparazziDebug` all green.
