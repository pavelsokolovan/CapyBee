# 19 — Missions List: Accordion Rows (Compact Collapsed State)

## Purpose

Implementation instructions to reimplement the mission list (`SCR-06 Missions List`) as **accordion rows**.

**Design goal (from product):**

> Accordion row. Fully collapsed to one line (icon + title + time chip + chevron).  
> Tapping anywhere expands it in place to reveal the complete button, "not today" link, and an optional note field.  
> Most compact at rest, one extra tap to act.

This supersedes the always-expanded card layout from `08-missions-redesign-instruction.md` while preserving:

- CapyBee face icon
- Time hint chip
- Primary “Mark complete” action
- Secondary “Not today” skip flow (with acknowledgment + undo)
- Per-mission optional note (shown only when expanded / during completion)
- Category filter chips (from `18-missions-categories-and-expansion-instruction.md`)
- No competitive ranking, no skip counters visible to child or parent

Applies to: `SCR-06 - Missions List` (`/missions` and the Missions tab inside authenticated home).

Related docs:  
`04-screen-descriptions.md`, `08-missions-redesign-instruction.md`, `18-missions-categories-and-expansion-instruction.md`, `06-capybee-phrases-instruction.md`.

---

## 1. Problem Being Fixed

Current / previous state after earlier redesigns:

- Cards are relatively tall even when the child is only browsing.
- Primary actions and the note field take vertical space on every row.
- On a phone with ~20–45 missions the list becomes long and tiring to scan.

Target state:

- **Default (collapsed)**: every mission is a single compact row.
- **Expanded**: only the row the child taps grows in place and reveals the actions + optional note.
- Only one row should be expanded at a time (accordion behaviour).
- Collapsed height is minimal so many missions fit above the fold / require less scrolling.
- The child still needs only one extra tap to act (expand → complete / skip).

---

## 2. Visual & Interaction Specification

### 2.1 Collapsed row (default / rest state)

Single horizontal line, full width of the list container:

```
[ CapyBee face icon ]  Title text (truncated if needed)     [ time chip ]  [chevron]
```

Layout details:

| Element            | Spec                                                                 |
|--------------------|----------------------------------------------------------------------|
| Container          | Rounded rectangle (≈ `rounded-2xl`), soft border or very light honey background, subtle shadow. Matches the soft yellow/cream card seen in the reference screenshot. |
| Icon               | Face-only CapyBee avatar, 32–36 px, inside a soft circular or rounded background (yellow/honey). Left-aligned. |
| Title              | One line, medium weight, primary text colour. Truncate with ellipsis if it would wrap. |
| Time chip          | Pill on the right side of the title area. Example values: `2 min`, `1 min`, `5 min` (or the existing `timeHint` string shortened). Soft background, secondary text colour. |
| Chevron            | Small down-pointing chevron when collapsed, up-pointing when expanded. Rightmost element. Colour matches secondary text. |
| Height             | Compact — approximately 56–64 px total including padding. |
| Touch target       | The **entire row** is tappable to expand/collapse. Minimum 44 px height. |

No “Mark complete” button, no note field, and no “Not today” link are visible while collapsed.

### 2.2 Expanded row

When the user taps a collapsed row it expands **in place** (height transition). The header stays the same (icon + title + time chip + now-up chevron). Below the header appear, in order:

1. Optional note field  
   - Placeholder: “Opcjonalna notatka…” / “Optional note…” (or the existing bilingual string).  
   - Multi-line textarea, soft border, light background.  
   - Visible immediately on expand so the child can type a note before completing.

2. Primary button  
   - Full-width (or nearly full-width) rounded pill button.  
   - Label: “Oznacz jako ukończone” / “Mark complete”.  
   - Strong honey / gold fill, white or dark text for contrast.

3. Secondary action  
   - Text link centred or left-aligned under the button: “Nie dzisiaj” / “Not today”.  
   - Underlined or clearly tappable, secondary colour.  
   - Must still meet 44×44 px touch target.

