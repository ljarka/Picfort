# T-105 — The Wall's frame card showed an old photo after a server restart

**Status:** in-progress  
**Component:** Server (`app.py`, `FrameController`)  
**Depends on:** T-072 (browser frame), T-091 (frame card on the Wall)

## Problem

After a restart the server restored the frame's photo with `version = 1`. The Wall loads
`/frame/photo?w=1080&h=720&v=<version>`, and Coil keeps decoded images in memory by URL, so a
phone that had loaded `v=1` after an earlier restart kept showing that old photo while `/frame`
in a browser (which fetches by state) showed the right one. Several deploys on 2026-11-28 made it
visible.

## Fix

After a restart the version starts at the current Unix time, so versions never repeat across
restarts (they still go up by one per photo). The app needs no change.

## Acceptance criteria

- [x] `test_survives_a_restart`: the version after a restart is new and larger than before.
- [x] Server tests green.
- [ ] Owner: after the deploy, the Wall's card matches `/frame` within a minute (the Wall polls).
