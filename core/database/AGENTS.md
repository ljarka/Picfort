# :core:database

Room database, entities and DAOs for local caching and offline storage.

## Scope
- Room configuration: `ImagesDatabase` (version 23, destructive migration fallback).
- DAOs and Entities:
  - `UsersDao`, `UserEntity` (Immich users synced from stream).
  - `RemoteAssetsDao`, `RemoteAssetEntity` (Timeline images, stacks, partner libraries;
    `takenOnMonthDays` for photos of given days, T-055).
  - `BackupDao`, `LocalBackupEntity` (Background photo backup state).
- Hilt bindings: `DatabaseModule`.

## Verification
```bash
./gradlew :core:database:build
```
