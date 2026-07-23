# 07 — Missions Redesign: Visual Hierarchy, Skip Flow, New Mission Set

## Purpose

Implementation instructions for GitHub Copilot to redesign `SCR-06 Missions List`.
Covers: card visual hierarchy, the "not today" (skip) interaction and its acknowledgment
flow, relocation of the completion-note field, and 20 new missions to seed the database.

Applies to: `SCR-06 - Missions List` (`/missions`).
Related docs: `04-screen-descriptions.md`, `05-avatars-adding-instruction.md`,
`06-capybee-phrases-instruction.md`.

---

## 1. Problem Being Fixed

Current state:
- All mission cards look identical — no visual hierarchy, no sense of effort/size.
- Missions can be skipped per spec (US-009: *"The mission can be skipped without
  punishment"*), but no skip control exists in the UI.
- A "Completion note" text field sits at the top of the page, disconnected from any
  specific mission — confusing, and implies the note applies globally.

Target state:
- Each card shows a CapyBee icon, a short difficulty/time hint, a primary "Mark complete"
  action, and a secondary "Not today" link.
- Tapping "Mark complete" reveals the note field *inside that card*, not before.
- Tapping "Not today" produces a warm, brief CapyBee acknowledgment, then the mission
  folds back into the pool — with an undo window. No confirmation dialog, no reason
  required, no visible skip count anywhere a parent can see it.

---

## 2. Mission Card — Visual Hierarchy

Each card in the `MissionsList` component should render, top to bottom:

1. **CapyBee icon** — small face-only avatar (use the 200×200 face-only variant per
   `05-avatars-adding-instruction.md`), 32–40px, top-left of card. Neutral/calm
   expression by default; swap to the "cheer" expression on completion.
2. **Mission title** — short, one line, primary text weight.
3. **Difficulty/time hint** — small pill/badge, secondary text color. Not a number score —
   phrase it as time, e.g. `"this takes 1 minute"`. Never use words like "easy"/"hard" as
   a label (that invites comparison and shame); time is a neutral, honest signal.
4. **Primary action** — `Mark complete` button, full-width or prominent.
5. **Secondary action** — `Not today` as a plain text link, smaller and lower visual
   weight than the primary button. Must still meet the 44×44px touch target minimum
   even though it's styled as a link.

Do not use color, size, or badges to rank missions against each other (no "hard" vs
"easy" color coding, no streak/competition indicators — this violates the
no-competitive-UI principle in `04-screen-descriptions.md`).

### Component sketch (React + Tailwind)

```jsx
function MissionCard({ mission, onComplete, onSkip }) {
  const [showNoteField, setShowNoteField] = useState(false);
  const [note, setNote] = useState("");

  return (
    <div className="rounded-2xl border border-honey-100 bg-white p-4 shadow-sm">
      <div className="flex items-start gap-3">
        <img
          src={mission.capyIconSrc}
          alt=""
          className="h-9 w-9 flex-shrink-0"
          aria-hidden="true"
        />
        <div className="flex-1">
          <h3 className="text-base font-medium text-brown-900">{mission.title}</h3>
          <span className="mt-1 inline-block rounded-full bg-honey-50 px-2 py-0.5 text-xs text-brown-500">
            {mission.timeHint /* e.g. "this takes 1 minute" */}
          </span>
        </div>
      </div>

      {!showNoteField ? (
        <div className="mt-3 flex items-center justify-between">
          <button
            onClick={() => setShowNoteField(true)}
            className="rounded-full bg-honey-400 px-4 py-2 text-sm font-medium text-white"
          >
            Mark complete
          </button>
          <button
            onClick={() => onSkip(mission.id)}
            className="min-h-[44px] min-w-[44px] text-sm text-brown-400 underline-offset-2 hover:underline"
          >
            Not today
          </button>
        </div>
      ) : (
        <div className="mt-3 space-y-2">
          <textarea
            value={note}
            onChange={(e) => setNote(e.target.value)}
            placeholder="Anything you want to remember? (optional)"
            className="w-full rounded-lg border border-honey-100 p-2 text-sm"
            rows={2}
          />
          <div className="flex gap-2">
            <button
              onClick={() => onComplete(mission.id, note)}
              className="rounded-full bg-honey-500 px-4 py-2 text-sm font-medium text-white"
            >
              Save
            </button>
            <button
              onClick={() => setShowNoteField(false)}
              className="px-3 py-2 text-sm text-brown-400"
            >
              Back
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
```

**Remove** the standalone completion-note field previously rendered at the top of
`/missions`. The note is per-mission and only appears after `Mark complete` is tapped,
scoped to that card.

---

## 3. Skip ("Not today") Flow

### 3.1 Interaction sequence

1. Child taps `Not today`.
2. **No confirmation dialog.** Same friction as completing — a confirmation would imply
   skipping is a mistake.
3. Card content is replaced in place (same card bounds, ~1.5s) with a CapyBee face-only
   avatar + one short acknowledgment line, randomly picked from the skip phrase pool
   (Section 4).
4. Card then folds/collapses out of the list (height transition, ~300ms, respect
   `prefers-reduced-motion`).
5. A toast/snackbar appears for 3–4 seconds: `Undo`. Tapping it restores the card to its
   original position with no data recorded.
6. If not undone, the mission returns to the general pool (not removed from the system) —
   it may resurface on a later day, not necessarily "tomorrow," so it doesn't read as a
   queue the child owes.

### 3.2 What must NOT happen

- No required reason field, no dropdown, no "why did you skip?" prompt.
- No skip counter, streak break, or "missed" indicator shown anywhere — including
  `SCR-07 Mission Completion History`, which is parent-facing. Skips are never surfaced
  to the parent view.
- No automatic substitution of a "better" or harder mission immediately after a skip.
- No visual difference in how the *next* card set is generated based on a single skip.

### 3.3 Data model

Add a lightweight interaction log, separate from `mission_completions`, so skip data can
inform future suggestion logic without ever being exposed in parent-facing UI:

```sql
CREATE TABLE mission_interactions (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  child_profile_id UUID NOT NULL REFERENCES child_profiles(id),
  mission_id UUID NOT NULL REFERENCES missions(id),
  action VARCHAR(16) NOT NULL CHECK (action IN ('completed', 'skipped', 'undone')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

### 3.4 API

```
POST /api/missions/{missionId}/skip
  → 201, records a 'skipped' mission_interactions row, mission stays active in pool

POST /api/missions/{missionId}/skip/undo
  → 200, records an 'undone' mission_interactions row (or deletes the prior 'skipped' row —
    pick one approach and keep it consistent), no visible effect on mission state
```

Do **not** add a `skip_count` field to any response consumed by
`GET /api/missions/completions` (parent-facing). Keep skip data isolated to
`mission_interactions`, queried only by internal suggestion logic.

---

## 4. Skip Acknowledgment Phrase Pool

Add to the existing bilingual phrase system from `06-capybee-phrases-instruction.md` as a
new pool: `missionSkip` (8 phrases, EN + PL, rotate non-repeating like other pools).

```json
{
  "missionSkip": [
    { "en": "Okay. Not every day is a mission day.", "pl": "Okej. Nie każdy dzień musi być na misję." },
    { "en": "No worries — it'll be here tomorrow.", "pl": "Spoko, wróci innym razem." },
    { "en": "That's fine. You don't owe me this one.", "pl": "W porządku. Nie musisz mi nic udowadniać." },
    { "en": "Skipping counts as taking care of yourself too.", "pl": "Odpuszczenie sobie też czasem znaczy dbać o siebie." },
    { "en": "Some days just aren't mission days. That's okay.", "pl": "Są dni bez misji. To normalne." },
    { "en": "Noted. I'm not going anywhere.", "pl": "Zanotowane. Nigdzie się nie wybieram." },
    { "en": "Fair enough. Rest counts too.", "pl": "Jasne. Odpoczynek też się liczy." },
    { "en": "All good. We'll try again another day.", "pl": "Wszystko gra. Spróbujemy innym razem." }
  ]
}
```

Wire via the existing `useCapyBeePhrase("missionSkip")` hook pattern.

---

## 5. Twenty New Missions — Seed Data

Insert into the `missions` table. `time_hint` is the literal display string used in the
card badge (Section 2). `category` is for internal grouping/suggestion logic only —
never rendered as a label to the child.

```sql
INSERT INTO missions (title_en, title_pl, time_hint_en, time_hint_pl, category, active) VALUES
('Say hi to someone new at school', 'Powiedz "cześć" komuś nowemu w szkole', 'this takes 1 minute', 'to zajmie 1 minutę', 'social', true),
('Ask someone what their favorite subject is', 'Zapytaj kogoś, jaki jest jego ulubiony przedmiot', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true),
('Sit somewhere new at lunch', 'Usiądź dziś w nowym miejscu na obiedzie', 'this takes 1 minute', 'to zajmie 1 minutę', 'social', true),
('Compliment someone on something small', 'Powiedz komuś coś miłego', 'this takes 1 minute', 'to zajmie 1 minutę', 'social', true),
('Learn one new word in the local language', 'Naucz się jednego nowego słowa w lokalnym języku', 'this takes 3 minutes', 'to zajmie 3 minuty', 'new_world', true),
('Find one thing near your home you didn''t notice before', 'Znajdź coś w okolicy, czego wcześniej nie zauważyłeś', 'this takes 5 minutes', 'to zajmie 5 minut', 'new_world', true),
('Try one food you haven''t tried here yet', 'Spróbuj jednego nowego jedzenia', 'this takes 2 minutes', 'to zajmie 2 minuty', 'new_world', true),
('Ask a teacher one question after class', 'Zadaj nauczycielowi jedno pytanie po lekcji', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true),
('Draw or describe your old street from memory', 'Narysuj lub opisz z pamięci swoją starą ulicę', 'this takes 5 minutes', 'to zajmie 5 minut', 'old_world', true),
('Write down a smell or sound that reminds you of home', 'Zapisz zapach lub dźwięk, który przypomina ci dom', 'this takes 2 minutes', 'to zajmie 2 minuty', 'old_world', true),
('Message an old friend just to say hi', 'Napisz do starego przyjaciela, żeby się przywitać', 'this takes 3 minutes', 'to zajmie 3 minuty', 'old_world', true),
('Name one thing that''s easier here than back home', 'Znajdź jedną rzecz, która tu jest łatwiejsza niż w domu', 'this takes 2 minutes', 'to zajmie 2 minuty', 'reflection', true),
('Look up one club or activity at your school', 'Sprawdź jedno kółko lub zajęcia w szkole', 'this takes 3 minutes', 'to zajmie 3 minuty', 'exploration', true),
('Ask someone to explain a game or rule you don''t know', 'Poproś kogoś, żeby wytłumaczył ci grę lub zasadę', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true),
('Take a photo of something that made today okay', 'Zrób zdjęcie czegoś, co sprawiło, że dziś było okej', 'this takes 1 minute', 'to zajmie 1 minutę', 'reflection', true),
('Wave or smile at the same person two days in a row', 'Uśmiechnij się do tej samej osoby dwa dni z rzędu', 'this takes 1 minute', 'to zajmie 1 minutę', 'social', true),
('Ask your family to cook something from home together', 'Zaproponuj rodzinie ugotowanie czegoś z domu', 'this takes 10 minutes', 'to zajmie 10 minut', 'old_world', true),
('Find out what sport is popular at your new school', 'Dowiedz się, jaki sport jest popularny w nowej szkole', 'this takes 3 minutes', 'to zajmie 3 minuty', 'new_world', true),
('Write one sentence about how today actually felt', 'Napisz jedno zdanie o tym, jak naprawdę było dziś', 'this takes 1 minute', 'to zajmie 1 minutę', 'reflection', true),
('Offer to help a classmate with something small', 'Zaoferuj pomoc koledze lub koleżance w czymś drobnym', 'this takes 2 minutes', 'to zajmie 2 minuty', 'social', true);
```

If `missions` currently has no `category`, `time_hint_en`, or `time_hint_pl` columns, add
them via migration before running the seed:

```sql
ALTER TABLE missions
  ADD COLUMN IF NOT EXISTS time_hint_en VARCHAR(64),
  ADD COLUMN IF NOT EXISTS time_hint_pl VARCHAR(64),
  ADD COLUMN IF NOT EXISTS category VARCHAR(32);
```

---

## 6. Acceptance Checklist

- [ ] Every mission card shows: CapyBee icon, title, time hint, `Mark complete`,
      `Not today` — no card lacks any of these.
- [ ] No two missions on screen are visually ranked against each other by color/size.
- [ ] Completion note field only appears inside a card, only after `Mark complete` is
      tapped; the old top-of-page field is removed.
- [ ] `Not today` requires no confirmation, shows a CapyBee acknowledgment line, then
      folds the card with a 3–4s `Undo` window.
- [ ] No skip count, streak-break, or "missed" indicator appears in any parent-facing
      view (`SCR-07`) or anywhere else.
- [ ] `mission_interactions` table exists and skip/undo events are recorded there, not in
      `mission_completions`.
- [ ] `missionSkip` phrase pool (8 EN/PL pairs) is wired via `useCapyBeePhrase`.
- [ ] 20 new missions are seeded with correct `time_hint` strings and are selectable by
      `GET /api/missions?active=true`.
- [ ] All touch targets, including the `Not today` link, meet 44×44px minimum.
- [ ] Reduced-motion preference disables the fold animation (instant removal instead).