Visual hierarchy inside the expanded card:

```
┌─────────────────────────────────────────────┐
│ [icon]  Title text…               [2 min] ▴ │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │ Opcjonalna notatka...                   │ │
│ │                                         │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│ ┌─────────────────────────────────────────┐ │
│ │         Oznacz jako ukończone           │ │
│ └─────────────────────────────────────────┘ │
│                                             │
│              Nie dzisiaj                    │
└─────────────────────────────────────────────┘
```

### 2.3 Accordion behaviour rules

- Only **one** mission row may be expanded at a time.
- Tapping a different collapsed row:
  1. Collapses the currently expanded row (if any).
  2. Expands the newly tapped row.
- Tapping the already-expanded row (header area) collapses it again.
- Expanding / collapsing must animate height smoothly (≈ 200–300 ms). Honour `prefers-reduced-motion` → instant toggle.
- The list must not jump scroll position in a jarring way when a row expands; keep the expanded row in view if possible.

### 2.4 Completion flow (inside expanded row)

1. Child expands a mission.
2. (Optional) types a note.
3. Taps “Oznacz jako ukończone” / “Mark complete”.
4. On success:
   - Show a brief positive CapyBee reaction (cheer expression or short phrase from the existing completion pool).
   - Collapse / remove the row with a short animation (or replace content momentarily with a success state then fold away).
   - Refresh the list (mission is no longer in the active suggested set for today, or is marked completed according to existing backend rules).

### 2.5 “Not today” / Skip flow

Exactly the same product rules as defined in `08-missions-redesign-instruction.md` §3:

- No confirmation dialog.
- On tap → replace card content (or show overlay) with a short CapyBee acknowledgment phrase from the `missionSkip` pool.
- Then collapse / remove the row.
- Show a 3–4 s “Undo” toast.
- Record the interaction in `mission_interactions` (action = `skipped`), never surface skips to the parent history view.
- Undo restores the row and records `undone` (or removes the skip row).

The only change is that the “Not today” control is only visible when the row is expanded.

---

## 3. Component Structure (React + Tailwind sketch)

Suggested component shape (adapt to existing `AuthenticatedHome.tsx` / mission components):

```tsx
interface MissionAccordionRowProps {
  mission: Mission;
  isExpanded: boolean;
  onToggle: () => void;
  onComplete: (missionId: string, note: string) => void;
  onSkip: (missionId: string) => void;
  // locale strings, etc.
}

function MissionAccordionRow({
  mission,
  isExpanded,
  onToggle,
  onComplete,
  onSkip,
}: MissionAccordionRowProps) {
  const [note, setNote] = useState('');

  return (
    <div
      className={cn(
        'rounded-2xl border border-honey-100 bg-white/90 shadow-sm transition-all',
        'overflow-hidden'
      )}
    >
      {/* Collapsed / header – always visible, tappable */}
      <button
        type="button"
        onClick={onToggle}
        className="flex w-full items-center gap-3 px-4 py-3 text-left min-h-[56px]"
        aria-expanded={isExpanded}
      >
        <img
          src={mission.capyIconSrc /* or shared face asset */}
          alt=""
          className="h-9 w-9 flex-shrink-0 rounded-full"
          aria-hidden="true"
        />
        <span className="flex-1 truncate text-base font-medium text-brown-900">
          {mission.title}
        </span>
        <span className="flex-shrink-0 rounded-full bg-honey-50 px-2.5 py-0.5 text-xs text-brown-500">
          {mission.timeHint /* e.g. "2 min" */}
        </span>
        <ChevronIcon direction={isExpanded ? 'up' : 'down'} className="ml-1 h-4 w-4 text-brown-400" />
      </button>

      {/* Expanded body */}
      {isExpanded && (
        <div className="space-y-3 border-t border-honey-50 px-4 pb-4 pt-3">
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder={/* "Opcjonalna notatka..." / "Optional note..." */}
            rows={2}
            className="w-full resize-none rounded-xl border border-honey-100 bg-white p-3 text-sm"
          />

          <button
            type="button"
            onClick={() => onComplete(mission.id, note)}
            className="w-full rounded-full bg-honey-500 py-3 text-sm font-medium text-white shadow-sm"
          >
            {/* "Oznacz jako ukończone" / "Mark complete" */}
          </button>

          <button
            type="button"
            onClick={() => onSkip(mission.id)}
            className="mx-auto block min-h-[44px] text-sm text-brown-500 underline-offset-2 hover:underline"
          >
            {/* "Nie dzisiaj" / "Not today" */}
          </button>
        </div>
      )}
    </div>
  );
}
```

