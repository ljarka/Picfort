# :core:model

Pure Kotlin/Android domain models shared across all features and core modules.

## Scope
- Shared data models: `FamilyMember`, `BirthDate`, `ShoppingList`, `ShoppingItem`, `FamilyEvent`, `EventsSegment`.
- Zero UI presentation logic, zero network dependencies, zero database dependencies.
- Models MUST remain immutable (`val` properties, data classes).

## Verification
```bash
./gradlew :core:model:build
```
