# app — AGENTS.md

Thin Android application shell, navigation root, and Hilt root assembler.

## Scope & Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Android activity, root `NavHost`, tab bar coordination, shared element transitions |
| `AppNavigation.kt` | Navigation contracts and tab graph mapping (`Screen`, `TabGraph`, `Tab.graph`, `Tab.root`) |
| `PicfortApplication.kt` | Application class, Hilt AndroidApp entry, Coil image loader init |
| `AppModule.kt` | Top-level Hilt module: binds cross-feature bridges (e.g. `ImmichUsersSync`) |
| `ui/shell/AppShell.kt` | Floating navigation bar (`TabBar`), `TabRoot`, `ComingSoonScreen`; `Tab.accent` (Wall coral, Shopping mint, Events amber, School sky, Photos periwinkle), applied in `MainActivity` via `AccentTheme` |
| `ui/init/*` | `InitScreen` (signed in → app) and the one-page setup (T-058): `SetupScreen`/`SetupContent`, `SetupViewModel` (Immich ping → login → `PicfortServerCheck`), `picfortSuggestion` (same host :5000) |
| `ui/login/*` | `LoginService` (`/api/auth/login`, via `Provider`: Retrofit reads the base URL when built) |
| `ui/settings/*` | App settings, server info, backup status; `AppearanceSettingsStore` ("Colors from wallpaper", off by default → brand palette) |
| `backup/*` | Android WorkManager periodic background photo backup |

## Architecture & Rules

- `:app` is the only module that depends on all `:feature:*` modules.
- **NEVER** put feature-specific domain logic, database DAOs, or feature UI in `:app`.
- Cross-feature dependencies must be wired in `AppModule.kt` via interfaces in `:core:model`.
- Family settings need the Picfort server: without one (`ServerStatusViewModel.configured`, rechecked on start)
  Photos gets no "+ Family" circle, long-press "Edit" or "Link"; Settings hides "Family" (`FamilyRepository.supported`).
- `Screen.Timeline(date)`: a birthday's year opens Photos on that day on top of the event (Back returns
  to it); a tab switch from there pops it without `saveState`, so it never ends up in another tab's stack (T-056).

## Verification

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:verifyPaparazziDebug
./scripts/maestro-smoke.sh   # on a device: clean install, login, every tab (maestro/smoke.yaml)
./scripts/maestro-smoke.sh maestro/setup-errors.yaml   # T-058: setup errors, https:// default, Skip
```
