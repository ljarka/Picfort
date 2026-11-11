# T-042 — Wishlist carousel & event linking (Android)

**Status:** ready  
**Component:** Android app  
**Depends on:** T-019 (Shopping tab), T-023 (Events UI), T-041 (Wishlist backend API)

## Summary

In the **Shopping** tab, display a horizontal **Wishlist carousel** above the shopping lists. Family
members can add product links (e.g. from Allegro or Amazon), see auto-rendered product photos and
titles, and link items to family events (birthdays, Christmas) or assign them to a family member.

## Mockup

Approved wireframe: `docs/wireframes/shopping-wishlist-v1.png`.

## Design

### Shopping Tab Carousel
- Placed directly above the "Listy zakupów" (Shopping lists) section.
- Header: **"Lista życzeń"** with item count badge.
- Horizontal `LazyRow` with cards:
  - **Product card:**
    - High-quality product photo (Coil `AsyncImage`, rounded 14 dp).
    - Product title (max 2 lines, ellipsis).
    - Formatted price (e.g. "149 zł") if available.
    - Event badge pill (e.g. "🎂 Ola's birthday" or "🎄 Christmas Eve") linking to that event.
    - Secret status badge: "Reserved by Tata" (hidden from the recipient if assigned to them!).
  - **"+ Add link" card:**
    - Clean dashed/elevated tile inviting to paste a link.
- **Tap on card:** opens the product URL in the external browser or store app (e.g. Allegro app).
- **Long press / menu:** "Mark as bought", "Reserve gift", "Edit", "Delete".

### Add Wishlist Item Bottom Sheet
- Input field: "Product URL" with a quick **"Paste from clipboard"** button.
- As soon as a valid URL is entered, auto-fetch preview (`POST /wishlist/preview`) and show:
  - Product thumbnail and title (editable by user if shop title is too long).
  - Price field.
- **Link to event:** Dropdown / picker showing upcoming events (from `EventsRepository.upcoming`),
  with special priority for birthdays and holidays.
- **For who:** Dropdown of family members (from `FamilyRepository.allMembers`).
- **Save button:** calls `POST /wishlist`.

### Event Detail Integration
- In `EventDetailScreen` (T-023), if there are wishlist items linked to this `event.id`:
  - A dedicated section: **"Gift ideas & Wishlist"**.
  - Shows product cards directly on the event screen so family members know what to buy.

## Acceptance criteria

- [ ] Wishlist carousel renders smoothly in Shopping tab with product image, title, price, and event badge.
- [ ] Pasting a link fetches title and image automatically; user can adjust fields before saving.
- [ ] Tapping a card opens the external shop URL.
- [ ] Items linked to an event appear both in the Shopping wishlist carousel and in that event's detail screen.
- [ ] Marking an item as bought moves it to the end or strikes it through.
- [ ] Paparazzi snapshot tests for wishlist carousel (light & dark).
- [ ] Unit tests for `WishlistRepository` and preview parsing.
