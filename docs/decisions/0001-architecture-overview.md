# ADR 0001 — Three-tier architecture

**Status:** accepted  
**Date:** 2026-11-08

## Context

Picfort needs to serve three distinct use cases from a single codebase:
1. A mobile photo browser and backup tool
2. A picture frame controller
3. A family communication board

These use cases involve three separate runtime environments: an Android phone,
a Linux server attached to a display, and a shared Immich photo server.

## Decision

Adopt a three-tier architecture:

- **Android app** — UI and user interaction; consumes Immich API and ImageFrameServer API
- **Immich** — external, self-hosted; source of truth for photos; not modified by Picfort
- **ImageFrameServer** — Python Flask service; owns the display and the Family Wall data

Each tier has a single, clear responsibility. No tier duplicates another's data
except for Room cache (intentional offline-first pattern).

## Consequences

- The Android app never stores permanent photo data — Room is a cache only.
- ImageFrameServer is the only service Picfort owns and deploys.
- Adding a new display type (e.g. second frame) means adding a second ImageFrameServer instance, not changing the app.
- If Immich changes its API, only the `network/` layer of the app changes.