Parent list responsibility:

```tsx
const [expandedId, setExpandedId] = useState<string | null>(null);

// inside the map:
<MissionAccordionRow
  key={mission.id}
  mission={mission}
  isExpanded={expandedId === mission.id}
  onToggle={() =>
    setExpandedId((prev) => (prev === mission.id ? null : mission.id))
  }
  onComplete={handleComplete}
  onSkip={handleSkip}
/>
```

---

## 4. Layout & Spacing Guidelines

- Vertical gap between accordion rows: 10–12 px.
- Outer list padding consistent with the rest of the app (usually 16 px horizontal).
- Category chip row (from spec 18) stays **above** the accordion list, unchanged.
- Empty state and loading state remain as already specified.
- On very small screens the time chip may become slightly smaller; never drop the icon or the chevron.

---

## 5. Accessibility

- The header button must have `aria-expanded={true|false}`.
- When expanded, the note field and action buttons should be reachable by keyboard / screen reader in natural order.
- “Not today” must remain a real button (or link with button role) so it receives focus.
- Announce completion and skip success via a polite live region or the existing toast pattern.
- Respect `prefers-reduced-motion` for expand/collapse and fold-away animations.

---

## 6. Data & API Impact

No backend changes required for the accordion itself.

- Completion still uses the existing `POST /api/missions/{id}/completions` (with optional note).
- Skip still uses the endpoints and `mission_interactions` table defined in spec 08.
- Category filtering and ordering from spec 18 continue to work unchanged — the accordion is only a presentation change of the same `visibleMissions` array.

---

## 7. Acceptance Checklist

- [ ] Every mission renders as a single-line collapsed row by default (icon + title + time chip + chevron).
- [ ] Tapping anywhere on the collapsed row expands it in place.
- [ ] Only one row can be expanded at a time.
- [ ] Expanded state shows: optional note textarea, full-width “Mark complete” button, “Not today” link.
- [ ] Tapping the expanded header collapses the row again.
- [ ] Height animation is smooth; reduced-motion users get an instant toggle.
- [ ] Completing a mission records the note (if any) and removes/folds the row with positive feedback.
- [ ] “Not today” follows the skip + acknowledgment + undo rules from spec 08; skips never appear in parent history.
- [ ] Category filter chips still work and filter the accordion list.
- [ ] List remains compact enough that many missions are visible without excessive scrolling on a typical phone.
- [ ] Touch targets ≥ 44×44 px for all interactive elements.
- [ ] Visual style (soft yellow/cream cards, honey accents, CapyBee face icon) matches the reference screenshot and existing design language.

---

## 8. Implementation Notes for Copilot / Developers

1. Prefer extracting `MissionAccordionRow` into its own component so the list stays readable.
2. Keep the existing bilingual strings; only the layout changes.
3. Re-use the CapyBee face asset already defined for mission cards.
4. When a mission is completed or skipped, clear `expandedId` if it pointed at that mission.
5. After a successful completion, the list should re-fetch or optimistically remove the mission according to current product rules (mission no longer shown as available for today).
6. Do not re-introduce a global note field at the top of the page.

---

*This specification turns the mission list into the most compact “browse → one-tap expand → act” pattern while preserving every product rule already defined for completion, skipping, categories, and tone.*
