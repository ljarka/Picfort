# :core:model

Pure Kotlin/Android domain models shared across all features and core modules.

## Scope
- Shared data models: `FamilyMember`, `BirthDate`, `ShoppingList`, `ShoppingItem`, `FamilyEvent`, `EventsSegment`.
- Cross-feature contracts: `FamilyMemberProvider`, `FamilyEventsProvider`, `FrameSender`, `ImmichUsersSync`, `SchoolMembership` (Family's "Goes to school" ↔ School, T-068),
  `DayPhotosProvider` (`DayPhoto`: photos taken on given days, from `:feature:timeline`, T-055).
- Shared coroutine helper: `pollEvery` for periodic refresh loops (fixed delay or start-to-start cadence).
- Zero UI presentation logic, zero network dependencies, zero database dependencies.
- Models MUST remain immutable (`val` properties, data classes).

## Verification
```bash
./gradlew :core:model:build
```
