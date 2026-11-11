# T-025 — Spending summary: Android

**Status:** ready (first step: mockup for the owner to approve)  
**Component:** Android app  
**Depends on:** T-021 (receipt sheet), T-020 (`/spending`)

## Summary

A Spending screen on the Shopping tab: what the family spent this month, the last six months
as bars, per shop and per person, and the month's receipts.

## Mockup first

`docs/wireframes/spending-v1.png`, approved by the owner before coding.

## Screen

Entry: a "Spending" icon (bar chart) in the Shopping tab's top bar, left of ⋮. Bottom bar
hidden.

```
┌─────────────────────────────┐
│ ←  Spending                 │
│   ‹  November 2026  ›       │  month switcher; › disabled on the current month
│      1 284,50 zł            │  month total
│      +12% vs October        │  hidden when last month is 0
│ ▁▃▅▂▇▆  Jun … Nov           │  6 bars ending at the selected month; tap selects it
│ By shop                     │
│  Biedronka        640,00 zł │  top 5, then "Other"; no shop → "Unknown"
│ By person                   │
│  (◯) Mama         900,00 zł │  unknown id → "Former member"
│ Receipts                    │
│  12 Nov  Lidl      84,10 zł │  tap → receipt sheet (T-021)
│  10 Nov  Orlen   No total ⚠ │  receipts without a total first
│                 (+ Receipt) │  FAB: add a receipt without a list
└─────────────────────────────┘
```

- Data: `GET /spending?from=<selected − 5 months>&to=<selected>` and `GET
  /receipts?month=<selected>`; reload on open, on month change, after the sheet closes, and
  on pull-to-refresh. No polling.
- The current month is the phone's.
- A note under the total when needed: "2 receipts not confirmed" (`unconfirmed_count`), "1
  receipt without a total" (`missing_total_count`). Unconfirmed receipts are in the total.
- Bars: plain Compose `Canvas`, no chart library. Each bar has a content description
  ("October 2026, 1 146,00 zł"); the selected bar is highlighted by more than color.
- Amounts: `NumberFormat.getCurrencyInstance` for the device locale with the server's
  `currency`; minor units as `Long`.
- One scrolling `LazyColumn`.
- Empty month: "No receipts in November 2026" with the FAB. Server unreachable: message +
  Retry (no offline copy).

## Acceptance criteria

- [ ] Month total, six bars, by shop and by person match `/spending` for a month with
      receipts from two people and three shops.
- [ ] Switching months (arrows, tapping a bar) updates everything; the future is not reachable.
- [ ] A receipt dated 31 Oct counts in October, 1 Nov in November.
- [ ] Adding, editing or deleting a receipt from this screen updates the numbers on return.
- [ ] Notes for unconfirmed receipts and receipts without a total appear when they apply.
- [ ] TalkBack reads the bars and totals.
- [ ] Paparazzi: month with data, empty month, notes shown, dark.
- [ ] README Features and screenshots; CI passes.

## Out of scope

- Budgets, categories, year view, CSV export, "who owes whom".
- A spending card on the wall or the frame.
