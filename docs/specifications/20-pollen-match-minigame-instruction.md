# 20 — Pollen Match Mini-Game

## Purpose

Add the first of a small family of short, non-competitive mini-games to
CapyBee: **Pollen Match**, a hex-tile memory-pairs game. A round takes
20–45 seconds, has no fail state and no comparison between children, and
optionally adds one filled cell to the child's honeycomb map on completion.

Traces back to the mini-game concept discussion (four options presented:
Pollen Match, Hive Builder Tap, Pollen Drift, Balloon Breath Round —
Pollen Match selected as the first to build).

## Ground truth reviewed

- `docs/specifications/07-honeycomb-cells-instruction.md` — cell color
  system, hex geometry, `HoneycombMap` component contract.
- `app/ui/src/hooks/useHoneycombCells.ts` — how filled cells are derived
  client-side from check-ins / missions / friendships / memories (no
  separate "cell" table in the database).
- `app/ui/src/AuthenticatedHome.tsx` — `moodSectionRef` / `hiveSectionRef`
  panel layout (~L1866–1912), `copy` bilingual object (~L439), `text.*`
  usage, `submitCheckIn` (~L1170–1197) as the reference optimistic-update +
  offline-queue pattern, `.suggestion-card` button pattern (~L1893).
- `app/ui/src/offline/queueStore.ts` — `QueueActionType` union;
  `app/ui/src/offline/syncEngine.ts` — generic path/method dispatch (no
  per-type branching needed).
- `app/ui/src/styles.css` — root tokens `--ink`, `--muted`, `--panel`,
  `--panel-border`, `--accent`, `--accent-strong`, `--shadow`; `.bottom-nav`
  / `.nav-item` (~L1556–1594); `.capybee-toast` overlay pattern (~L1141).
- `app/ui/tailwind.config.ts` — `honey` color scale (50–900).
- `app/server/.../domain/checkin/CheckInEntry.java`,
  `service/CheckInService.java`, `web/dto/CreateCheckInRequest.java` /
  `CheckInResponse.java` — reference entity/service/DTO pattern for a
  simple, parent-less write (closest existing analog to a game result).
- `app/server/.../web/ApiController.java` (~L121–134) — single-controller
  REST pattern, `requireOAuth2(authentication)` helper.
- `app/server/src/main/resources/db/migration/` — latest applied migration
  is `V10__widen_avatar_url.sql`; this feature adds `V11`.

## Constraints (from house rules)

- No PII, no real names.
- No social/competitive features — no leaderboard, no cross-child
  comparison, ever.
- Bilingual EN/PL required for every string.
- No padlocks, hearts, or human figures — hive/honeycomb visual language
  only. The mascot appears only as the existing `CapyBeeAvatar` assets,
  never a new character drawing.
- A round must be skippable/closeable at any time with no penalty or nag.
- Fly.io cost impact: negligible — one new small table, no new process.

## 1. UX flow

### Entry point: one "Play" button → a hub, not one card per game

**Decision (per Pavlo): a single "Play" entry point, opening a hub screen
that lists every available mini-game.** This replaces the earlier
one-card-per-game idea so the Home screen doesn't grow a new card every
time a mini-game ships.

A single new panel is added to Home (`SCR-03`), directly after the existing
hive section (`hiveSectionRef`) and before check-in history
(`checkInHistorySectionRef`):

```tsx
<section className="panel" ref={playSectionRef}>
  <h3>{text.playTitle}</h3>
  <button className="suggestion-card" onClick={() => setPlayHubOpen(true)}>
    <CapyBeeAvatar src={capyBeeAvatar.suggesting} size={96} />
    <span>{text.playHubPrompt}</span>
  </button>
</section>
```

Reuses the existing `.suggestion-card` style — no new CSS for the entry
point itself.

Tapping it opens the **Play hub** (`PlayHub.tsx`), a full-screen overlay
(same elevation/close-button pattern as the game overlay below) listing
every mini-game as a row: icon, name, one-line blurb, tap to start.

