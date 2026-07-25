# 09 — Friendship Tracker Improvements (SCR-08)

## Purpose

Implementation-ready instructions to fix the Friendship Tracker screen (`/friendships`) so it stops reading like a raw CRUD form and starts feeling like part of the CapyBee companion journey. Covers: enum-to-language mapping, a visual stage selector, celebration toast (message + avatar + animation), differentiated entry cards, softened delete action, and an empty state.

Applies to `SCR-08` as defined in `04-screen-descriptions.md`, and touches the same `useCapyBeePhrase` hook and avatar system defined in `05-avatars-adding-instruction.md` / `06-capybee-phrases-instruction.md`.

Reference screenshots: current implementation shows the `stage` field rendering the raw API enum value (e.g. `want_to_know_better`, `noticed`) directly in a plain `<select>`, entry cards with no visual differentiation, a harsh red "Delete" button, and a static, non-personalized toast ("The hive knows. 🍯") after adding an entry.

---

## 1. Stage enum → display language (no raw enums in UI, ever)

### Problem
`Stage` dropdown and entry card badges currently render backend enum values verbatim: `noticed`, `was_nice`, `talked`, `want_to_know_better`. This breaks tone consistency with the rest of the app.

### Fix
Create a single source of truth mapping enum → bilingual label + color token + icon. Do not let any component read the raw enum for display purposes — always go through this map.

```ts
// src/constants/friendshipStages.ts

export type FriendshipStage = "noticed" | "was_nice" | "talked" | "want_to_know_better";

export const FRIENDSHIP_STAGES: FriendshipStage[] = [
  "noticed",
  "was_nice",
  "talked",
  "want_to_know_better",
];

export const STAGE_META: Record<FriendshipStage, {
  labelKey: string;       // i18n key, resolved via existing locale files
  icon: string;           // emoji or icon component name
  colorToken: string;     // CSS var, matches honeycomb palette
  avatarExpression: string; // maps to full-body avatar set from 05-avatars-adding-instruction.md
}> = {
  noticed: {
    labelKey: "friendship.stage.noticed",
    icon: "👀",
    colorToken: "var(--cell-empty-active)", // dashed cream, filled
    avatarExpression: "curious",
  },
  was_nice: {
    labelKey: "friendship.stage.wasNice",
    icon: "🙂",
    colorToken: "var(--cell-checkin)", // honey yellow
    avatarExpression: "warm-smile",
  },
  talked: {
    labelKey: "friendship.stage.talked",
    icon: "💬",
    colorToken: "var(--cell-new-world)", // fresh green
    avatarExpression: "excited",
  },
  want_to_know_better: {
    labelKey: "friendship.stage.wantToKnowBetter",
    icon: "🤝",
    colorToken: "var(--cell-new-world-deep)", // deeper fresh green
    avatarExpression: "hopeful",
  },
};
```

### Locale entries to add

```json
// en.json
"friendship": {
  "stage": {
    "noticed": "Noticed them",
    "wasNice": "They were nice to me",
    "talked": "We talked",
    "wantToKnowBetter": "Want to know them better"
  }
}
```

```json
// pl.json
"friendship": {
  "stage": {
    "noticed": "Zauważyłem/-am",
    "wasNice": "Był/a dla mnie miły/a",
    "talked": "Porozmawialiśmy",
    "wantToKnowBetter": "Chcę go/ją lepiej poznać"
  }
}
```

Note: Polish stage labels should stay gender-neutral where possible for the child's own phrasing (avoid forcing "-łem" vs "-łam"); if the UI needs a single string, prefer the neutral construction the team already uses elsewhere (check `06-capybee-phrases-instruction.md` for existing gender-neutral patterns and match them).

**Any existing component or API response mapping that displays `stage` as text must be updated to go through `STAGE_META[stage].labelKey` — grep the codebase for direct renders of the `stage` field and replace.**

---

## 2. Stage selector — visual progression arc, not a `<select>`

### Replace
The current plain dropdown with a horizontal 4-step illustrated selector, consistent with the honeycomb visual language (dashed cream → honey yellow → fresh green → deep green, per `STAGE_META.colorToken`).

### Component spec

```
FriendshipStageSelector
├── props: value: FriendshipStage, onChange: (stage: FriendshipStage) => void
├── renders 4 tappable step chips in a row, in FRIENDSHIP_STAGES order
├── each chip: icon (STAGE_META.icon) + short label (STAGE_META.labelKey)
├── selected chip: filled background = STAGE_META.colorToken, scale 1.05
├── unfilled chips: outline only, muted
├── tapping any chip selects it directly (not sequential-only — a parent can
│   jump straight to "want to know better" if that's the accurate stage)
├── steps before the selected one show a filled connector line between them
│   (progression arc feel), steps after stay unfilled
```

Layout: single row on screens ≥360px, wrap to 2x2 grid only if text labels overflow at 360px — test at that width per `04-screen-descriptions.md` mobile-first requirement.

