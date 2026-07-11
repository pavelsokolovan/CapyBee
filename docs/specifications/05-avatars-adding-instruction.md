# CapyBee Avatar Insertion Instructions

## Purpose

This document tells Copilot exactly where to place each avatar image, what size to render it at, and what logic or condition controls which avatar is shown. Follow these instructions screen by screen. Do not add avatars in any location not listed here.

## Asset Location

All avatar files live in:

```
src/assets/avatars/
```

Full list of files:

```
capybee-default.png
capybee-mood-heavy.png
capybee-mood-okay.png
capybee-mood-good.png
capybee-celebrating.png
capybee-waving.png
capybee-empathetic.png
capybee-suggesting.png
capybee-face-happy.png
capybee-face-sad.png
capybee-face-okay.png
```

## Display sizes

| Usage context | Rendered size | File to use |
|---|---|---|
| Hero / landing | 200×200px | full body |
| Home screen greeting | 160×160px | full body |
| Mood reaction (after save) | 160×160px | full body |
| Mission suggestion | 120×120px | full body |
| Mission complete celebration | 160×160px | full body |
| Empty state | 120×120px | full body |
| Chat bubble / speech bubble avatar | 48×48px | face only |
| Check-in history list item | 32×32px | face only |

All images: `alt=""` (decorative) except the landing hero which uses `alt="CapyBee"`.

---

## Screen-by-screen instructions

---

### SCR-01 — Landing page (`/`, unauthenticated)

**Where:** Above the main heading, centered.

**Avatar:** `capybee-waving.png`

**Size:** 200×200px

**Logic:** Always shown. No conditions. This is a static hero image.

**Copy next to avatar:** None. The heading below it introduces the app.

---

### SCR-02 — Auth redirect / loading

**Where:** Center of screen while OAuth redirect is in progress.

**Avatar:** `capybee-default.png`

**Size:** 120×120px

**Logic:** Show during the loading/redirect state only. Remove once redirect completes.

**Copy next to avatar:** A single line below the image: "One moment..." (EN) / "Chwileczkę..." (PL)

---

### SCR-03 — Authenticated home (`/`, authenticated)

This screen has three distinct avatar moments.

#### Moment 1 — Greeting (top of screen, before check-in)

**Where:** Above the "Jak dziś było?" / "How's today?" section.

**Avatar:** `capybee-waving.png` if this is the first visit of the day. `capybee-default.png` if the child has already checked in today.

**Size:** 160×160px

**Logic:**

```
if (noCheckInToday) {
  show capybee-waving.png
} else {
  show capybee-default.png
}
```

Determine `noCheckInToday` by checking whether the most recent check-in in the `GET /api/check-ins` response has today's date.

**Copy next to avatar:** A short CapyBee speech bubble rendered directly below the image (not beside it — below, centered):

- First visit of day: "Hej! Jak dziś było?" / "Hey! How was today?"
- Already checked in: "Miło cię znowu widzieć." / "Good to see you again."

The speech bubble is a rounded rectangle with a small upward tail pointing at the avatar. Background: warm cream (`#FDF6E3`). Border: 1px solid `#E8C97A`.

#### Moment 2 — Mood reaction (immediately after saving a check-in)

**Where:** Replace the greeting avatar in-place (same position, same size). Show for 3 seconds, then revert to the greeting state.

**Avatar:** depends on mood saved:

| Mood saved | Avatar |
|---|---|
| `heavy` | `capybee-empathetic.png` |
| `okay` | `capybee-face-okay.png` shown at 160×160px (scale up face-only image) |
| `good` | `capybee-celebrating.png` |

**Logic:**

```
onCheckInSaved(mood) {
  showReactionAvatar(mood)         // swap avatar
  showReactionSpeechBubble(mood)   // swap speech bubble text
  setTimeout(() => revertToGreeting(), 3000)
}
```

**Speech bubble text per mood:**

| Mood | Polish | English |
|---|---|---|
| `heavy` | "To brzmi ciężko. Jestem tu." | "That sounds hard. I'm here." |
| `okay` | "Okej to też jest coś. Dobra robota." | "Okay is something. Good job showing up." |
| `good` | "To świetnie! Cieszę się razem z tobą." | "That's great! I'm happy with you." |

#### Moment 3 — Mission suggestion (below mood reaction, or below check-in section)

