# T-071 — Search: filter by photos or videos

**Status:** done (pending)  
**Component:** Android app (`:feature:timeline`)  
**Depends on:** T-057

## Summary

Owner request (2026-11-14): an easy way to search only photos or only videos. Under the search
field: a segmented switch **All / Photos / Videos**.

## Design

- `MediaFilter` (`All` null, `Photos` "IMAGE", `Videos` "VIDEO") goes as Immich `type` in both
  searches: `search/smart` (text) and `search/metadata` (people only, or the type alone). Null is
  left out (the app's Json skips defaults).
- The type alone is a search: "Videos" lists every video, newest first. Switching re-runs the
  search with the text in the field; "All" with nothing else returns to the hint.
- "Nothing found" names the type: "No videos", "No videos of Ola", "Nothing found for "dog" in videos".
- The viewer list key (`source`) includes the type.

## Acceptance criteria

- [x] `maestro/search-people.yaml` (test account): Videos → video tiles; Photos → none; All → hint.
- [x] Unit tests: request `type` with text, people and alone; "nothing found" texts.
- [x] Paparazzi: search screens with the switch, `search_videos`; unit tests, ktlint, lint, boundaries green.
