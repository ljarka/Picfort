# Playbook: Adding a New Feature Module

Follow this standard procedure whenever building a new capability (e.g. School timetable, Receipts scanning, Spending summary).

## 1. Scaffold the module

Run:
```bash
./scripts/new-feature.sh <name>
# e.g. ./scripts/new-feature.sh school
```
This automatically sets up:
- `feature/<name>/build.gradle.kts`
- `feature/<name>/AGENTS.md`
- Registration in `settings.gradle.kts` and `app/build.gradle.kts`

## 2. Architecture Rules to Enforce

1. **Feature Isolation**: Never import `feature:*` from another `feature:*`.
2. **Cross-Feature Communication**: If `<name>` needs data from another feature (e.g. family members or events):
   - Check if an interface exists in `:core:model` (`FamilyMemberProvider`, `FamilyEventsProvider`).
   - If not, define a new interface in `:core:model` and implement it in the source feature repository.
3. **Core Dependencies**:
   - Models belong in `:core:model`.
   - Reusable theme/avatar components belong in `:core:ui`.
   - Network requests use `PicfortServerClient` in `:core:network`.
   - Room tables/DAOs belong in `:core:database`.

## 3. UI and Screenshots

1. Write your Screen and ViewModel in `feature/<name>/src/main/java/...`.
2. Create `...ScreenshotTest.kt` in `feature/<name>/src/test/java/...` using Paparazzi.
3. Record initial goldens:
   ```bash
   ./gradlew :feature:<name>:recordPaparazziDebug
   ```

## 4. Hook into :app Shell

1. Add destination route and screen invocation to `app/src/main/java/com/github/ljarka/picfort/MainActivity.kt`.
2. If it's a primary tab, add it to `Tab` in `app/src/main/java/com/github/ljarka/picfort/ui/shell/AppShell.kt`.

## 5. Verify

```bash
./scripts/check-modular-boundaries.sh
./gradlew :feature:<name>:testDebugUnitTest :feature:<name>:verifyPaparazziDebug
```
