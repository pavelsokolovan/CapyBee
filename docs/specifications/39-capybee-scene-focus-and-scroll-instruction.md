# Spec 39 — CapyBee: Scene focus and scroll

**Depends on:** 30.5, 32, 33, 36, 37 and 38

**Baseline:** `4c4f71b` plus specs 36–38

After every accepted action the new beat must start at its top, with keyboard
and screen-reader focus on the right heading and nothing scrolled out of
place. This is logic and CSS only; there is no new visual design, so there is
no prototype.

## Baseline problems

- `GameRun.tsx` moves focus with plain `element.focus()` in one
  `useLayoutEffect`. Browsers scroll just enough to reveal the heading, so when
  the scene was scrolled the new beat opens mid-page. This is the half-scrolled
  state in the playtest screenshots.
- Scrolling lives in the `.adventure-overlay`, which is shared by the catalog,
  setup, review and play phases. A scroll offset left by an earlier phase (for
  example a long review screen) is still there when the game mounts. With spec
  37 the overlay becomes `overflow: hidden` for scene-first runs, so a stale
  offset would hide the header with no way to scroll back. This spec closes that
  gap.
- `.game-expedition-location` carries `scroll-margin-block-start: 4.5rem` for a
  sticky status line that spec 37 moves into the fixed top region, so it no
  longer does anything useful.
- Dragging on the dimmed backdrop of the map sheet (spec 38) can scroll the
  story behind it.

## Behavior

Applies to every run `GameRun` renders; the scroller differs by layout (see 1).

1. **One helper.** Add `app/ui/src/game/sceneScroll.ts` exporting
   `resetSceneScroll(scroller: HTMLElement | null)` (sets `scrollTop = 0` and
   `scrollLeft = 0`, instantly, never smooth) and
   `focusScene(target: HTMLElement | null, scroller: HTMLElement | null)` (calls
   `resetSceneScroll`, then `target.focus({ preventScroll: true })`). The
   scroller is:
   - scene-first runs (spec 36 `'expedition'`): the `.game-scene-body` from spec
     37;
   - every other run, including the Anonymous Note episode: the closest
     `.adventure-overlay` of the run's `section`, via a new section ref.
2. **Accepted actions.** In the existing layout effect, use `focusScene` instead
   of `heading.current?.focus()` for the branches that focus a heading
   (`episode`, `expedition`, non-`MOVE` commands, `ENTER_SCENE` moves, ended
   runs, and the episode ending heading). The conditions that decide *whether*
   and *where* to focus do not change. The non-entry `MOVE` branch that focuses
   the current map tile keeps ordinary `focus()` because the tile lives in the
   sheet's own scroll area.
3. **Only for actions made here.** Reset and refocus only when `intention.current`
   matches an accepted, saved revision, as today. A revision that arrives
   without a local action (`controller.check()`, recovery, another tab) neither
   scrolls nor moves focus, so a child who is reading is never yanked. A
   rejected action changes neither scroll nor focus.
4. **First mount.** When a different run id mounts (`focusBaseline` branch), call
   `resetSceneScroll` on the scroller and also on the closest `.adventure-overlay`
   (to clear offsets left by earlier overlay phases), then focus the run heading
   with `preventScroll`.
5. **Scroll containment.** Add `overscroll-behavior: contain` to
   `.game-scene-body`, the map sheet panel and its scrolling area, and the
   footer's scroll area, so touch scrolling at the end of one region does not
   move another.
6. **CSS cleanup.** Remove `scroll-margin-block-start` from
   `.game-expedition-location`. Keep `.game-episode-ending-title`'s margin.
   Give the run `section` `scroll-margin-block-start: 4rem`, so
   `scrollIntoView` and focus on the legacy overlay clear its sticky header.
7. **Visible focus.** Make sure the focus outline on the location caption chip
   (spec 37) is not clipped by the art's `overflow: hidden` (the chip sits 8 px
   from the edge; keep outline width plus offset within that). Do not remove
   focus styles from headings.
8. **Reduced motion and zoom.** No smooth scrolling anywhere in these paths.
   At 200% zoom, after an accepted action the heading is inside the visible
   scroller area, not under the header or footer.
9. **Unchanged:** which element receives focus, announcements and live regions,
   the Text mode and sheet focus rules from specs 32, 33 and 38, commands,
   persistence and all copy.

## Out of scope

- Moving focus to the story text instead of the location heading when the
  location does not change. Revisit it with the screen-reader review in the
  release acceptance.
- Animated transitions between beats (spec 43).

## Verification

1. **Helper tests** (`sceneScroll.test.ts`): `resetSceneScroll` zeros both
   offsets and tolerates `null`; `focusScene` zeros the offsets, then calls
   `focus` once with `{ preventScroll: true }`, and tolerates a missing scroller
   or target.
2. **Component tests** (`GameRun.test.tsx`), scene-first fixture:
   - Set `.game-scene-body` `scrollTop` to a nonzero value, submit a choice, accept
     the revision: `scrollTop` is `0`, the location heading has focus, and `focus`
     was called with `{ preventScroll: true }`.
   - A revision from `controller.check()` with no local action leaves `scrollTop`
     and focus unchanged.
   - A rejected action leaves `scrollTop` and focus unchanged.
   - Mounting with a stale `.adventure-overlay` `scrollTop` resets it to `0`.
   Repeat the first and last cases for a legacy (no `presentation`) fixture, where
   the overlay is the scroller, and for the episode layout.
3. **Browser suite** (`game-expeditions.mjs`, `game-honeycomb-board.mjs`): make a
   scene scroll (360 × 480 viewport or 200% zoom), choose or advance, then assert
   `.game-scene-body.scrollTop === 0`, `document.activeElement` is the location
   heading, and the heading's bounding box is inside the body's. Assert that the
   overlay has `scrollTop === 0` after starting a game from a scrolled review
   screen. Assert that `overscroll-behavior` is `contain` on the body, sheet and
   footer. Re-run the existing reduced-motion, forced-colors, Text mode and
   keyboard checks. Save viewport screenshots to `docs/evidence/39/`
   (`after-advance-small-viewport.png`, `after-advance-200-zoom.png`).
4. **Gates:** `npm test` in `app/ui`, `./scripts/dev-workflow.ps1 -Action sync`,
   the Spring-served browser suite, and a manual pass on a real phone, scrolling a
   long scene and then choosing, in Polish and English controls.
5. Add `docs/game-scene-focus-scroll-acceptance.md` in the style of the other
   acceptance documents, linking the evidence.
