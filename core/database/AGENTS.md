# :core:database

Room database, entities and DAOs for local caching and offline storage.

## Scope & Table Classification (ADR 0016)
`ImagesDatabase` (version 26) keeps `fallbackToDestructiveMigration(dropAllTables = true)` because **every table in it is a cache** that can be rebuilt without data loss. Cheap additive steps live in `ImagesDatabaseMigrations.kt` (`MIGRATION_23_24`, `MIGRATION_24_25`, `MIGRATION_25_26`, SQL copied from Room's generated `createAllTables`) so such updates skip the full re-sync:

| Table | Entity | Kind | Rebuilt from |
|---|---|---|---|
| `users` | `UserEntity` | cache | Immich sync stream / users API |
| `remote_assets` | `RemoteAssetEntity` | cache | Immich sync stream |
| `remote_asset_exif` | `RemoteAssetExifEntity` | cache | Immich sync stream (`AssetExifV1`, T-088) |
| `remote_documents` | `RemoteDocumentEntity` | cache | Immich metadata OCR search (T-088) |
| `remote_best` | `RemoteBestEntity` | cache | Immich `ai/best/frame-worthy` tag search (T-094) |
| `ai_labels` | `AiLabelEntity` | cache | Picfort server `/photo-insights/labels` (T-102, T-099) |
| `remote_stacks` | `RemoteStackEntity` | cache | Immich sync stream |
| `partners` | `PartnerEntity` | cache | Immich sync stream |
| `sync_state` | `SyncStateEntity` | cache | Immich sync cursor (full re-sync on wipe) |
| `local_backup` | `LocalBackupEntity` | cache | Local MediaStore re-hash; server dedupes, nothing is uploaded twice |

If a future table holds user state that cannot be rebuilt (e.g. local reminders or unsynced drafts), replace the destructive fallback with explicit Room `Migration`s and `MigrationTestHelper` tests.

## Verification
```bash
./gradlew :core:database:build
```
