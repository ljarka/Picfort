# feature:shopping — AGENTS.md

Shopping list vertical slice: collaborative checklists, offline caching, and wishlists.

## Scope & Files

| File | Purpose |
|---|---|
| `ui/shopping/ShoppingScreens.kt` | UI: `ShoppingListsContent` (cards grid) and `ShoppingListContent` (checklist) |
| `ui/shopping/ShoppingViewModels.kt` | ViewModels: `ShoppingListsViewModel`, `ShoppingListViewModel`, `ShoppingListsScreen`, `ShoppingListScreen` |
| `ui/shopping/ShoppingRepository.kt` | Repository: polling, sync, optimistic updates, offline cache file |
| `ui/shopping/ShoppingApi.kt` | Server HTTP client: `/lists` endpoints |
| `ui/shopping/ShoppingModels.kt` | UI state: `ShoppingListsUi`, `ShoppingListUi` |
| `ui/shopping/ShoppingScreenshotTest.kt` | Paparazzi snapshots: cards, empty state, offline banner, list items |

## Public Contract / Navigation Entry Points

- `ShoppingListsScreen(onOpen: (ShoppingList, Boolean) -> Unit, onArchivedClick: () -> Unit, onBack: () -> Unit, onSettingsClick: () -> Unit, onSetUpServer: () -> Unit)`
- `ShoppingListScreen(onBack: () -> Unit, focusAdd: Boolean)`

## Dependencies & Boundaries

- Depends on `:core:model` (`ShoppingList`, `ShoppingItem`, `FamilyMember`, `FamilyMemberProvider`).
- Depends on `:core:ui` (`PicfortTheme`, `MemberAvatar`, `SettingsAction`, `ServerRequiredContent`).
- Depends on `:core:network` (`PicfortServerClient`, `PicfortServerException`, `FrameSettingsStore`, `AccessTokenStore`, `AppScope`).
- **NEVER** depends directly on other feature modules (`:feature:wall`, `:feature:events`, `:feature:family`).
- Family members and user identity are consumed via `FamilyMemberProvider` interface.

## Verification

```bash
./gradlew :feature:shopping:testDebugUnitTest
./gradlew :feature:shopping:verifyPaparazziDebug
./gradlew :feature:shopping:recordPaparazziDebug # on intentional UI changes
```
