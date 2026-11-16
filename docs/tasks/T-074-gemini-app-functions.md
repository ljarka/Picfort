# T-074 — Gemini Assistant integration via Android AppFunctions

**Status:** ready  
**Component:** Android app (`:app`, `:core:model`, feature providers)  
**Depends on:** T-051 (modular architecture), existing features (`:feature:school`, `:feature:events`, `:feature:shopping`, `:feature:wall`, `:feature:family`)

## Summary

Expose Picfort's family hub capabilities to the on-device **Gemini Assistant** via Jetpack
**AppFunctions** (`androidx.appfunctions` / `androidx.appfunctions-service`). Users can ask
Gemini natural-language questions or issue commands in Polish or English—such as checking when
a child finishes school, asking about upcoming birthdays/events, reading or adding items to
shopping lists, or reading/posting on the Family Wall—without opening the app.

On devices running Android < 16 (API < 36) or without AppFunctions-capable Gemini, the app
behaves unchanged and the assistant integration is simply not active.

## Architecture & Modular Boundaries (ADR 0011)

- **Strict feature isolation preserved:** `:app` hosts `PicfortAppFunctionService : AppFunctionService()`.
- **Cross-feature contracts in `:core:model`:** Define lightweight read/action interfaces (or
  reuse existing providers like `FamilyMemberProvider`, `FamilyEventsProvider`) implemented by
  each feature module and injected into `PicfortAppFunctionService` via Hilt:
  - `SchoolTimetableProvider` (in `:core:model`, implemented in `:feature:school`):
    children list, day lessons/activities, computed finish time.
  - `FamilyEventsProvider` (extend if needed in `:core:model`, implemented in `:feature:events`):
    upcoming events & birthdays (with age and participants) for a date range or member.
  - `ShoppingAssistantProvider` (in `:core:model`, implemented in `:feature:shopping`):
    lists, unchecked items, add item, check/uncheck item.
  - `WallAssistantProvider` (in `:core:model`, implemented in `:feature:wall`):
    recent posts, post a text note.
  - `FamilyMemberProvider` (existing in `:core:model`, implemented in `:feature:family`):
    family members for resolving names/nicknames.
- **KSP & serial execution (ADR 0012):** `androidx.appfunctions:appfunctions-compiler` uses
  KSP at build time to generate the AppFunctions schema; `gradle.properties` already enforces
  serial KSP execution.

## Exposed `@AppFunction` capabilities

Each function uses `@AppFunction(isDescribedByKDoc = true)` with clear, concise KDocs and
`@AppFunctionSerializable` return types so Gemini can map parameters and summarize answers
naturally:

### 1. School (`:feature:school`)
- `getChildSchoolFinishTime(childName: String, date: String? = null)`:
  Returns when the child finishes school/activities on `date` (defaults to today), including
  the last lesson/activity name and time, or states that there are no classes that day.
- `getChildTimetable(childName: String, date: String? = null)`:
  Returns the full ordered list of lessons and extracurricular activities for that day.

### 2. Events & Birthdays (`:feature:events`)
- `getUpcomingEvents(daysAhead: Int = 30, memberName: String? = null, birthdaysOnly: Boolean = false)`:
  Returns upcoming events and birthdays within `daysAhead` days (optionally filtered by
  family member or birthdays only), including date, title, turning age (for birthdays), and
  assigned members.
- `getNextBirthday(memberName: String? = null)`:
  Finds the next birthday (for a specific person or anyone in the family), returning date and
  age.

### 3. Shopping Lists (`:feature:shopping`)
- `getShoppingLists()`:
  Returns active shopping list names and unchecked item counts.
- `getShoppingListItems(listName: String? = null)`:
  Returns items on the matching list (or the most recently updated active list if omitted).
- `addShoppingItem(itemName: String, listName: String? = null)`:
  Adds an item to the specified list (or default/active list if omitted) and returns the
  updated state.
- `toggleShoppingItem(itemName: String, listName: String? = null, checked: Boolean = true)`:
  Checks or unchecks a matching item on the list.

### 4. Family Wall (`:feature:wall`)
- `getRecentWallNotes(limit: Int = 5)`:
  Returns the latest Family Wall posts (author, timestamp, text).
- `postWallNote(text: String)`:
  Posts a short text note to the Family Wall as the current user.

### 5. Family Members (`:feature:family`)
- `getFamilyMembers()`:
  Returns visible family members (display name, role/school flag, birthday if set) so Gemini
  can disambiguate names.

## Offline / Server Auth & Error Handling

- Each provider checks whether the Picfort server URL & Immich access token are configured
  (`ServerUrlStore` / `AccessTokenStore`).
- If not configured or unreachable:
  - Read functions (`getShoppingListItems`, `getUpcomingEvents`, etc.) fall back to local
    Room cache when available (e.g. offline shopping list copy) or return a structured error
    message (`"Picfort server is not configured or unreachable"`).
  - Write functions (`addShoppingItem`, `postWallNote`) return a clear error rather than
    silently failing.
- No sensitive secrets or tokens are ever returned in AppFunction responses.

## Dependencies & Configuration

- `androidx.appfunctions:appfunctions`
- `androidx.appfunctions:appfunctions-service`
- `androidx.appfunctions:appfunctions-compiler` (KSP)
- `AndroidManifest.xml`: register `PicfortAppFunctionService` with
  `android.app.appfunctions.AppFunctionService` intent filter and
  `android.permission.BIND_APP_FUNCTION_SERVICE` permission (ignored gracefully on API < 36).
- Requires `compileSdk >= 36` (Picfort already uses `compileSdk = 37`).

## Testing & Verification

- **Unit tests:** test each provider's name matching (case-insensitive, Polish diacritics
  friendly, e.g. `"Feliks"`, `"Ola"`), date resolution (`today`, `tomorrow`, `YYYY-MM-DD`),
  and offline/unconfigured error handling without requiring Android 16 OS.
- **ADB verification on Android 16+ emulator/device:**
  - `adb shell cmd app_function list-app-functions --package <picfort_package>` lists all
    exposed functions.
  - `adb shell cmd app_function execute-app-function ...` executes `getChildSchoolFinishTime`,
    `getUpcomingEvents`, `getShoppingListItems`, `addShoppingItem`, and `getRecentWallNotes`.
- **Checks:** `./scripts/check-modular-boundaries.sh`, `./gradlew ktlintCheck`,
  `./gradlew testDebugUnitTest` all green.

## Acceptance criteria

- [ ] `PicfortAppFunctionService` exposes School, Events/Birthdays, Shopping, Family Wall,
      and Family Members functions via `@AppFunction`.
- [ ] All cross-feature contracts live in `:core:model`; `:scripts/check-modular-boundaries.sh`
      passes with zero feature-to-feature dependencies.
- [ ] `adb shell cmd app_function list-app-functions` and `execute-app-function` succeed on
      Android 16+ emulator/device.
- [ ] App installs and runs normally on Android < 16 (`minSdk 26`) with no crashes.
- [ ] Unit tests, `ktlintCheck`, and CI pass.

## Out of scope

- Destructive actions via voice (deleting events, deleting shopping lists, deleting wall
  posts, or managing Immich photo backups/frames).
- Custom Google Assistant App Actions (`shortcuts.xml`) for legacy Google Assistant (< Android 16).