**Where:** Inside a suggestion card that appears below the check-in section after a check-in is saved. The card contains a small avatar on the left and suggestion text on the right.

**Avatar:** `capybee-suggesting.png`

**Size:** 48×48px (face-only scale is fine here — use `capybee-face-happy.png` as fallback if suggesting image not ready)

**Logic:** Show this card only after a check-in has been saved in the current session. Hide it on initial page load. Hide it if the child navigates away and returns.

**Card content:**

```
[avatar 48px] "Mam dla ciebie małą misję na dziś →"
              "I have a small mission for you today →"
```

Tapping the card navigates to `/missions`.

---

### SCR-04 — Child profile setup (`/profile/setup`)

**Where:** Top of the form, centered above the first field.

**Avatar:** `capybee-default.png`

**Size:** 120×120px

**Logic:** Always shown. Static. This is a reassuring presence during setup.

**Copy next to avatar:** Speech bubble below the image: "Jak mam się do ciebie zwracać?" / "What should I call you?"

---

### SCR-05 — Child profile settings (`/profile/settings`)

**Where:** Top of the settings screen, centered.

**Avatar:** `capybee-default.png`

**Size:** 80×80px

**Logic:** Always shown. Static. Smaller than setup screen — this is a utility screen for parents.

**No speech bubble** on this screen.

---

### SCR-06 — Missions list (`/missions`)

This screen has two avatar moments.

#### Moment 1 — Page header

**Where:** Top of the page, left-aligned next to the page title "Misje" / "Missions".

**Avatar:** `capybee-face-happy.png`

**Size:** 48×48px

**Logic:** Always shown. Static.

**No speech bubble.**

#### Moment 2 — Empty state (no active missions)

**Where:** Center of screen, replacing the mission card list when `GET /api/missions` returns an empty array.

**Avatar:** `capybee-default.png`

**Size:** 120×120px

**Speech bubble below:** "Nie ma teraz misji — wróć jutro!" / "No missions right now — check back tomorrow!"

**Logic:**

```
if (missions.length === 0) {
  show empty state with capybee-default.png
} else {
  show mission card list
}
```

#### Moment 3 — Mission completion feedback

**Where:** A full-width feedback banner that appears at the top of the screen for 3 seconds after `POST /api/missions/{missionId}/completions` succeeds.

**Avatar:** `capybee-celebrating.png`

**Size:** 80×80px, left side of the banner.

**Banner content:**

```
[capybee-celebrating 80px]  "Misja wykonana! Nowa komórka w ulu 🍯"
                             "Mission done! A new cell in your hive 🍯"
```

Banner background: warm honey yellow (`#FDF0C0`). Border-bottom: 2px solid `#E8C97A`. Auto-dismiss after 3 seconds.

**Logic:**

```
onMissionCompleted() {
  showCompletionBanner()
  setTimeout(() => hideBanner(), 3000)
}
```

---

### SCR-07 — Mission completion history (`/missions/history`)

**Where:** Next to each history list item, left of the mission title.

**Avatar:** `capybee-face-happy.png`

**Size:** 32×32px

**Logic:** Same avatar for every list item. Always shown. No conditions.

**Empty state:** If list is empty, show `capybee-default.png` at 120×120px centered with text: "Nie ma jeszcze żadnych misji." / "No missions yet."

---

### SCR-08 — Friendship tracker (`/friendships`)

This screen has two avatar moments.

#### Moment 1 — Page header

**Where:** Top of the page, left-aligned next to the "Relacje" / "Friendships" title.

**Avatar:** `capybee-face-happy.png`

**Size:** 48×48px

**Logic:** Always shown. Static.

#### Moment 2 — Entry created feedback

**Where:** A small toast notification at the bottom of the screen, appearing for 3 seconds after `POST /api/friendships` succeeds.

**Avatar:** `capybee-celebrating.png`

**Size:** 48×48px, left of the toast text.

**Toast text:**

```
"Zapamiętałem! Każdy krok się liczy."
"Got it! Every step counts."
```

**Logic:**

```
onFriendshipEntryCreated() {
  showToast(capybee-celebrating, message)
  setTimeout(() => hideToast(), 3000)
}
```

#### Moment 3 — Empty state (no entries yet)

**Where:** Center of entries list area when `GET /api/friendships` returns empty array.

