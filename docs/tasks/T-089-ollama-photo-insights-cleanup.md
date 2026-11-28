# T-089 — Ollama photo insights, Immich `ai/*` tagging & Clean up screen

**Status:** done (`5d2fd24`)  
**Component:** Picfort server (`photo_ai.py`, `immich.py`, `slideshow.py`, `app.py`), Android (`:feature:timeline`, `:app`)  
**Depends on:** T-088  
**ADR:** [0019](../decisions/0019-ollama-photo-insights.md)

## Summary

Add an optional background Vision-LLM worker on `picfort-server` (configured via `OLLAMA_*` env vars) that analyzes Immich photo previews into `photo_ai.db`, optionally applies hierarchical `ai/*` tags in Immich, improves frame slideshow filtering, and powers a **"Clean up"** review screen reachable from the Photos timeline.

## Tag taxonomy (`ai/*`)

- `ai/cleanup/low-quality` (`quality <= 2`)
- `ai/cleanup/blurry` (`"blurry"` in `issues`)
- `ai/cleanup/accidental` (`category == "accidental"` or `"accidental"` in `issues`)
- `ai/cleanup/closed-eyes` (`"closed_eyes"` in `issues`)
- `ai/type/screenshot`, `ai/type/document`, `ai/type/receipt`, `ai/type/meme`, `ai/type/kids-art`, `ai/type/nature` (`is_nature == true`)
- `ai/best/frame-worthy` (`frame_worthy == true` and `category == "photo"`)

Ordinary photos get zero tags. Dismissing a candidate ("Keep") removes `ai/cleanup/*` tags in Immich and marks `dismissed = 1` in `photo_ai.db`.

## Acceptance criteria

- [x] `picfort-server/photo_ai.py`: `PhotoAiStore` (`photo_ai.db`, `SqliteStore`), `PhotoAiWorker`, Ollama client with `scene`-first JSON schema, optional Immich `ai/*` tagging.
- [x] `picfort-server/app.py`: `/photo-insights/status`, `/photo-insights/cleanup`, `/photo-insights/<id>/dismiss`, `DELETE /photo-insights/<id>`.
- [x] `picfort-server/slideshow.py`: skip `photo_ai` low-quality / cleanup / non-photo assets when `filter_photos=True`.
- [x] Android `:feature:timeline`: `PhotoInsightsClient` (via `PicfortServerClient`), **"Clean up (N)"** chip on `TimelineScreen` (hidden when server has no candidates or isn't configured), `CleanupScreen` with All / Low quality / Screenshots & docs filters, full-screen preview tap, **Keep** and **Move to trash** actions.
- [x] Unit tests for server (`test_photo_ai.py`) and Android + ktlint + modular boundaries + Paparazzi green.
