# app — AGENTS.md

Thin Android application shell, navigation root, and Hilt root assembler.

## Scope & Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Android activity, root `NavHost`, tab bar coordination, shared element transitions |
| `PicfortApplication.kt` | Application class, Hilt AndroidApp entry, Coil image loader init |
| `AppModule.kt` | Top-level Hilt module: binds cross-feature bridges (e.g. `ImmichUsersSync`) |
| `ui/shell/AppShell.kt` | Floating navigation bar (`TabBar`), `TabRoot`, `ComingSoonScreen` |
| `ui/init/*` | Onboarding, initial connection validation (`InitScreen`, `InitViewModel`) |
| `ui/login/*` | Immich login screen, email/password form |
| `ui/server/*` | Server address input and testing screen |
| `ui/settings/*` | App settings, server info, backup status |
| `backup/*` | Android WorkManager periodic background photo backup |

## Architecture & Rules

- `:app` is the only module that depends on all `:feature:*` modules.
- **NEVER** put feature-specific domain logic, database DAOs, or feature UI in `:app`.
- Cross-feature dependencies must be wired in `AppModule.kt` via interfaces in `:core:model`.

## Verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:verifyPaparazziDebug
```
