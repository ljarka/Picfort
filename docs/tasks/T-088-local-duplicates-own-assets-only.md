# T-088 — Bug: phone-copy deduplication matches partners' assets (clean-up shows photos you don't own)

**Status:** ready  
**Priority:** MEDIUM — theoretical regression from T-059; **needs device verification before implementing**
**Component:** Android app (`:feature:timeline`)  
**Depends on:** T-059 (the commit that introduced the bug: `ac7187c`)

> **⚠ Verification required:** This issue was identified by static code analysis, not reproduced
> on a device. Before implementing the fix, verify that the symptom actually occurs: on a family
> setup with two accounts sharing Immich libraries, check whether a phone photo whose name + time
> (or checksum) matches *only* a partner's Immich asset disappears from the timeline. If it does
> not reproduce, close this task with a note explaining why the matching is acceptable as-is.

## Problem

`localIdsOnServer` in `LocalDuplicates.kt` determines which phone photos are "already in Immich"
so the timeline shows them once (as the Immich copy) rather than twice. It is also the data
source for the backed-up count in Settings and for the "clean up photos" flow where the user
deletes phone copies to free space.

T-059 added two new matching conditions beyond the original backup-link check:
2. SHA-1 checksum match  
3. File name + time taken match (± time-zone offset)

**Both new conditions match against ALL visible timeline assets — including partners' (family
members who shared their Immich library).** Only condition 1 (backup link) is inherently
per-user.

### Consequence

If a family member uploaded `DSC_1234.jpg` taken at noon to their Immich account and you have
the same file on your phone, `localIdsOnServer` now marks your phone copy as "already on
server" — even though it matched a **partner's** asset, not yours.

Result:
- Your phone copy is hidden from the timeline; the partner's Immich copy is shown instead.
- In "clean up photos" / selection mode that copy appears as if it were yours in Immich.
- Attempting to delete it from Immich fails (you are not the owner → Immich 403).
- The Settings counter "X of Y phone items are in Immich" is inflated by false matches.

## Root cause

```kotlin
// LocalDuplicates.kt — iterates over ALL remote assets, including partners'
val checksums = remote.mapTo(HashSet()) { it.checksum }          // condition 2
remote.forEach { a ->                                              // condition 3
    takenByName.getOrPut(name.lowercase()) { ... } += taken
}
```

`RemoteAssetEntity.ownerId` exists and is indexed. `SyncStateEntity.userId` (the current
user's Immich ID) is available via `RemoteAssetsDao.syncState()`, which
`SyncTimelineSource` already fetches.

## Fix

Add a `myUserId: String` parameter to `localIdsOnServer` and restrict conditions 2 and 3
to the current user's own assets. Condition 1 (backup link) checks against `remoteIds` —
a Set of all asset IDs — which is correct: the linked asset IS yours regardless of how it
was uploaded.

```kotlin
// LocalDuplicates.kt
fun localIdsOnServer(
    remote: List<RemoteAssetEntity>,
    local: List<ImageData>,
    backupLinks: Map<Long, String>,
    localChecksums: Map<Long, String>,
    zone: ZoneId,
    myUserId: String,                                   // ← new
): Set<Long> {
    val remoteIds = remote.mapTo(HashSet()) { it.id }  // all, for backup-link check
    val myAssets = remote.filter { it.ownerId == myUserId }  // own only
    val checksums = myAssets.mapTo(HashSet()) { it.checksum }
    val takenByName = HashMap<String, MutableList<Long>>()
    myAssets.forEach { a ->                            // ← was `remote`
        val name = a.originalFileName?.takeIf { it.isNotBlank() } ?: return@forEach
        val taken = a.localDateTime ?: return@forEach
        takenByName.getOrPut(name.lowercase()) { ArrayList(1) } += taken
    }
    return local.mapNotNullTo(HashSet()) { m ->
        val onServer = backupLinks[m.id] in remoteIds ||
            localChecksums[m.id]?.let { hexToBase64(it) in checksums } == true ||
            takenByName[m.fileName.lowercase()]?.any { isSameMoment(wallClock(m.dateTaken, zone), it) } == true
        m.id.takeIf { onServer }
    }
}
```

**How to thread `myUserId` to the call site (`TimelineRepository.kt`):**

`SyncTimelineSource` already exposes a flow of `userId` (from `dao.syncStateFlow()`).
Inject `RemoteAssetsDao` into `TimelineRepository` (it already has it via `backupDao`'s
module, or inject `dao` directly) and read `userId` from `syncState()`, or add a
`myUserId: Flow<String>` to `SyncTimelineSource` and combine it in `TimelineRepository.merged`.

The simplest approach: add `val userId: Flow<String>` to `SyncTimelineSource`:

```kotlin
// SyncTimelineSource.kt
val userId: Flow<String> = dao.syncStateFlow().map { it?.userId.orEmpty() }.distinctUntilChanged()
```

Then in `TimelineRepository.merged`:

```kotlin
private val merged = combine(remote.assets(), remote.userId, local.filterNotNull()) { assets, userId, snapshot ->
    val zone = ZoneId.systemDefault()
    val onServer = localIdsOnServer(assets, snapshot.media, snapshot.backupLinks, snapshot.checksums, zone, userId)
    Merged(buildTimelineIndex(assets, snapshot.media, onServer, zone), onServer)
}
```

## Acceptance criteria

- [ ] Unit tests in `LocalDuplicatesTest`: a phone photo whose checksum or name+time matches
  only a **partner's** asset is NOT returned by `localIdsOnServer` (stays visible as the phone
  copy).
- [ ] Existing tests unchanged: matches against the **own** user's assets still work.
- [ ] `./gradlew :feature:timeline:testDebugUnitTest` green.
- [ ] `./gradlew :feature:timeline:verifyPaparazziDebug` green (no visual change expected).
- [ ] Device check: family setup with two accounts sharing libraries — a photo uploaded only
  by the partner does NOT disappear from the current user's phone-copy view.
