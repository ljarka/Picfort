# Picfort — Roadmap & Status

Last updated: 2026-11-11

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
| [T-002](tasks/T-002-family-wall-android.md) | Family Wall — Android screen (approved mockup, members = Immich users) | `done` |
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
| [T-015](tasks/T-015-timeline-sync-stream.md) | Timeline from Immich's sync stream (local copy, offline; code merged, check on a device) | `in-progress` |
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
| [T-013](tasks/T-013-material-you.md) | Material You / dynamic color & visual rebranding | `done` |

### Phase 6 — Quality & CI
Automated checks and architecture guardrails.

| Task | Description | Status |
|------|-------------|--------|
| [T-014](tasks/T-014-ci-github-actions.md) | GitHub Actions: build + screenshot tests | `done` |

### Phase 7 — Family hub
The app opens on the Family Wall; bottom tabs Wall · Shopping · Events · School · Photos.
Shared shopping lists with receipts and spending, a family events timeline linked to
Immich albums, and a school timetable per child. Data on the Picfort server (ADR 0008).
Receipts can be read by Google Gemini (ADR 0009): opt-in on the server, the one exception to "without any cloud service".

Delivery order (each milestone is usable on its own):
- **M1 shell:** T-017.
- **M2 lists:** T-018 → T-019.
- **M2b family profiles:** T-031 → T-032 (names, photos from Immich people, hidden accounts;
  after T-018). Small, and the wall is the home screen, so it goes early.
- **M3 events:** T-022 → T-023 → T-026 (albums), T-027 (today panel on the wall).
- **M3b members, birthdays, recurring events** (ADR 0010): T-033 → T-034 (add people, birth dates),
  T-035 (recurring, server) → T-036 (birthday events, server) → T-037 (Android). T-026 (albums) is independent.
- **M4 school timetable:** T-028 → T-029 → T-030 (children from Immich people).
- **M5 receipts & spending:** T-020 → T-024 (Gemini) → T-021 → T-025.

Events and school before receipts: no external dependency, daily use, and receipts are the
largest block. M3 and M4 are independent of each other and can swap. Server tasks T-020,
T-022, T-028 and T-031 need T-018's foundations (`store.py`, token auth for the new paths). Every
Android task except T-017 and T-026 starts with a mockup the owner approves.

| Task | Description | Status |
|------|-------------|--------|
| [T-017](tasks/T-017-app-shell-bottom-nav.md) | App shell: Wall as home, bottom navigation (5 tabs) | `done` |
| [T-018](tasks/T-018-shopping-lists-backend.md) | Shopping lists — server API, `shopping.db`, shared store/auth foundations | `done` |
| [T-019](tasks/T-019-shopping-lists-android.md) | Shopping lists — Android, Keep-like checklist, read-only offline copy | `done` |
| [T-020](tasks/T-020-receipts-backend.md) | Receipts + `/spending` — server, totals typed by hand | `ready` |
| [T-021](tasks/T-021-receipts-android.md) | Receipts — Android: add to a list, check, correct | `ready` |
| [T-022](tasks/T-022-events-backend.md) | Events — server API (`events.db`) | `done` |
| [T-023](tasks/T-023-events-android.md) | Events — Android: upcoming / past, add, edit | `done` |
| [T-024](tasks/T-024-receipt-reading-gemini.md) | Receipts — read shop, date, total with Gemini (server, opt-in) | `ready` |
| [T-025](tasks/T-025-spending-summary-android.md) | Spending summary — Android: month, 6-month bars, by shop and person | `ready` |
| [T-026](tasks/T-026-event-albums-android.md) | Events — link or create a family-shared Immich album (Android) | `ready` |
| [T-027](tasks/T-027-wall-today-panel.md) | Family Wall — today panel: date and the day's event | `done` |
| [T-028](tasks/T-028-school-timetable-backend.md) | School timetable — server API (`school.db`): children, bell schedule, lessons, activities | `ready` |
| [T-029](tasks/T-029-school-timetable-android.md) | School timetable — Android: child chips, "finishes at", week by day, editing | `ready` |
| [T-030](tasks/T-030-children-immich-people.md) | Children from Immich people: face photo, per-parent link, "Photos of Ola" | `ready` |
| [T-031](tasks/T-031-family-profiles-backend.md) | Family profiles — server (`family.db`): display name, photo, hidden, per Immich user | `done` |
| [T-032](tasks/T-032-family-settings-android.md) | Family settings — Android: rename, photo from Immich people, hide; one source of members | `done` |
| [T-033](tasks/T-033-family-members-birthdays-server.md) | Family members without Immich + birth dates — server (`family.db` v2) | `done` |
| [T-034](tasks/T-034-family-add-members-birthday-android.md) | Family settings — Android: add people, Immich badge, birth date | `done` |
| [T-035](tasks/T-035-recurring-events-server.md) | Recurring events — server (weekly / monthly / yearly, end date; `events.db` v2) | `done` |
| [T-036](tasks/T-036-birthday-events-server.md) | Birthdays as events — server (`events.db` v3) | `done` |
| [T-037](tasks/T-037-events-repeat-birthdays-android.md) | Events — Android: repeat, birthdays with age | `done` |
| [T-038](tasks/T-038-visual-rebranding-and-icon.md) | Visual rebranding — app icon, warm palette & floating nav bar | `done` |
| [T-039](tasks/T-039-event-reminders-android.md) | Event & birthday reminders — Android notifications | `ready` |
| [T-040](tasks/T-040-shopping-reminders-android.md) | Shopping list reminders — Android notifications | `ready` |
| [T-041](tasks/T-041-wishlist-backend.md) | Wishlist & link preview — server API (`shopping.db` v2) | `ready` |
| [T-042](tasks/T-042-wishlist-android.md) | Wishlist carousel & event linking — Android | `ready` |
| [T-052](tasks/T-052-photos-family-carousel.md) | Photos — family carousel, photos of one person (per-parent link to Immich people) | `in-progress` |
| [T-053](tasks/T-053-events-who.md) | Events — who the event is for (family members on an event) | `done` |
| [T-054](tasks/T-054-events-member-filter.md) | Events — filter the list by family member | `done` |
| [T-055](tasks/T-055-birthday-photos.md) | Events — photos from past birthdays in a birthday's detail | `done` |
| [T-056](tasks/T-056-birthday-year-to-timeline.md) | Events — a birthday's year opens Photos on that day | `done` |
| [T-057](tasks/T-057-search-by-people.md) | Photos — search by Immich people | `done` |