**Avatar:** `capybee-default.png`

**Size:** 120×120px

**Speech bubble below:** "Kogo dziś zauważyłeś?" / "Who did you notice today?"

---

### SCR-09 — Memory space (`/memories`)

This screen has three avatar moments, one per world tab and one for saves.

#### Moment 1 — Old World tab empty state

**Where:** Center of the memory list area when Old World tab is active and `GET /api/memories?worldType=old_world` returns empty.

**Avatar:** `capybee-empathetic.png`

**Size:** 120×120px

**Speech bubble below:** "Twój stary dom jest tutaj bezpieczny." / "Your old home is safe here."

#### Moment 2 — New World tab empty state

**Where:** Center of the memory list area when New World tab is active and `GET /api/memories?worldType=new_world` returns empty.

**Avatar:** `capybee-default.png`

**Size:** 120×120px

**Speech bubble below:** "Zacznij budować swój nowy ul." / "Start building your new hive."

**Logic for both:**

```
if (activeTab === 'old_world' && memories.length === 0) {
  show capybee-empathetic.png with old world message
}
if (activeTab === 'new_world' && memories.length === 0) {
  show capybee-default.png with new world message
}
```

#### Moment 3 — Memory saved feedback

**Where:** Small toast at the bottom of the screen for 3 seconds after `POST /api/memories` succeeds.

**Avatar:** `capybee-celebrating.png`

**Size:** 48×48px, left of toast text.

**Toast text:**

| Active tab | Polish | English |
|---|---|---|
| Old World | "Zapamiętane. To zawsze będzie twoje." | "Saved. This will always be yours." |
| New World | "Nowa chwila w ulu!" | "A new moment in the hive!" |

---

### SCR-10 — Session expired / not authenticated

**Where:** Center of screen.

**Avatar:** `capybee-waving.png`

**Size:** 120×120px

**Logic:** Always shown when this state is rendered.

**Speech bubble below:** "Hej, wróciłeś! Zaloguj się znowu." / "Hey, you're back! Sign in again."

---

## Shared CapyBee speech bubble component

All speech bubbles across the app use the same component. Implement it once and reuse everywhere.

```
<CapyBeeBubble text="..." />
```

Styling:
- Background: `#FDF6E3`
- Border: `1px solid #E8C97A`
- Border-radius: `16px`
- Padding: `10px 16px`
- Font-size: `14px`
- Color: `#5C3D00`
- Max-width: `260px`
- Centered below the avatar image
- Small upward-pointing triangle at the top center of the bubble (CSS triangle, same background color and border)

The bubble text comes from the locale currently active (`en` or `pl`). Use the same i18n system as the rest of the app.

---

## Avatar display rules

- Never show two full-body avatars on screen at the same time.
- Face-only avatars in list items and toasts do not count toward this limit.
- All avatar `<img>` elements must have `draggable="false"` and `user-select: none`.
- Do not animate avatars with CSS (no spin, bounce, or pulse). Swapping between avatars counts as the animation.
- On screens where a speech bubble is shown, the bubble appears directly below the avatar with 8px gap.

---

## Traceability

| Avatar file | Screens used |
|---|---|
| `capybee-default.png` | SCR-01 (fallback), SCR-02, SCR-03 (returning), SCR-04, SCR-05, SCR-06 empty, SCR-07 empty, SCR-08 empty, SCR-09 new world empty |
| `capybee-waving.png` | SCR-01, SCR-03 (first visit), SCR-10 |
| `capybee-empathetic.png` | SCR-03 (heavy reaction), SCR-09 old world empty |
| `capybee-celebrating.png` | SCR-03 (good reaction), SCR-06 completion, SCR-07 history items, SCR-08 entry created, SCR-09 memory saved |
| `capybee-suggesting.png` | SCR-03 (mission suggestion card) |
| `capybee-face-happy.png` | SCR-06 header, SCR-07 list items, SCR-08 header |
| `capybee-face-okay.png` | SCR-03 (okay reaction) |
| `capybee-face-sad.png` | Not currently placed — reserved for future heavy-mood dedicated screen |
| `capybee-mood-heavy.png` | Mood selector card — heavy option |
| `capybee-mood-okay.png` | Mood selector card — okay option |
| `capybee-mood-good.png` | Mood selector card — good option |
