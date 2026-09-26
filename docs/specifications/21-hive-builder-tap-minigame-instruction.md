# 21 — Hive Builder Tap Mini-Game

## Purpose

Add the second mini-game to CapyBee: **Hive Builder Tap**, a Simon-style
sequence-memory game. A round grows a honeycomb pattern as the child
successfully recalls longer and longer tile sequences — the game visually
becomes a hex cluster as play continues. Like Pollen Match, rounds take
20–60 seconds, have no fail state, and optionally award one hive cell per
day (capped at 1 game-cell/day across all games, same logic as Pollen
Match).

Traces back to the original mini-game concepts (four options presented:
Pollen Match ✅ built, Hive Builder Tap, Pollen Drift, Balloon Breath
Round).

## Ground truth reviewed

- `docs/specifications/20-pollen-match-minigame-instruction.md` — Pollen
  Match's full spec and implementation patterns (see also commit
  `5b5aa74` in repo).
- `app/ui/src/AuthenticatedHome.tsx` — how Pollen Match is wired: state
  (`selectedGameKey`, `gameOpen`), opening function (`openPollenMatch`),
  closing function (`closePollenMatch`), conditional render block around
  line 2574, and the Play tab game list around line 2232.
- `app/server/src/main/java/com/capybee/server/domain/game/GameResult.java`
  — JSONB `metrics` field is flexible and game-agnostic; each game writes
  its own metric shape (Pollen Match uses `{ "flips": N, "pairsTotal": 4
  }`).
- `app/server/src/main/java/com/capybee/server/service/GameService.java`
  — backend already accepts any `gameKey` and queries by it; no changes
  needed for a new game key.
- `app/ui/src/hooks/usePollenMatchGame.ts` — pure game logic hook pattern
  (no DOM effects, fully testable).
- `app/ui/src/AuthenticatedHome.tsx` ~line 2244 — Play tab game list
  shows Hive Builder as a disabled row waiting to be built; removal of
  `disabled` attribute signals completion.

## Constraints (unchanged from Pollen Match)

- No PII, no real names.
- No social/competitive features — no cross-child comparison, ever.
- Bilingual EN/PL required for every string.
- No padlocks, hearts, or human figures — hive/honeycomb visual language
  only.
- A round must be skippable at any time with no penalty or nag.
- One hive cell per day across all games (inherited from Pollen Match
  daily-cap wiring; no new backend logic needed).
- Game icon must be visually distinct from Pollen Match's 🌼🍯🍃🌻 —
  suggested: **🐝** (the bee, part of CapyBee itself).

## 1. UX flow

### Entry point (no change)

Same as Pollen Match: a row in the Play tab game list. Remove `disabled`
from the button, set it to `onClick={openHiveBuilderTap}`.

### Game view

Three sequential states (same pattern as Pollen Match):

1. **Intro** — CapyBeeAvatar, one-line intro, "Start" button.
2. **Playing** — the growing hex sequence pattern, visual feedback on each
   tap, optional fail/reset (see §3 for design choice).
3. **Complete** — CapyBee reaction, round summary (longest sequence
   reached), two buttons: "Add to my hive" / "Just play" (or one "Nice!"
   if daily cap already hit).

Key difference from Pollen Match: this game has **optionally no fail
state**. See §3 for the open design question: allow the child to tap
whenever (endless play mode) or reset on a wrong tap (Simon-style)?

### Daily cap (inherited)

If `hasGameHexToday` (using `sameCalendarDay` from existing Pollen Match
wiring) is already true, show only "Nice!" button and save nothing. No
new backend logic.

## 2. Visual design

Reuses app's existing token set — no new colors introduced.

### Colors (same as Pollen Match's game cells)

| Purpose | Token / value |
|---|---|
| Hex fill | `#FBE7B8` |
| Hex stroke | `#E09A1E` |
| Hex matched/lit | add glow effect (inset box-shadow + scale) |
| Icon (in hive cell) | **🐝** (per §1 constraint) |

### Hex tile geometry

Reuse the exact same SVG hex polygon as Pollen Match (`points="42,3 81,23
81,71 42,91 3,71 3,23"` at 84×94px), non-interactive decoration — the
tiles never get clicked. The sequence plays automatically after each
successful tap, so the child is watching, not tapping the hive itself.

### Layout (growing hexagon cluster)

Start with 1 center hex. On each successful tap/recall, add the next hex
to the cluster around the existing tiles, following a brick-offset
pattern (exact same stagger used by the honeycomb) to grow outward as a
proper hex ring. By sequence length 6–7, the cluster fills a typical
phone-width play area.

Example positions for a 7-tile growing sequence:
```
      Round 1:     Round 2:      Round 3:      ...
        [0]         [0][1]        [2]
                                 [0][1][3]
                                   [4][5]
```

(coordinates vary; the spec defers exact brick-offset math to the
implementation — the hook should handle it, same way Pollen Match's
`createPollenMatchBoard` handles the 3×3 grid).

