# ADR 0019 — Optional Server-Side Ollama Photo Insights & Immich Tagging

**Status:** accepted (2026-11-28)

## Context

Immich provides face detection, CLIP semantic search, and OCR, but does not assess technical photo quality (blur, dark/overexposed, closed eyes, accidental shots), aesthetic frame-worthiness, or curated cleanup candidates. Running a Vision LLM on each phone would drain battery, duplicate work across family members, and require configuring LLM endpoints on every device.

## Decision

1. **Server-only LLM configuration:** Ollama lives exclusively on `picfort-server` via optional environment variables (`OLLAMA_URL`, `OLLAMA_MODEL`, `OLLAMA_BATCH_SIZE`, `OLLAMA_INTERVAL_MINUTES`, `OLLAMA_ACTIVE_HOURS`, `OLLAMA_TAG_IMMICH`). When `OLLAMA_URL` or `OLLAMA_MODEL` is unset, the worker is completely disabled.
2. **Local SQLite cache (`photo_ai.db`):** Structured results (`scene`, `category`, `is_nature`, `quality`, `issues`, `frame_worthy`, `cleanup_candidate`, `cleanup_reason`, `dismissed`) are stored in `photo_ai.db` using `SqliteStore` with schema migrations (ADR 0016). The JSON schema puts `scene` first so the vision model describes the image before classifying it.
3. **Never auto-delete:** The LLM only flags `cleanup_candidate` items. Deletion always requires explicit user action in the app (moving photos to Immich's reversible trash) or directly in Immich.
4. **Optional hierarchical Immich tags (`ai/*`):** When `OLLAMA_TAG_IMMICH=true` and `IMMICH_API_KEY` has `tag.read`, `tag.create`, and `tag.asset`, the server mirrors useful labels into Immich's native tag tree (`ai/cleanup/*`, `ai/type/*`, `ai/best/frame-worthy`) so the curated metadata stays useful in Immich itself. Ordinary photos receive no tags. Dismissing a cleanup candidate in Picfort removes `ai/cleanup/*` tags from that asset in Immich.
5. **Android app is LLM-agnostic:** The app never talks to Ollama or exposes LLM settings. It only consumes `/photo-insights/*` from `picfort-server` to show a **"Clean up (N)"** entry point on the Photos timeline when candidates exist.

## Consequences

- Zero impact on installs without Ollama.
- Picture frame slideshow (`slideshow.py`) automatically benefits from `photo_ai.db` by skipping low-quality/non-photo assets.
- Library cleanup done in Picfort cleans the real Immich library via Immich trash.

## Amendment (2026-11-28, T-093)

- Photos marked `is_sensitive` never go to the frame, in any mode and whatever `filter_photos` is.
- One photo's failed analysis is counted in `photo_failures` and skipped after 3 attempts; an
  unreachable Ollama/Immich only stops the batch. The worker covers the whole library through a
  stored scan page (`worker_state`).
- Deleted `ai/*` tags are re-created by the server the next time it tags a photo.
- "Best" (`ai/best/frame-worthy`) needs quality 5 and the prompt asks for about 1 in 10 photos;
  photos tagged under the older rule lose the tag when the worker starts (T-103).
