# :core:network

OkHttp, credential storage, Picfort server client and network error mapping.

## Scope
- Server client: `PicfortServerClient`, `PicfortServerException` (`com.github.ljarka.picfort.core.network`).
- Credentials & settings: `SecurePreferencesStore` (DataStore + Keystore, auto-recovers from lost/invalidated Keystore keys, non-blocking async writes; tested in `SecurePreferencesStoreTest`, T-108), `AccessTokenStore`, `FrameSettingsStore`,
  `normalizeFrameUrl()`; `defaultSchemeFor()` (no scheme typed: http:// on the local network, else https://;
  Immich and Picfort addresses, T-058).
- Qualifier: `@PicfortHttpClient`.
- Setup mistakes and their fixes (T-086), `SetupHints.kt`, pure and tested (`SetupHintsTest`):
  `picfortUnreachableMessage`, `notPicfortMessage` (AirPlay on :5000, Immich's port), `picfortImmichProblem`
  (the server's `/status` "immich"), `immichUnreachableMessage`, `notImmichMessage` (`Server: picfort`).

## Verification
```bash
./gradlew :core:network:build :core:network:testDebugUnitTest
```