### Motion

Framer Motion (already a dependency in Pollen Match code):

- **Sequence play:** each hex in the sequence lights up (fill color
  slightly brighter, scale 1→1.08→1) for ~500ms, one after another, no
  overlap.
- **Tap feedback:** the hex the child just tapped pulses (scale 1→0.94→1)
  to confirm the input.
- **Match settled:** both the newly-lit hex and the growing cluster
  subtly settle (scale 1→1.02→1 over 220ms) to mark "round progressed."
- **Entrance:** no stagger on the cluster intro — all tiles appear at once
  for calm, immediate clarity.

## 3. Game logic (open design choice)

**Question for Pavlo: fail state or endless?**

### Option A — Simon-style (fail on wrong tap, reset)

Child taps the tiles in order as the sequence plays. Wrong tap:
- All tiles flash red briefly (or just the wrong tile dims/shakes).
- Sequence resets to 1 tile.
- The round ends when the child stops tapping correctly OR explicitly
  closes.
- Metric: `longestSequence` (e.g. 7 means child recalled a 7-tile
  sequence before missing).
- Feels: skill-based, encouraging replays to "beat your high score"
  (though the app never shows comparisons — just personal progress).

### Option B — Endless replay (no fail)

Child taps the tiles in order. Wrong tap:
- Sequence quietly resets to 1 tile.
- No visual punishment, just a restart.
- The game continues indefinitely; child closes when they're done.
- Metric: `maxSequenceLength` (same as Option A) + `totalTapsAttempted`
  (to show effort/engagement without judgment).
- Feels: more forgiving, focuses on "how far did you get" rather than
  "what was your mistake."

**This spec assumes Option A** (Simon-style with reset) since it's the
more recognizable pattern and the concept brief mentioned it as "watch,
then tap it back" — implying a clear success/reset loop. If Pavlo
prefers Option B (endless), the logic and metrics change slightly but
the architecture stays identical.

