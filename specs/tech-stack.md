# Picfort — Tech stack

> Part of the project constitution: [mission](mission.md) (why) · **tech stack** (how) ·
> [roadmap](roadmap.md) (what next). Decisions in detail: [ADRs](../docs/decisions/README.md).

## Shape of the system

```
Android app (Picfort) ──REST──► Immich (photos, users, faces, albums)
        │
        └────────REST─────────► Picfort server (family data, frame) ──► picture frame / browser frame
```

Three parts, all on the family's own hardware (ADR 0001). The app talks to both servers directly;
the Picfort server checks the caller's Immich token (ADR 0006).

## Android app

| Choice | Why | Tradeoff considered |
|---|---|---|
| **Kotlin + Jetpack Compose, Material 3** | Modern Android standard; fast UI work for agents. | Android only (a non-goal is iOS), so no KMP/Flutter overhead. |
| **Multi-module: `:core:*` + `:feature:*` + thin `:app`** (ADR 0011) | Each module fits in an agent's context; features never depend on each other, only on contracts in `:core:model`. | More Gradle setup and wiring in `:app`, in exchange for isolation and fast per-module tests. |
| **Hilt** | Standard DI; bridges feature contracts in `:app`. | Compile time (KSP runs one at a time, ADR 0012). |
| **Room** (`:core:database`) | Local timeline cache and backup state; offline start (ADR 0005). | Migrations required for every bump (ADR 0016). |
| **OkHttp, Retrofit, kotlinx.serialization** | Well-known, small, works with Immich's and our REST APIs. | — |
| **Coil 3, Media3** | Images and video in the timeline and viewer. | — |
| **WorkManager + AlarmManager** | Backup, periodic re-plans, reminders on the phone (ADR 0015). | No instant notifications: see *Notifications* below. |
| **ML Kit GenAI Prompt (Gemini Nano)** | Receipt reading on the phone, no cloud (ADR 0009). | Only on phones with AICore; others type by hand. |
| **Offline copies** (JSON files / Room) | The shop and the school run often have no connection to home. | Read-only offline; edits wait for the connection. |

minSdk 26, compileSdk 37, JDK 21.

## Picfort server (`picfort-server/`)

| Choice | Why | Tradeoff considered |
|---|---|---|
| **Python 3.11+, Flask, waitress** | Small, readable, runs on a Raspberry Pi; drives the e-paper frame. | Single process, modest throughput: fine for one family. |
| **SQLite, one file per feature** (ADR 0002, 0008) | Zero-ops, backup = copy files, fits a Pi. | No cross-feature joins, one writer at a time; Postgres was rejected as too much setup for other families. |
| **Migrations for every schema change** (ADR 0016) | Family data must survive updates. | Every change needs a migration and a test. |
| **Auth: the caller's Immich token** (ADR 0006), optional `PICFORT_API_KEY` (ADR 0007) | No second account system. | Depends on Immich being reachable. |
| **Pinned requirements, Dependabot** | Reproducible builds. | Weekly update PRs to review. |

## Hosting and deployment

- **Server:** Docker image `ghcr.io/ljarka/picfort-server`, installed with `docker/docker-compose.yml` +
  `example.env`, on any always-on machine at home (often the one running Immich). Install without
  Docker is supported for the Raspberry Pi frame.
- **App:** signed APK from GitHub releases (release workflow on a version tag).
- **Away from home:** the supported default is the home network, plus an **optional** tunnel
  (e.g. Cloudflare Tunnel) protected by `PICFORT_API_KEY` (ADR 0007). Offline copies cover the rest.

## Notifications

Phones **poll**; there is no push. Event and shopping reminders are planned on each phone
(ADR 0015), wall reactions are checked every 15 minutes (ADR 0018).
*Revisit later:* if delays start to hurt, self-hosted push (UnifiedPush/ntfy) is the path — it adds
one container and a distributor app per phone, so it needs an ADR first.

## Quality gates

Every change must pass (see the Definition of Done in [AGENTS.md](../AGENTS.md)):

- `./scripts/check-modular-boundaries.sh` — features stay isolated.
- `./gradlew ktlintCheck` (ADR 0013) and `lintDebug` (CI).
- `./gradlew testDebugUnitTest` — JUnit unit tests and **Paparazzi** screenshot tests (JVM, no device).
- Server: Python `unittest` suite, including API contract checks, on 3.11 and 3.13.
- Device checks with **Maestro** flows (`maestro/`) on the test server only, never the family's.
- CI on GitHub Actions: all of the above, a diff secret scan, and a Docker image smoke test.

## Rules for adding to the stack

- A new library, container or external service needs a reason in the task and, if it changes the
  shape of the system, an ADR.
- Nothing that sends family data to a third party (see [mission](mission.md#non-goals)).