Touch target: each chip minimum 44×44px per existing non-functional UX requirements.

```jsx
// FriendshipStageSelector.jsx (structure only, style with existing Tailwind tokens)
{FRIENDSHIP_STAGES.map((stage, i) => (
  <button
    key={stage}
    type="button"
    onClick={() => onChange(stage)}
    aria-pressed={value === stage}
    className={`stage-chip ${value === stage ? "stage-chip--active" : ""}`}
    style={value === stage ? { backgroundColor: STAGE_META[stage].colorToken } : undefined}
  >
    <span className="stage-chip__icon">{STAGE_META[stage].icon}</span>
    <span className="stage-chip__label">{t(STAGE_META[stage].labelKey)}</span>
  </button>
))}
```

---

## 3. Celebration toast — dynamic message + avatar + animation

### 3a. Phrase pools

Add a new phrase pool to the existing bilingual phrase library (`06-capybee-phrases-instruction.md` pattern), keyed by stage, 3 variants each to avoid repetition. `{name}` is interpolated from the `Person` field value.

```json
// friendshipAddedPhrases.json
{
  "noticed": {
    "en": [
      "{name} noticed you — that matters!",
      "You noticed {name}. Small step, real step.",
      "{name} is on your radar now. Nice."
    ],
    "pl": [
      "{name} cię zauważył — to się liczy!",
      "Zauważyłeś {name}. Mały krok, ale prawdziwy.",
      "{name} już jest na twojej mapie. Super."
    ]
  },
  "was_nice": {
    "en": [
      "{name} was nice to you. That's worth remembering.",
      "A kind moment with {name} — keep that one.",
      "{name} made things a little easier today."
    ],
    "pl": [
      "{name} był dla ciebie miły. Warto to zapamiętać.",
      "Miła chwila z {name} — zatrzymaj ją.",
      "{name} sprawił, że dziś było trochę łatwiej."
    ]
  },
  "talked": {
    "en": [
      "You talked to {name}! That takes courage.",
      "A real conversation with {name}. Growing.",
      "You and {name} talked. That's a real connection starting."
    ],
    "pl": [
      "Porozmawiałeś z {name}! To wymaga odwagi.",
      "Prawdziwa rozmowa z {name}. Rośniesz.",
      "Ty i {name} porozmawialiście. To początek czegoś prawdziwego."
    ]
  },
  "want_to_know_better": {
    "en": [
      "You want to know {name} better — your hive is building.",
      "{name} might be someone special. Let's see.",
      "That's a real friendship spark with {name}."
    ],
    "pl": [
      "Chcesz lepiej poznać {name} — twój ul rośnie.",
      "{name} może być kimś ważnym. Zobaczymy.",
      "To prawdziwa iskra przyjaźni z {name}."
    ]
  }
}
```

### Hook usage

```ts
// extends existing useCapyBeePhrase hook
function useFriendshipAddedPhrase(stage: FriendshipStage, name: string, locale: "en" | "pl") {
  const pool = friendshipAddedPhrases[stage][locale];
  const raw = pool[Math.floor(Math.random() * pool.length)];
  return raw.replace("{name}", name);
}
```

### 3b. Avatar mapping

Use `STAGE_META[stage].avatarExpression` to select from the existing full-body 8-expression avatar set (per `05-avatars-adding-instruction.md`):

| Stage | Expression | Existing avatar asset key |
|---|---|---|
| `noticed` | curious / interested | `capybee_curious_400.png` |
| `was_nice` | warm smile | `capybee_warm-smile_400.png` |
| `talked` | excited / celebratory | `capybee_excited_400.png` |
| `want_to_know_better` | hopeful / determined | `capybee_hopeful_400.png` |

If any of these four expressions don't already exist in the 8-expression set, fall back to the closest match (`warm-smile` is the safe default) rather than blocking implementation — flag the gap for a follow-up asset request instead of stalling this ticket.

### 3c. Animation (Framer Motion)

Replace the current static toast with a spring-in slide + avatar wiggle. No new dependencies — `framer-motion` is already in the stack.

```jsx
// FriendshipToast.jsx
import { motion, AnimatePresence } from "framer-motion";

function FriendshipToast({ toast, onDismiss }) {
  useEffect(() => {
    if (!toast) return;
    const timer = setTimeout(onDismiss, 4000);
    return () => clearTimeout(timer);
  }, [toast]);

  return (
    <AnimatePresence>
      {toast && (
        <motion.div
          initial={{ opacity: 0, y: 40 }}
          animate={{ opacity: 1, y: 0 }}
          exit={{ opacity: 0, y: 20 }}
          transition={{ type: "spring", stiffness: 300, damping: 24 }}
          className="capybee-toast"
        >
          <motion.img
            src={toast.avatarSrc}
            alt=""
            animate={{ rotate: [0, -8, 8, -4, 0] }}
            transition={{ duration: 0.6, delay: 0.15 }}
            className="capybee-toast__avatar"
          />
          <span>{toast.message}</span>
        </motion.div>
      )}
    </AnimatePresence>
  );
}
```

