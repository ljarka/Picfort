# feature:shopping — AGENTS.md

Shopping list vertical slice: collaborative checklists, offline caching, receipts, spending summary, and wishlist carousel (T-042).

## Scope & Files

| File | Purpose |
|---|---|
| `ui/shopping/ShoppingScreens.kt` | UI: `ShoppingListsContent` (cards grid + wishlist carousel) and `ShoppingListContent` (checklist + receipts) |
| `ui/shopping/WishlistUi.kt` | Wishlist horizontal carousel, product cards, add/edit bottom sheet with link preview |
| `ui/shopping/WishlistApi.kt` | Server HTTP client: `/wishlist` and `/wishlist/preview` endpoints |
| `ui/shopping/WishlistRepository.kt` | Shared wishlist state & `WishlistProvider` implementation |
| `ui/shopping/ReceiptSheet.kt` | Receipt modal bottom sheet, date picker, member/list dropdowns, full-screen zoom viewer |
| `ui/shopping/ReceiptReader.kt` | `ReceiptReader` & `GeminiNanoReceiptReader` (on-device ML Kit GenAI Prompt / AICore) |
| `ui/shopping/ReceiptReadingParser.kt` | JSON parsing and validation for on-device receipt reading output |
| `ui/shopping/ReceiptMoney.kt` | `BigDecimal` receipt total parsing and locale-aware currency formatting |
| `ui/shopping/SpendingScreen.kt` | Monthly spending summary screen, 6-month bar chart, by-shop/by-person, receipts |
| `ui/shopping/SpendingViewModel.kt` | ViewModel for `SpendingScreen` (month navigation, `/spending` + `/receipts`) |
| `ui/shopping/SpendingModels.kt` | Spending domain/UI models and aggregation helpers (`topShopsWithOther`, etc.) |
| `ui/shopping/ShoppingViewModels.kt` | ViewModels: `ShoppingListsViewModel`, `ShoppingListViewModel`, `ShoppingListsScreen`, `ShoppingListScreen` |
| `ui/shopping/ShoppingRepository.kt` | Repository: polling, sync, optimistic updates, offline cache file, receipts CRUD |
| `ui/shopping/ShoppingAssistantImpl.kt` | `ShoppingAssistantProvider` for Gemini AppFunctions (T-074) |
| `ui/shopping/ShoppingApi.kt` | Server HTTP client: `/lists` and `/receipts` endpoints |
| `ui/shopping/ShoppingModels.kt` | UI state: `ShoppingListsUi`, `ShoppingListUi` |
| `ui/shopping/ShoppingScreenshotTest.kt` | Paparazzi snapshots: cards, empty state, offline banner, list items, receipts & sheet |

## Public Contract / Navigation Entry Points

- `ShoppingListsScreen(onOpen: (ShoppingList, Boolean) -> Unit, onArchivedClick: () -> Unit, onSpendingClick: () -> Unit, onBack: () -> Unit, onSettingsClick: () -> Unit, onSetUpServer: () -> Unit)`
- `ShoppingListScreen(onBack: () -> Unit, focusAdd: Boolean)`
- `SpendingScreen(onBack: () -> Unit)`

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