```tsx
const games: PlayHubEntry[] = [
  { key: 'pollen_match', icon: '🌼', title: text.pollenMatchTitle, blurb: text.pollenMatchBlurb, available: true },
  // future entries just get added here — no navigation changes needed:
  // { key: 'hive_builder_tap', icon: '🐝', ... , available: false }
];
```

In v1 only Pollen Match is `available: true`; the list component itself
doesn't need to change when the second game ships — only this array grows.

**Nav note:** the bottom nav is intentionally fixed at 4 equal tabs
(`12-navigation-redesign-instruction.md` explicitly fixed a 5-tab crowding
bug) — the Play hub is deliberately **not** a 5th bottom-nav tab, it's a
Home-launched overlay, like the game itself. If a "Play" tab in the bottom
nav is actually wanted instead, that's a nav change outside this spec's
scope and should be its own intent note, since it reopens a decision spec
12 already closed once.

### Game view

Tapping the card opens the game as a full-screen overlay (same elevation
pattern as other modals in this app — fixed, `z-index: 60`, dismissible via
a close (×) button top-right, not a back-gesture trap). It is **not** a new
route; it is local component state (`gameOpen: boolean`) so closing it
never loses the child's place in the app.

States, in order:

1. **Intro** — CapyBee avatar + one line (`text.pollenMatchIntro`) + a single
   "Start" button. No settings, no difficulty picker in v1.
2. **Playing** — 3×3 grid of hex tiles (9 tiles = 4 pairs + 1 free "wildcard"
   tile that CapyBee occupies decoratively and is never flipped — keeps the
   grid visually a hex cluster without an awkward odd tile; see §3 for the
   alternative 8-tile layout if the wildcard is cut). Tap a face-down tile
   to flip it face-up; tap a second tile:
   - Match → both tiles lock in "matched" state (small settle animation),
     a soft chime-free visual pulse only (no sound asset exists in this app
     yet — do not add one in v1).
   - No match → both tiles flip back after 700ms.
   - No timer countdown, no move limit, no "wrong answer" color (red is
     never used) — a non-match just flips back neutrally.
3. **Complete** — shown once all pairs are matched. CapyBee reacts
   (`text.pollenMatchDone`), shows the round summary (time taken, flip
   count — framed as information, not a score to beat), and one primary
   button: "Add to my hive" (or "Nice!" if the child has opted out of
   saving — see §4). Closing from this state always counts as complete.
4. **Closed early** — if the child closes mid-round, nothing is saved, no
   guilt copy, no "are you sure" dialog. CapyBee's tone here matches the
   existing mission-skip pattern: no nag on return.

### Where it is NOT

Not a new bottom-nav tab, not a new top-level screen/route, not part of the
Missions catalog (missions are DB-driven prompts; this is a fixed
interaction) — kept as a Home-panel entry to match its "light daily extra"
role rather than a core loop.

## 2. Visual design

Reuses the app's existing token set — no new design system introduced.

### Colors (all already defined in the codebase)

| Purpose | Token / value | Source |
|---|---|---|
| Tile back (face-down) | Tailwind `honey-400` `#f7b927` | `tailwind.config.ts` |
| Tile back border | Tailwind `honey-600` `#b67d14` | `tailwind.config.ts` |
| Tile front (face-up, unmatched) | `#FFF3D6` fill / `var(--accent-strong)` stroke | new, matches `--accent-strong` `#c88610` |
| Tile matched | `#A8D5A2` fill / `#5A9B52` stroke | reused from honeycomb "New World" cell colors (`07-honeycomb-cells-instruction.md`) |
| Overlay background | `var(--panel)` over blurred backdrop, `backdrop-filter: blur(10px)` | matches `.bottom-nav` pattern |
| Primary button | existing `.primary-button` class | `styles.css` |
| Card/panel | existing `.panel` class | `styles.css` |

No red, no black-on-bright warning colors anywhere in the game — consistent
with "no fail state."

### Hex tile geometry