Note: the absence of a numeric "score" or "level" is intentional (matches
Pollen Match's philosophy — no competitive framing).

## 4. Data model (reuses existing infrastructure)

No new table or columns. The `game_results.metrics` JSONB column
(already generic) receives:

```json
{
  "longestSequence": 6,
  "totalTapsAttempted": 23
}
```

Backend: no changes needed. `GameService` already handles any `gameKey`
and any `metrics` shape.

## 5. API contract (no changes)

Reuses `POST /api/games/results` from Pollen Match. Same DTO:

```java
public record CreateGameResultRequest(
    UUID id,
    String gameKey,
    Integer durationMs,
    Map<String, Object> metrics) {}
```

Frontend fires:

```tsx
await enqueueAction({
  clientId: resultId,
  type: 'gameResult',
  path: '/api/games/results',
  method: 'POST',
  createdAt: Date.now(),
  payload: {
    gameKey: 'hive_builder_tap',
    durationMs: Date.now() - startedAt,
    metrics: {
      longestSequence: 6,
      totalTapsAttempted: 23
    }
  }
});
```

## 6. Frontend architecture

New files:

- `src/components/HiveBuilderTapGame.tsx` — game overlay: intro / playing
  / complete states, hex cluster rendering, sequence logic, calls
  `onComplete(result)` when finished.
- `src/hooks/useHiveBuilderTapGame.ts` — pure game logic (sequence
  generation, tap validation, cluster growth, animations). **No DOM side
  effects** — fully testable with Vitest, same pattern as
  `usePollenMatchGame.ts`.

Modified files:

- `AuthenticatedHome.tsx`:
  - Add `selectedGameKey` type union: change `'pollen_match' | null` to
    `'pollen_match' | 'hive_builder_tap' | null`.
  - Add state for game-specific vars (same pattern as Pollen Match):
    `hiveBuilderTapStage`, `hiveBuilderTapSequence`, `hiveBuilderTapPosition`,
    `hiveBuilderTapStartedAt`, `hiveBuilderTapLocked`, `hiveBuilderTapSaved`.
  - Add `openHiveBuilderTap()` function (mirrors `openPollenMatch`).
  - Add `closeHiveBuilderTap()` function (mirrors `closePollenMatch`).
  - Add `saveHiveBuilderTapResult()` function (mirrors
    `savePollenMatchResult`), calling `enqueueAction` with `gameKey:
    'hive_builder_tap'` and the metrics above.
  - Modify the Play tab game list around line 2244: replace the disabled
    `🐝 Budowniczy Ul / Hive Builder` row with an enabled one, `onClick={openHiveBuilderTap}`.
  - Add conditional render block for `gameOpen && selectedGameKey ===
    'hive_builder_tap'`, rendering `<HiveBuilderTapGame />` (mirrors the
    Pollen Match block at line 2574).
  - Fetch/append game results alongside Pollen Match (no new fetch
    needed — both games use the same `GET /api/games/results` by `gameKey`
    or combined).

### Optimistic submit (copy Pollen Match pattern exactly)

```tsx
const saveHiveBuilderTapResult = async () => {
  if (!hiveBuilderTapStartedAt || hiveBuilderTapSaved) return;

  const resultId = crypto.randomUUID?.() ?? `hive-builder-${Date.now()}`;
  const durationMs = Date.now() - hiveBuilderTapStartedAt;

  setHiveBuilderTapSaved(true);
  closeHiveBuilderTap();

  await enqueueAction({
    clientId: resultId,
    type: 'gameResult',
    path: '/api/games/results',
    method: 'POST',
    createdAt: Date.now(),
    payload: {
      gameKey: 'hive_builder_tap',
      durationMs,
      metrics: {
        longestSequence: hiveBuilderTapSequence.length,
        totalTapsAttempted: hiveBuilderTapPosition // or however you count attempts
      }
    }
  });

  flushQueue();
};
```

No changes to offline queue (`queueStore.ts` already has `'gameResult'`
type).

## 7. Copy

Add to both `en` and `pl` blocks in `AuthenticatedHome.tsx`'s `copy`
object:

| Key | EN | PL |
|---|---|---|
| `hiveBuilderTapTitle` | Hive Builder | Budowniczy Ul |
| `hiveBuilderTapBlurb` | Remember the sequence | Zapamiętaj sekwencję |
| `hiveBuilderTapIntro` | Watch the hive light up, then tap each tile in the same order. No rush — you control the pace. | Obserwuj, jak ul się świeci. Potem stuknij każdy kafelek w tej samej kolejności. Bez pośpiechu — ty kontrolujesz tempo. |
| `hiveBuilderTapDone` | You got to {longestSequence}! | Dotarłeś/aś do {longestSequence}! |
| `hiveBuilderTapRoundSummary` | Longest sequence | Najdłuższa sekwencja |
| `hiveBuilderTapStart` | Start | Start |

## 8. Testing (mandatory per `copilot-instructions.md`)

- `useHiveBuilderTapGame.test.ts` (Vitest): sequence generation
  produces correct length; tap validation accepts correct order and
  rejects wrong; sequence grows by 1 on each successful round (or
  resets to 1 on error, per §3 choice); game completion fires exactly
  once when... (TBD based on win condition in §3).
- `HiveBuilderTapGameTest` or extend `GameServiceTest` (JUnit 5): create
  game result with `gameKey: 'hive_builder_tap'` succeeds; metrics are
  saved to JSONB correctly; retrieval by `gameKey` returns only this
  game's results.

No new JaCoCo exemptions.

## 9. Open questions for Pavlo

1. **Fail state** (§3) — Option A (Simon-style reset on error) or Option
   B (endless replay with no fail)? This changes the game logic but not
   the overall architecture.
2. **Hex cluster growth pattern** — exact positions for tiles 1–7 in the
   brick-offset layout. Deferred to the implementation hook, but can be
   sketched before coding if desired.
3. **Tap input method** — the Play tab lists games as rows (like Pollen
   Match), so "enter" is a game selection. Inside the game: should the
   child tap the growing hex cluster itself (like Pollen Match's tiles),
   or is tapping anywhere on the game area enough (simpler, less error-prone)? Pollen Match uses tile-tapping; this spec assumes the same for consistency, but it's a choice.
4. **Icon in the honeycomb cell** — confirm 🐝 is the right choice; if
   there's a collision with other planned games (Pollen Drift, Balloon
   Breath), pick a different one now.

## 10. Rollout checklist

- [ ] Decide on §3 fail-state design (Option A or B).
- [ ] Implement `useHiveBuilderTapGame.ts` hook with chosen logic.
- [ ] Implement `HiveBuilderTapGame.tsx` component (overlay, states,
  rendering).
- [ ] Add state variables and handlers to `AuthenticatedHome.tsx` (mirrors
  Pollen Match).
- [ ] Update Play tab game list in `AuthenticatedHome.tsx` (enable the
  🐝 row, set `onClick={openHiveBuilderTap}`).
- [ ] Add conditional render block for `selectedGameKey ===
  'hive_builder_tap'` in `AuthenticatedHome.tsx`.
- [ ] Add copy keys to both `en` and `pl` blocks.
- [ ] Add tests (hook + optional service level).
- [ ] Verify 🐝 icon doesn't conflict with future games' icons.

## Notes

This spec is intentionally lean because Pollen Match already established
the game infrastructure (state management pattern, JSONB metrics,
offline-queue wiring, daily-cap logic). Hive Builder Tap plugs into that
pattern cleanly — just a new game logic hook and UI component, plus
a few new state variables in `AuthenticatedHome.tsx`.

The open design choice in §3 is the only substantial divergence from
"clone and recolor": fail-state behavior shapes the game feel. Recommend
resolving that before starting implementation.
