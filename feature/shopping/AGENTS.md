# feature:shopping — AGENTS.md

Shopping list vertical slice: collaborative checklists, offline caching, receipts, spending summary, and wishlist carousel (T-042).

## Scope & Files

| File | Purpose |
|---|---|
| `ui/shopping/ShoppingScreens.kt` | UI: `ShoppingListsContent` (cards grid + wishlist carousel) and `ShoppingListContent` (checklist + receipts) |
| `ui/shopping/WishlistUi.kt` | Wishlist horizontal carousel, product cards, add/edit bottom sheet with link preview, Take / Choose photo (T-083) |
| `ui/shopping/WishlistWebViewSheet.kt` | In-app WebView bottom sheet fallback when server preview cannot fetch an image |
| `ui/shopping/WishlistApi.kt` | Server HTTP client: `/wishlist`, `/wishlist/preview`, `PUT /wishlist/{id}/photo`; resolves `/wishlist/photo/…` |
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
| `ui/shopping/reminders/*` | List reminders (T-040): `ShoppingReminderTimes` (pure: presets, labels, text, gone/finished lists), `ShoppingReminders` (store, alarms, notification), `ShoppingReminderReceiver` (+ rearm receiver, `ShoppingReminderSync`), `ShoppingReminderUi` (bell, status row, sheet) |
| `ui/shopping/ShoppingScreenshotTest.kt` | Paparazzi snapshots: cards, empty state, offline banner, list items, receipts & sheet, reminders |

## Reminders (T-040, like ADR 0015)

- Per phone, nothing on the server: one `ShoppingReminder(listId, title, at)` per list in preferences
  `shopping_reminders`; AlarmManager exact when allowed, else inexact. Receivers are in this module's manifest.
- Fired: `ShoppingRepository.refresh()` (8 s, else the offline copy) → "Time for groceries: <title>" with
  up to 5 unchecked items. Nothing left, or the list deleted/archived → no notification, reminder dropped.
- Actions: Open list (launch intent with `EXTRA_OPEN_SHOPPING_LIST_ID`, read by `MainActivity`), Snooze 1h.
- `ShoppingReminderSync` (started by both ViewModels and the receiver) drops reminders of lists that
  vanish or get archived and dismisses a shown one once all items are checked. Re-armed after reboot,
  update or clock change; one missed by more than 12 h is dropped.

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
./scripts/maestro-smoke.sh maestro/shopping-reminders.yaml # T-040 on a device, test server only (~8 min)
```
