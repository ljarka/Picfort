# T-081 — School journal: offline copy, fake server, device test, removal check

**Status:** ready  
**Component:** Android (`:feature:school-journal`), `maestro/`, `scripts/`  
**Depends on:** T-076 … T-080. Spec §7, §8, §9.

## Steps

1. Cache `filesDir/school-journal/cache.json` (status, children, last subjects per child + semester,
   first messages page), tied to the server address, cleared on Disconnect; offline banner, read only.
2. Fake server (`maestro/scripts/fake-school-server.py`, built in T-076): extend with whatever the
   flows below need.
3. `maestro/school-journal.yaml`: Wall icon → setup → connect → messages (open, unread, read all) →
   grades (New markers) → Sync now → status line → badge gone on School; `maestro/school-journal-offline.yaml`.
4. Removal dry run on a throwaway branch: follow ADR 0017 "How to remove"; boundaries + tests
   green; record the result in the ADR (status → accepted). Add to
   `check-modular-boundaries.sh`: nothing in `core/` mentions `school-journal` or `schooljournal`.

## Done when

- Flows pass on the emulator against the fake server; removal dry run green. Docs: root
  `AGENTS.md` module map, `docs/ARCHITECTURE.md`, `feature/school-journal/AGENTS.md`.
