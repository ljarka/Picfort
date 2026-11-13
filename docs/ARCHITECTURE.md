# Architecture Overview

Picfort is a privacy-first family photo ecosystem built entirely on self-hosted infrastructure.
No Google Photos, no iCloud, no Amazon — everything runs at home. (Planned exception: receipt
photos read by Gemini, opt-in, [ADR 0009](decisions/0009-receipts-gemini.md).)

ImageFrameServer is called the **Picfort server** in the app and the README.

## Components

```
┌─────────────────────────────────────────────────────────────────┐
│  Home network                                                   │
│                                                                 │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────────┐  │
│  │  Android app │    │    Immich    │    │ ImageFrameServer │  │
│  │   (Picfort)  │◄──►│  (NAS/VPS)  │    │  (Raspberry Pi)  │  │
│  └──────┬───────┘    └──────────────┘    └────────┬─────────┘  │
│         │                                         │            │
│         └─────────────── REST ───────────────────►│            │
│                                                   │            │
│                                          ┌────────▼─────────┐  │
│                                          │  Display device  │  │
│                                          │  (tablet / e-ink)│  │
│                                          └──────────────────┘  │
└─────────────────────────────────────────────────────────────────┘
```

## Component Responsibilities

### Android app (`app/`)
- Browse photos: unified timeline (Immich + phone MediaStore). Immich assets come from a local
  Room copy kept up to date with Immich's sync stream (ADR 0005, T-015)
- Backup phone photos to Immich via WorkManager
- Control the picture frame (send photo, configure slideshow)
- Family Wall: post and read messages/photos shared with family

Tech stack: Kotlin, Jetpack Compose, Room, Hilt, Retrofit, WorkManager, Media3

### Immich
External self-hosted photo server. Picfort uses its REST API for:
- Timeline, albums, search, people, memories
- Asset management (share, delete, download)
- Backup upload

Picfort does not modify Immich. It is a pure API consumer.

### ImageFrameServer (`ImageFrameServer/`)
Python Flask service, installed with Docker Compose on any always-on machine (preview mode,
no panel) or directly on a Raspberry Pi that drives an e-paper panel. Responsibilities:
- Receive a photo from the app and display it on the connected screen
- Run an autonomous slideshow (album, memories, random modes)
- Serve the Family Wall: store and serve posts (text + photos)
- Expose a REST API consumed by the Android app

Tech stack: Python, Flask, SQLite, Pillow, Waveshare e-paper driver (optional)

### Display device
One of:
- Waveshare e-paper panel connected to the Raspberry Pi via SPI
- Android tablet in kiosk/frame mode running Picfort directly
- Any HDMI display connected to the Raspberry Pi

## Data flows

### Photo display (manual)
```
App ──POST /run?assetId=X──► ImageFrameServer ──► fetch from Immich ──► render ──► display
```

### Family Wall post
```
App ──POST /wall + Authorization: Bearer <Immich token>──► ImageFrameServer
        ImageFrameServer ──GET /api/users/me (cached 5 min)──► Immich   (who is posting)
        ImageFrameServer ──► SQLite (wall.db) + wall_photos/
App ──GET /wall (poll 60 s while the screen is open)──► posts
```

### Backup
```
Phone MediaStore ──► WorkManager ──► Immich REST API
```

## Key design principles

1. **Home network first, tunnel optional** — the Picfort server is meant for the home network
   (or a VPN). It may be exposed through a tunnel such as Cloudflare Tunnel only with
   `PICFORT_API_KEY` set ([ADR 0007](decisions/0007-server-api-key.md)).
2. **Layered auth on the Picfort server:**
   - Frame endpoints (`/run`, `/status`, `/slideshow`, `/next`, `/albums`, `/current`): no
     auth on the home network ([ADR 0004](decisions/0004-network-auth.md)).
   - Family Wall (`/wall`): the caller's Immich token, checked with Immich; it also says who
     the author is ([ADR 0006](decisions/0006-family-wall-auth.md)).
   - With `PICFORT_API_KEY` set: every request also needs `X-Picfort-Key` (ADR 0007).
   - The server's own Immich API key (read-only: `asset.read`, `asset.view`,
     `asset.download`, `album.read`) is used only to fetch photos for the frame.
3. **Immich is the source of truth for photos** — Picfort never duplicates photo storage. The
   Picfort server owns only family data Immich has no place for: Family Wall posts and photos
   today, and (Phase 7, [ADR 0008](decisions/0008-family-hub-storage.md)) shopping lists,
   receipts, events and school timetables.
4. **Polling over WebSocket** — polling while a screen is open (60 s for the wall) is
   sufficient for home use and avoids persistent connections.

## AI-agent threat model and controls

Beyond runtime app security, Picfort also treats **agent workflow** as a security surface.

### Main risks

1. **Prompt injection in repository content** — instructions hidden in comments, generated files,
   or copied snippets that try to change how tooling is used.
2. **Secret leakage during large edits** — accidental addition of tokens/keys in generated code,
   tests, or CI snippets.
3. **Unbounded blast radius** — broad cross-module edits that silently bypass intended review depth.
4. **Contract drift** — endpoint/path/shape changes that compile but break app ↔ server integration.

### Controls in this repository

- **Scope guardrail in CI:** `scripts/check-agent-change-scope.sh` blocks oversized, cross-module
  diffs (with explicit override).
- **Diff secret scan in CI:** `scripts/check-no-secrets-in-diff.sh` scans added lines for
  high-confidence credential patterns.
- **Critical-path ownership gates:** `.github/CODEOWNERS` requires owner review for navigation,
  networking/security, workflows, architecture decisions, and guardrail scripts.
- **Contract checks for the server API:** `ImageFrameServer/tests/test_api_contract.py` snapshots
  route methods and key response shapes so accidental API drift fails tests.

These controls complement existing runtime protections (API key, token-based wall auth, private
cache headers) and reduce both accidental and malicious misuse in AI-assisted changes.