### Phase 8 — Modular Architecture (Agentic Pair Programming)
Deconstruct monolithic `:app` into isolated feature slices and core foundations (ADR 0011).
Enables AI agents to operate within compact, focused context windows with per-module `AGENTS.md`
and fast isolated snapshot testing (`./gradlew :feature:<name>:verifyPaparazziDebug`).

| Task | Description | Status |
|------|-------------|--------|
| [T-043](tasks/T-043-modularization-foundation-core-model.md) | Build-logic convention plugins & pure domain `:core:model` | `done` |
| [T-044](tasks/T-044-modularization-core-ui.md) | Design system & shared UI components `:core:ui` | `done` |
| [T-045](tasks/T-045-modularization-core-network-database.md) | Network infrastructure `:core:network` & persistence `:core:database` | `done` |
| [T-046](tasks/T-046-modularization-feature-shopping.md) | Feature slice `:feature:shopping` (lists, offline copy, local snapshots) | `done` |
| [T-047](tasks/T-047-modularization-feature-events.md) | Feature slice `:feature:events` (upcoming, form, detail, local snapshots) | `done` |
| [T-048](tasks/T-048-modularization-feature-family.md) | Feature slice `:feature:family` (settings, members, birth dates, local snapshots) | `done` |
| [T-049](tasks/T-049-modularization-feature-wall.md) | Feature slice `:feature:wall` (dashboard, today panel, member chips, local snapshots) | `done` |
| [T-050](tasks/T-050-modularization-feature-timeline-frame.md) | Feature slices `:feature:timeline` & `:feature:frame` | `done` |
| [T-051](tasks/T-051-modularization-app-shell-cleanup.md) | Thin `:app` shell, root navigation assembly & architecture boundary guard | `done` |

Decided by the owner (2026-11-11), as recommended:
- **Schema changes** in `shopping.db` / `events.db`: no migrations; the old file is kept as
  `.bak` and the feature starts empty (ADR 0008).
- **Shopping away from home:** a connection is not assumed; T-019 keeps a read-only copy of
  the lists on the phone; checking items off offline is v2.
- **Deleting** a list, event or receipt: the creator only; anyone can edit, and archive a list.
- **Currency:** one for the whole server (`CURRENCY=PLN`); a receipt from abroad is typed in PLN.
- **Today panel without an event today** (T-027): "Next: …" for an event within 30 days,
  otherwise only the date.
- **"Ola finishes at 13:40" in the wall's today panel:** not in T-027 or T-029; a small
  follow-up task once both are done.
- **Family members beyond Immich, birthdays, recurring events** (ADR 0010, owner request 2026-11-11): manual
  members live in `family.db`; a birth date creates a yearly birthday event; events repeat weekly, monthly
  or yearly with an optional end date; `family.db` and `events.db` get additive migrations (ADR 0008's
  "no migrations" no longer applies to them).
- **Children are not linked to Immich users;** the server keeps an unused `immich_user_id`
  column so a later link needs no schema change.
- **Children can be chosen from Immich people** (face recognition), with the face as their
  photo, and each parent can browse the child's photos from their own library (T-030). The
  link is per parent, because Immich people are per user.
- **Family members can be renamed, get a photo from Immich people, and be hidden** (T-031,
  T-032). It is family-wide configuration: anyone can change it for any member. The "test" in
  the name rule (PR #37) stays as the default for members without a setting.
- **Deleting a child** (and its timetable): anyone, after a confirmation (either parent must
  be able to).
- **Five tabs:** Wall · Shopping · Events · School · Photos. It is the Material maximum: a sixth
  section later needs a "More" tab or moving something out.

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
| [0004](decisions/0004-network-auth.md) | No auth on the frame endpoints (trusted local network; see 0006, 0007) |
| [0005](decisions/0005-timeline-sync-stream.md) | Timeline from a local copy synced with Immich's sync stream |
| [0006](decisions/0006-family-wall-auth.md) | Family Wall checks the caller's Immich token (supersedes 0004 for `/wall`) |
| [0007](decisions/0007-server-api-key.md) | Optional `PICFORT_API_KEY` for a server reachable from outside (e.g. Cloudflare Tunnel) |
| [0008](decisions/0008-family-hub-storage.md) | Shopping lists, receipts, events, school timetables on the Picfort server, one SQLite file per feature, `.bak` on schema change (proposed) |
| [0009](decisions/0009-receipts-gemini.md) | Receipts read by Gemini free tier on the server, opt-in (proposed) |
| [0010](decisions/0010-family-members-birthdays-recurring-events.md) | Manual family members, birth dates & recurring events with additive migrations |
| [0011](decisions/0011-modular-architecture.md) | Multi-module architecture optimized for AI Agent pair programming |
