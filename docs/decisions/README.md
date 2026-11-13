# Architecture decision records (ADRs)

Each file in this directory documents one major technical decision.

## Index

| ADR | Topic |
|---|---|
| [0001](0001-architecture-overview.md) | Three-tier architecture: Immich + Android + ImageFrameServer |
| [0002](0002-family-wall-storage.md) | Family Wall storage in ImageFrameServer SQLite |
| [0003](0003-display-strategy.md) | Tablet-first display strategy; e-paper as secondary |
| [0004](0004-network-auth.md) | Frame endpoints open on trusted home network |
| [0005](0005-timeline-sync-stream.md) | Timeline from a local copy synced from Immich stream |
| [0006](0006-family-wall-auth.md) | Family Wall requires Immich token validation |
| [0007](0007-server-api-key.md) | Optional `PICFORT_API_KEY` for externally reachable server |
| [0008](0008-family-hub-storage.md) | Family-hub feature storage on Picfort server |
| [0009](0009-receipts-gemini.md) | Optional Gemini-based receipt reading on server |
| [0010](0010-family-members-birthdays-recurring-events.md) | Manual members, birthdays, recurring events with additive migrations |
| [0011](0011-modular-architecture.md) | Multi-module architecture for focused, agent-friendly development |
| [0012](0012-serial-ksp.md) | One KSP task at a time (KSP2 in parallel left modules without Hilt code) |
| [0013](0013-ktlint.md) | ktlint for Kotlin code style, checked in CI |
