# T-103 — Stricter "Best": quality 5 and a selective prompt

**Status:** in-progress  
**Component:** Server (`photo_ai.py`)  
**Depends on:** T-089, T-090, T-094

## Summary

The first ~300 analyzed photos (2026-11-28) showed the model marks too many photos "Best": 62% of
real photos were frame-worthy (quality 4 and 5 both qualified; 34% of all photos got quality 5).
"Best" is meant for highlights (the timeline strip, "Best photos only" on the frame).

## Design

- `BEST_MIN_QUALITY = 5` in `tags_for_insight`, `frame_worthy_ids` and `dashboard_stats`
  (was 4): about 29% on the same photos.
- Prompt: frame-worthy only for exceptional photos worth hanging on the family's wall; "most good
  photos are NOT frame-worthy, about 1 in 10 is". Applies to photos analyzed from now on (the first
  big pass was at ~1%).
- `PhotoAiWorker.reconcile_best_tags()` on start: photos tagged `ai/best/frame-worthy` in Immich that
  no longer qualify lose the tag (`ImmichTagger.remove_tag`, `PhotoAiStore.stale_best_tags`,
  `set_tags_applied`). The app's Best cache follows at its next daily refresh.

## Acceptance criteria

- [x] Tests: quality 4 is no longer Best; reconcile untags once; prompt asks for selectivity.
- [x] Server tests green.
- [ ] Owner: after a few hundred more photos, the share of Best on `/dashboard` is near 10–15%.
