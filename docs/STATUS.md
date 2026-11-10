# Picfort — Roadmap & Status

Last updated: 2026-11-10

## Vision

Privacy-first family photo ecosystem. Unified timeline of Immich + phone photos,
picture frame control, and a shared Family Wall — all without any cloud service.

---

## Phases

### Phase 1 — Family Wall (new major feature)
The defining feature that sets Picfort apart from every other Immich client.
A shared digital board for the family: notes, photos, fridge-magnet-style messages.

| Task | Description | Status |
|------|-------------|--------|
| [T-001](tasks/T-001-family-wall-backend.md) | Family Wall — ImageFrameServer backend | `done` |
| [T-002](tasks/T-002-family-wall-android.md) | Family Wall — Android screen (approved mockup, members = Immich users) | `in-progress` |
| [T-003](tasks/T-003-frame-mode-tablet.md) | Frame mode on Android tablet (parent's account, settings from the Picfort server) | `ready` |

### Phase 2 — Fix known limitations
Gaps documented in the README that affect day-to-day use.

| Task | Description | Status |
|------|-------------|--------|
| [T-004](tasks/T-004-phone-only-to-frame.md) | Send phone-only photos to frame | `ready` |
| [T-005](tasks/T-005-backup-album-filter.md) | Backup: filter by album | `ready` |
| [T-006](tasks/T-006-backup-foreground-service.md) | Backup: fix Android 12+ 10-min background limit | `ready` |

### Phase 3 — Content discovery
Surface more of what Immich already knows.

| Task | Description | Status |
|------|-------------|--------|
| [T-007](tasks/T-007-memories-screen.md) | Memories screen ("On This Day") | `ready` |
| [T-008](tasks/T-008-people-screen.md) | People screen (browse by face) | `ready` |
| [T-009](tasks/T-009-map-view.md) | Map view (photos by location) | `ready` |
| [T-015](tasks/T-015-timeline-sync-stream.md) | Timeline from Immich's sync stream (local copy, offline) | `draft` |
| [T-016](tasks/T-016-justified-timeline-layout.md) | Justified timeline layout from known aspect ratios (after T-015) | `done` |

### Phase 4 — Frame enhancements
Make the picture frame smarter and more connected.

| Task | Description | Status |
|------|-------------|--------|
| [T-010](tasks/T-010-bidirectional-frame-status.md) | Show live frame status in app | `ready` |
| [T-011](tasks/T-011-multi-frame.md) | Multi-frame support | `ready` |

### Phase 5 — Android ecosystem
Deeper integration with Android platform features.

| Task | Description | Status |
|------|-------------|--------|
| [T-012](tasks/T-012-android-widget.md) | Home screen widget (Glance API) | `ready` |
| [T-013](tasks/T-013-material-you.md) | Material You / dynamic color | `ready` |

### Phase 6 — Quality & CI
Automated checks and architecture guardrails.

| Task | Description | Status |
|------|-------------|--------|
| [T-014](tasks/T-014-ci-github-actions.md) | GitHub Actions: build + screenshot tests | `done` |

---

## Task statuses

- `draft` — idea captured, not yet designed
- `ready` — fully designed, an agent can implement without further questions
- `in-progress` — currently being implemented
- `done` — implemented and verified

---

## Architecture decisions

| ADR | Decision |
|-----|----------|
| [0001](decisions/0001-architecture-overview.md) | Three-tier architecture: Immich + Android + ImageFrameServer |
| [0002](decisions/0002-family-wall-storage.md) | Family Wall lives in ImageFrameServer SQLite |
| [0003](decisions/0003-display-strategy.md) | Tablet as primary display target; e-paper as secondary |
| [0004](decisions/0004-network-auth.md) | No auth on ImageFrameServer (trusted local network) |
| [0005](decisions/0005-timeline-sync-stream.md) | Timeline from a local copy synced with Immich's sync stream (proposed) |
| [0006](decisions/0006-family-wall-auth.md) | Family Wall checks the caller's Immich token (supersedes 0004 for `/wall`) |
