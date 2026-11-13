# :core:network

OkHttp, credential storage, Picfort server client and network error mapping.

## Scope
- Server client: `PicfortServerClient`, `PicfortServerException` (`com.github.ljarka.picfort.core.network`).
- Credentials & settings: `SecurePreferencesStore` (DataStore + Keystore), `AccessTokenStore`, `FrameSettingsStore`,
  `normalizeFrameUrl()`; `defaultSchemeFor()` (no scheme typed: http:// on the local network, else https://;
  Immich and Picfort addresses, T-058).
- Qualifier: `@PicfortHttpClient`.

## Verification
```bash
./gradlew :core:network:build
```