Optional, secondary polish (implement only if time allows — not a blocker): animate the corresponding honeycomb cell filling in on the same screen, if the honeycomb widget is mounted on this page or on Home after navigating back.

```jsx
<motion.polygon
  initial={{ scale: 0.8, fill: "var(--cell-empty)" }}
  animate={{ scale: 1, fill: STAGE_META[stage].colorToken }}
  transition={{ duration: 0.5, ease: "easeOut" }}
/>
```

### 3d. Trigger wiring

On successful `POST /api/friendships` (or `PATCH` when a stage is updated):
1. Resolve `message` via `useFriendshipAddedPhrase(stage, personName, locale)`.
2. Resolve `avatarSrc` via `STAGE_META[stage].avatarExpression` lookup.
3. Set toast state → `FriendshipToast` renders and auto-dismisses after 4s.
4. Do **not** block the form reset or list refresh on the toast animation — they proceed independently.

---

## 4. Entry list cards — stage-differentiated

### Problem
All cards in the list render identically regardless of stage (see current implementation — `person1`, `osoba4`, `osoba3` all look the same weight, only the `noticed` badge text differs).

### Fix
Add a left border (4px) and small stage icon to each card, colored via `STAGE_META[stage].colorToken`. Replace the current badge text (which reads raw-ish, e.g. `noticed`) with the localized label from Section 1.

```jsx
<div
  className="friendship-card"
  style={{ borderLeft: `4px solid ${STAGE_META[entry.stage].colorToken}` }}
>
  <div className="friendship-card__header">
    <span className="friendship-card__icon">{STAGE_META[entry.stage].icon}</span>
    <strong>{entry.personLabel}</strong>
    <span className="friendship-card__badge">{t(STAGE_META[entry.stage].labelKey)}</span>
  </div>
  <p className="friendship-card__note">{entry.note}</p>
  <button className="friendship-card__remove" onClick={() => handleRemove(entry.id)}>
    {t("friendship.remove")}
  </button>
</div>
```

### Note field placeholder
Add a supportive placeholder to reduce blank-field freeze:
- EN: `"What do you remember about them?"`
- PL: `"Co o nich pamiętasz?"`

---

## 5. Soften the delete action

### Problem
Current "Delete" button uses a red outline — reads as alarming/punitive for a children's app, especially for something as gentle as a friendship note.

### Fix
- Relabel: `"Delete"` → `"Remove"` (EN) / `"Usuń"` stays acceptable in PL but pair with neutral styling, not red.
- Style: replace red outline with neutral gray/brown outline (`var(--text-muted)` or equivalent existing neutral token — do not introduce a new color).
- Add an undo affordance: on tap, don't delete immediately — show a brief inline toast: *"Removed. [Undo]"* (EN) / *"Usunięto. [Cofnij]"* (PL), 5-second window before the `DELETE /api/friendships/{entryId}` call actually fires.

```jsx
function handleRemoveClick(entryId) {
  setPendingDelete(entryId);
  setUndoToast(true);
  deleteTimerRef.current = setTimeout(() => {
    api.deleteFriendship(entryId);
    setUndoToast(false);
  }, 5000);
}

function handleUndo() {
  clearTimeout(deleteTimerRef.current);
  setPendingDelete(null);
  setUndoToast(false);
}
```

Optimistically hide (fade out, don't fully remove from DOM) the card during the undo window rather than instantly vanishing it.

---

## 6. Empty state

### Problem
No CapyBee-voiced empty state currently exists for a first-time-use friendship list (zero entries).

### Fix
When `GET /api/friendships` returns an empty list, render:

```jsx
<div className="friendship-empty-state">
  <img src="capybee_hopeful_200.png" alt="" />
  <p>{locale === "pl"
    ? "Nikogo tu jeszcze nie ma — to nic. Znajdziemy twoją paczkę."
    : "No one here yet — that's okay. We'll find your people."}
  </p>
</div>
```

Matches the tone of existing empty states referenced in `04-screen-descriptions.md` ("Loading and empty states are always explicit; no blank screens").

---

## 7. Out of scope for this pass

- Honeycomb avatar variety (repeated capybara face across cells) — confirmed as test data, no change needed here.
- Stage-selector sequential-lock behavior (i.e. forcing users through steps in order) — deliberately not implemented; a parent/child should be able to select any stage directly since real friendships don't always progress linearly.

---

## Traceability

- Addresses SCR-08 (Friendship Tracker) from `04-screen-descriptions.md`.
- Supports US-012, US-013 (friendship tracker stories) from `01-user-stories.md`, specifically the acceptance criteria "the UI shows that every stage counts" and "the app does not rank children or compare them to others."
- Tone requirements per US-017 (gentle, non-lecturing, validates before acting).
- Reuses phrase-pool and avatar-mapping infrastructure from `06-capybee-phrases-instruction.md` and `05-avatars-adding-instruction.md` rather than introducing parallel systems.
