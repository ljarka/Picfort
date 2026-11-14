# feature:frame — AGENTS.md

Ambient e-paper frame mode and Picfort server setup/pairing vertical slice.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/frame/FrameScreen.kt` | UI: `FrameScreen`, `FrameContent`, slideshow controls, interval selector; "Frame in a browser" link (`browserFrameUrl`, only with a key, T-072) |
| `ui/frame/FrameViewModel.kt` | ViewModel: frame status, polling, interval/mode settings, next photo trigger |
| `ui/frame/FrameRepository.kt` | Repository & implementation of `FrameSender`: runs photo push to frame |
| `ui/frame/FrameDashboardApi.kt` | HTTP client for frame management endpoints (`/status`, `/slideshow`, `/albums`) |
| `ui/frame/FrameSettingsDialog.kt` | Server pairing dialog, live connection test, IP/port & API key input; `PicfortServerCheck` (test + save, also used by `:app`'s setup) |
| `ui/frame/PicfortServerSetupScreen.kt` | One-time step after login, only for installs from before T-058 (setup now asks on one page) |
| `ui/frame/FrameModule.kt` | Hilt module: provides `@FrameHttpClient` OkHttpClient and binds `FrameSender` |
| `ui/frame/FrameScreenshotTest.kt` | Paparazzi snapshots: frame view, not configured, server form & setup |

## Public Contract / Navigation Entry Points

- `FrameScreen(onBack: () -> Unit)`
- `FrameSettingsDialog(onDismiss: () -> Unit, onSaved: () -> Unit = {})`
- `PicfortServerSetupScreen(onDone: () -> Unit)`
- Implements `FrameSender` (`send`, `sendLocal`) bound in Hilt.

## Dependencies & Boundaries

- Depends on `:core:model` (`FrameResult`, `FrameSender`, `localImageUri`).
- Depends on `:core:ui` (`PicfortTheme`).
- Depends on `:core:network` (`PicfortServerClient`, `FrameSettingsStore`, `normalizeFrameUrl`).
- **NEVER** depends directly on other feature modules (`:feature:timeline`, `:feature:wall`, `:feature:shopping`).

## Verification

```bash
./gradlew :feature:frame:testDebugUnitTest
./gradlew :feature:frame:verifyPaparazziDebug
./gradlew :feature:frame:recordPaparazziDebug # on intentional UI changes
```