Reuse the exact hex polygon from `07-honeycomb-cells-instruction.md` at 1.3×
scale for tap-friendliness (44×44px minimum touch target rule from
`04-screen-descriptions.md`):

```
Local bounding box: 84×94 (was 64×72 in the honeycomb map)
points="42,3 81,23 81,71 42,91 3,71 3,23"
```

Icons inside tiles are emoji, same technique as `HoneycombMap` (`<text>`
element, `text-anchor="middle"`), font-size 26. Icon set for this game's
pairs (chosen to avoid overlap with existing cell-type icons ⭐🫀😊🫂 so a
completed game's icons never look like they mean something else):
🌼 🍯 🍃 🌻

### Layout (3×3, mobile-first at 360px)

```
   [🌼?] [🍯?] [   ]
   [🍃?] [🌻?] [🍃?]
   [   ] [🌼?] [🍯?]
```
Center tile is the fixed CapyBee decoration (not tappable, not a pair).
Grid uses the same brick-offset stagger described in the honeycomb spec's
`getCellPosition`, scaled to a 3-column, 3-row fixed grid (no dynamic
column count needed since this grid never resizes by data volume).

### Motion

Framer Motion (already a dependency), one clear pattern only:
- Flip: 3D rotateY 0→180 over 220ms, ease `easeInOut`.
- Match settle: scale 1→1.06→1 over 260ms, once.
- No entrance stagger animation on open — the whole grid appears at once to
  keep the "start" moment calm and immediate.

## 3. Grid layout — resolved: 3×3 with a decorative center tile

Pavlo left this choice to whichever fits the app's vibe best. Going with
the 3×3 grid with CapyBee sitting in the fixed center tile (not the plain
2×4 alternative), because:

- It echoes the honeycomb's own hex-cluster shape instead of a flat
  rectangle — visually closer to everything else in the app.
- Having CapyBee physically present inside the game (not just before/after
  it) matches how the mascot already shows up on nearly every other screen
  as a constant, quiet companion.
- A square-ish 3×3 cluster sits better inside the phone-width game overlay
  than a wider 2×4 row, with more comfortable per-tile tap size at 360px.

## 4. Data model (generalized for future mini-games)

**Decision (per Pavlo): generalize now rather than add a column per game.**
Only `duration_ms` is a named column, because every mini-game has a
duration; everything game-specific (flip count, longest sequence, pollen
collected, etc.) goes into one `metrics` JSONB column. Adding a second game
later requires zero schema changes — just a new `game_key` value and a
different shape of JSON from the frontend.

New table, additive migration `V11__game_results.sql`:

```sql
CREATE TABLE game_results (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL REFERENCES users(id),
    game_key VARCHAR(64) NOT NULL,
    duration_ms INTEGER NOT NULL,
    metrics JSONB NOT NULL DEFAULT '{}'::jsonb,
    completed_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_game_results_user_completed
    ON game_results (user_id, completed_at DESC);
```

`game_key` is a free-text discriminator (`'pollen_match'` for now, e.g.
`'hive_builder_tap'` / `'pollen_drift'` later) — confirm the actual `users`
table name against `V1__init.sql` before writing the FK (adjust to match,
e.g. `user_accounts`, if that's the real name — still open, see §9).

Entity — same shape as `CheckInEntry.java`, plus a JSONB-mapped field. This
repo has no existing JSONB column, so this introduces the pattern for the
first time; the minimal approach (no new dependency) uses Hibernate's
built-in JSON mapping (Hibernate 6.2+ / confirm version bundled with Spring
Boot 4.1):

```java
package com.capybee.server.domain.game;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "game_results")
public class GameResult {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount userAccount;

    @Column(name = "game_key", nullable = false, length = 64)
    private String gameKey;

    @Column(name = "duration_ms", nullable = false)
    private Integer durationMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private Map<String, Object> metrics = new HashMap<>();

    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    @PrePersist
    void onCreate() {
        if (id == null) id = UUID.randomUUID();
        if (completedAt == null) completedAt = Instant.now();
    }

    // getters/setters, same style as CheckInEntry
}
```

Pollen Match writes `metrics: { "flips": 8, "pairsTotal": 4 }`. A future
Hive Builder Tap would write `metrics: { "longestSequence": 6 }` into the
same table/column — no migration needed for it.

No `note` field — this entity never carries free text from the child, so
there's nothing to moderate or store beyond gameplay numbers.

## 5. API contract

Added to the existing single `ApiController` (do not create a new
controller class — follow the established pattern):

| Method | Path | Request | Response | Status |
|---|---|---|---|---|
| POST | `/api/games/results` | `CreateGameResultRequest` | `GameResultResponse` | 201 |
| GET | `/api/games/results?gameKey=pollen_match&limit=20` | — | `List<GameResultResponse>` | 200 |

```java
public record CreateGameResultRequest(
        UUID id,
        String gameKey,
        Integer durationMs,
        Map<String, Object> metrics) {
}

public record GameResultResponse(
        UUID id,
        String gameKey,
        Integer durationMs,
        Map<String, Object> metrics,
        Instant completedAt) {
}
```

Frontend types `metrics` as `Record<string, number>`. Each game's own
component is responsible for writing and reading its own known keys —
Pollen Match reads `metrics.flips` / `metrics.pairsTotal`; nothing in the
shared plumbing (queue, sync engine, API) needs to know a game's specific
metric names.

`GameService.createGameResult` follows `CheckInService.createCheckIn`
exactly: client-supplied `id` checked for idempotent retry (offline queue
may resubmit), ownership check on existing id, otherwise insert.

Both endpoints default to authenticated (no `SecurityConfig` allow-list
change needed — this is not `/api/health`).

## 6. Frontend architecture

New files:

- `src/components/PollenMatchGame.tsx` — the game overlay: intro / playing
  / complete states, tile grid, flip logic, calls `onComplete(result)` when
  finished.
- `src/hooks/usePollenMatchGame.ts` — pure game logic (tile shuffle, flip
  state machine, match detection, flip/duration counters) with **no**
  DOM/React-specific side effects beyond `useState`/`useReducer` — this is
  the file that needs Vitest coverage per the testing rule below.

Modified files:

- `AuthenticatedHome.tsx`:
  - `gameOpen` state + `playSectionRef` panel (per §1).
  - `submitGameResult` function, same shape as `submitCheckIn`:
    optimistic local append → `enqueueAction` → `flushQueue()`.
  - Fetch `GET /api/games/results` alongside the other `initialize()` calls
    so game history feeds the honeycomb hook.
- `hooks/useHoneycombCells.ts`:
  - Add `'game'` to `HoneycombCellType`.
  - Add a `gameKey?: string` field to `HoneycombCellData` (only populated
    for `type === 'game'`) so the icon lookup above can pick the right
    per-game emoji.
  - Add a `games: GameResultLike[]` input, mapped to cells with
    `world: null` (games are neither Old World nor New World — like
    check-ins, they're a "today" activity), `gameKey: entry.gameKey`, and
    `title` set to a fixed bilingual label per game (e.g. "Pollen Match" /
    "Zbieranie Pyłku"), not per-game free text.
- `components/HoneycombMap.tsx` / the color-lookup function in
  `07-honeycomb-cells-instruction.md`'s `getCellColors`: add a `game` case.
  Fill `#FBE7B8` / stroke `#E09A1E` (distinct from the existing four so a
  child can tell "I played something" apart from "I finished a mission" at
  a glance) for **every** game cell — the color marks the category
  ("this was a game"). **The icon inside the cell is per-game, not shared**
  (per Pavlo: individual icons per game): Pollen Match uses 🌼, so its
  honeycomb cells render `🌼` inside the shared game-colored hex. A future
  Hive Builder Tap cell would use a different icon (e.g. 🐝) in the same
  fill/stroke. `getCellIcon` needs the `gameKey` (not just the cell `type`)
  to pick the right emoji:

  ```js
  const gameIcons = { pollen_match: '🌼' /* , hive_builder_tap: '🐝', ... */ };
  function getCellIcon(type, gameKey) {
    if (type === 'game') return gameIcons[gameKey] ?? '🧩'; // 🧩 = fallback only
    const icons = { mission: '⭐', memory: '🫀', checkin: '😊', friendship: '🫂' };
    return icons[type] ?? '';
  }
  ```

  `useHoneycombCells.ts`'s `game` cell mapping (below) must carry `gameKey`
  through into `HoneycombCellData` so this lookup works.
- `offline/queueStore.ts`: add `'gameResult'` to `QueueActionType`. No
  change needed in `syncEngine.ts` (generic dispatch already handles any
  path/method/payload combination).

### Optimistic submit (mirrors `submitCheckIn`)

```tsx
const submitGameResult = async (result: PollenMatchResult) => {
  const clientId = crypto.randomUUID();
  const optimistic: GameResultEntry = {
    id: clientId,
    gameKey: 'pollen_match',
    durationMs: result.durationMs,
    flips: result.flips,
    pairsTotal: result.pairsTotal,
    completedAt: new Date().toISOString()
  };

  setGameResults((current) => [...current, optimistic]);
  setGameOpen(false);
  triggerFeedback({
    kind: 'game',
    phrase: pickPhrase('gameComplete'),
    avatar: capyBeeAvatar.default
  });

  await enqueueAction({
    clientId,
    type: 'gameResult',
    path: '/api/games/results',
    method: 'POST',
    payload: {
      gameKey: 'pollen_match',
      durationMs: result.durationMs,
      flips: result.flips,
      pairsTotal: result.pairsTotal
    },
    createdAt: Date.now()
  });
  flushQueue();
};
```

If the child dismisses the "Complete" screen via "Nice!" instead of "Add to
my hive" (i.e. they opted out of saving — see below), skip the
`enqueueAction` call entirely; nothing is sent or stored.

### Opting out of saving

Per the original ask ("it can save results and progress, or not"): saving
is opt-in at the moment of completion, not a settings toggle. The Complete
screen shows two buttons of equal visual weight — `text.addToHive` and
`text.justPlay` — neither framed as the "better" choice. This avoids adding
a new profile-settings field for something this minor.

### What "progress" means here — resolved: honeycomb cell only

**Decision (per Pavlo): a spot on the honeycomb map, nothing more.** This
matches the only progress mechanic that currently exists anywhere in
CapyBee — a check-in, a mission, a memory, a friendship entry each do
exactly one thing: add one filled hex to the honeycomb map. There is no
points/currency/unlock system built anywhere in the codebase today (the
"stars" and "unlocks" mentioned in the original concept doc were never
implemented — confirmed by searching the repo). A stars/unlocks system was
considered and explicitly not chosen — it would have meant new
infrastructure (a currency field, worth-rules, unlockables) with no
existing foundation, and is out of scope here and for any future
mini-game unless a separate spec introduces it later.

### Daily hive-cell cap — resolved: yes, one per day

**Decision (per Pavlo): needed, so games don't overfill the hive.**

Enforced entirely client-side, reusing the exact pattern already in the
codebase for `hasCheckInToday` (`AuthenticatedHome.tsx` ~L963), which uses
the existing `sameCalendarDay(a, b)` helper from `capybee.tsx` (~L58) — no
new date-comparison logic is introduced.

```tsx
const hasGameHexToday = useMemo(
  () => gameResults.some((entry) => sameCalendarDay(new Date(entry.completedAt), new Date())),
  [gameResults]
);
```

Behavior on the Complete screen:

- If `hasGameHexToday` is `false` when the round finishes: show the normal
  two-button choice (`text.addToHive` / `text.justPlay`) described above.
  Choosing "Add to my hive" saves the result as usual (optimistic append +
  `enqueueAction`).
- If `hasGameHexToday` is already `true` (child already earned a hive cell
  from a game today, in this or an earlier game session): show a single
  button (`text.niceGame` — "Nice!" / "Fajnie!") and **do not** call
  `enqueueAction` at all. Nothing is sent to the server. The round isn't
  penalized or blocked — the child can keep playing as many times as they
  want, it just doesn't add a second hex the same day. No explanation
  copy is needed beyond the warm one-line reaction; this is never framed
  as a limit reached, just as "already played today."

This means the cap lives entirely in the UI decision of whether to offer
the save button, not in a backend rule — consistent with how every other
"today" check in this app already works (`hasCheckInToday`). No new
backend validation, no new column. If a second device or the offline queue
ever produces two saved rows for the same day (edge case, not expected in
normal single-device use), that's a pre-existing class of tolerance in
this app (see idempotent-retry-by-id pattern) rather than a new problem to
solve here.

## 7. Copy (add to both `en` and `pl` blocks in `AuthenticatedHome.tsx`'s
`copy` object, same location/pattern as existing keys)

| Key | EN | PL |
|---|---|---|
| `playTitle` | Quick play | Szybka zabawa |
| `playHubPrompt` | Play a quick game | Zagraj w krótką grę |
| `playHubTitle` | Choose a game | Wybierz grę |
| `pollenMatchTitle` | Pollen Match | Zbieranie Pyłku |
| `pollenMatchBlurb` | Find the matching pairs | Znajdź pasujące pary |
| `pollenMatchIntro` | Flip two tiles at a time. No rush, no wrong answers. | Odkrywaj po dwa kafelki. Bez pośpiechu, bez złych odpowiedzi. |
| `pollenMatchDone` | You found them all! | Znalazłeś/aś wszystkie! |
| `addToHive` | Add to my hive | Dodaj do mojego ula |
| `justPlay` | Just play, don't save | Po prostu graj, nie zapisuj |
| `gameResultSummary` | {flips} flips · {seconds}s | {flips} odkryć · {seconds}s |
| `niceGame` | Nice! | Fajnie! |

Exact PL grammar (verb endings for a nickname of unknown gender, etc.)
should be reviewed against the existing `capybee-phrases-instruction.md`
tone rules before merging — flag this row for a native-speaker pass rather
than guessing.

## 8. Testing (mandatory per `copilot-instructions.md`)

- `usePollenMatchGame.test.ts` (Vitest, happy-dom): shuffle produces exactly
  4 pairs + 1 fixed center; flipping two matching tiles transitions both to
  `matched`; flipping two non-matching tiles reverts both after the delay;
  completion fires exactly once when all 4 pairs are matched; flip counter
  increments once per tile flip, not per attempt-pair.
- `GameServiceTest` (JUnit 5 + Mockito, mirrors `CheckInServiceTest`):
  create-success path; idempotent retry with same client `id` returns the
  existing row rather than duplicating; ownership check rejects an `id`
  that belongs to another account; validation rejects a negative
  `durationMs`/`flips`/`pairsTotal`.
- No new JaCoCo exemptions — `GameService` is covered by the existing 70%
  gate on `com.capybee.server.service.*`.

## 9. Open questions for Pavlo

Resolved: generic `metrics` JSONB (not per-game columns); single "Play"
hub entry point (not one card per game, not a 5th nav tab); individual
icon per game, shared color for the `game` cell type; daily cap of one
game-earned hive cell per calendar day, enforced client-side via the
existing `sameCalendarDay` pattern; progress = honeycomb cell only, no
stars/unlocks system; 3×3 grid with a decorative center CapyBee tile.

Still open — one fact to check, one thing to watch for later:

1. Confirm actual FK target/name for `users` vs `user_accounts` in
   `V1__init.sql` before finalizing `V11`'s migration SQL above. This is a
   lookup, not a decision.
2. Not blocking for Pollen Match, just a note for later: when Hive Builder
   Tap / Pollen Drift / Balloon Breath get speced, their icons need to
   stay visually distinct from Pollen Match's 🌼🍯🍃🌻 (and from each
   other) in the Play hub list and the honeycomb.
