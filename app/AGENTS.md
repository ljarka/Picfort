# app — AGENTS.md

Thin Android application shell, navigation root, and Hilt root assembler.

## Scope & Files

| File | Purpose |
|---|---|
| `MainActivity.kt` | Android activity, root `NavHost`, tab bar coordination, shared element transitions |
| `AppNavigation.kt` | Navigation contracts and tab graph mapping (`Screen`, `TabGraph`, `Tab.graph`, `Tab.root`) |
| `PicfortApplication.kt` | Application class, Hilt AndroidApp entry, Coil image loader init |
| `network/*` | OkHttp/Retrofit; `AuthInterceptor` (token for Immich and its home address), `DebugLoggingInterceptor` (no bodies for uploads/downloads) |
| `AppModule.kt` | Top-level Hilt module: binds cross-feature bridges (e.g. `ImmichUsersSync`) |
| `ui/shell/AppShell.kt` | Floating navigation bar (`TabBar`), `TabRoot`, `ComingSoonScreen`; `Tab.accent` (Wall coral, Shopping mint, Events amber, School sky, Photos periwinkle), applied in `MainActivity` via `AccentTheme` |
| `ui/init/*` | `InitScreen` (signed in → app) and the one-page setup (T-058): `SetupScreen`/`SetupContent`, `SetupViewModel` (Immich ping → login → `PicfortServerCheck`), `picfortSuggestion` (same host :5000) |
| `ui/login/*` | `LoginService` (`/api/auth/login`, via `Provider`: Retrofit reads the base URL when built) |
| `ui/settings/*` | App settings, server info, backup status (`countInImmich` with the timeline's match, `BackupQueueRow`, Stop; T-063); `AppearanceSettingsStore` ("Colors from wallpaper", off by default → brand palette) |
| `backup/*` | Android WorkManager periodic background photo backup; `BackupQueue` (what uploads now/next, T-063), `BackupScheduler.stop()`; `BackupRoute` (home-network address, 413 → waits for home, T-064) |

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
# Room filter test on a device (not in CI; uninstalls the app afterwards, log in again with smoke.yaml):
./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.github.ljarka.picfort.db.RemoteAssetsDaoTest
```
`test/.../contract/ImmichV3AppContractTest`: login and backup responses of Immich 3.3 (T-062).
