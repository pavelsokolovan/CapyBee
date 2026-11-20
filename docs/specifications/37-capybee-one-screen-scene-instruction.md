# Spec 37 — CapyBee: One-screen scene

**Depends on:** 30.5, 32, 33 and 36

**Baseline:** `4c4f71b` plus spec 36

**Prototype:** https://claude.ai/artifact/VAv1pYPpzPJPUvM6gi4fAQ (simplified
placeholder art; it only shows layout, spacing and the fit badge)

Make each story beat of a scene-first run (episode excluded) read like a game
screen: art on top, short text in a bubble, and the choices pinned where the
thumb is. The child should see what happened and what they can do without
scrolling past a picture to find the buttons.

This spec changes layout only. Text length (spec 40/41), the map sheet and
menu (spec 38) and scroll/focus behavior (spec 39) are separate.

## Decisions taken from the prototype

Both are the recommended option; change them here before implementation if you
prefer the alternative.

- **Actions:** pinned footer (not inline under the text, as today).
- **Text:** speech bubble with a small tail toward the stage (not a plain card).

## Baseline problems

- Choices sit at the end of the text card, so on a 360 × 640 phone they are
  often below the fold, as in the playtest screenshots.
- The location is a separate `h4` above the art and takes a full row.
- The overlay (`.adventure-overlay`) is the only scroller, so nothing can stay
  on screen while the story text changes length.

## Behavior

Applies when `sceneFirstKind(run) === 'expedition'` (spec 36).

1. **App-shell layout.** Inside the adventure overlay the scene is a column of
   three regions, so only the middle region scrolls:
   - *Top (fixed):* the existing overlay header, then the pending/recovery
     status line when it is visible.
   - *Body (scrolls):* tools row (Map, Text mode), art, bubble, then the
     secondary content that exists today (current characters disclosure, party,
     objectives, status, abandon button and dialog, ending/result blocks).
   - *Footer (fixed):* the story actions (see 3).
   For these runs the overlay does not scroll itself
   (`.adventure-overlay:has(.adventure-scene-panel)` gets `overflow: hidden`);
   `.adventure-scene-panel` becomes a flex column filling it, `.game-run` is
   `flex: 1; min-height: 0`, and the body is `overflow-y: auto`. Use `dvh`
   units, never `vh`. Overlays without a scene panel are unchanged.
2. **Art and location.** The backdrop keeps its catalog art and the stage
   characters from `gameCompanions(view).current`. Its height is
   `clamp(6rem, 20dvh, 10rem)` at the aspect ratio of today's art (replace the
   fixed `aspect-ratio: 2.4` and `max-height: 10rem`). The current location name
   becomes a caption chip at the bottom-left of the art, on an opaque cream
   surface. Pass it to `GameEncounter` as a new optional `caption` prop. The
   caption is the existing location `h4` (same `tabIndex={-1}`, `lang`, ref and
   focus behavior), so heading semantics do not change. When the backdrop is
   hidden (Text mode, forced colors) the same `h4` renders as an ordinary
   heading above the text, so it is never hidden with the art.
3. **Pinned actions.** In `GameRun`, split the actions by count:
   - If the rendered actions (enabled and disabled options plus the Continue
     button) number **1–3** and the view has no text input, render
     `SceneActions` in the footer. Footer buttons are full width, at least
     48 px tall, and padded by `env(safe-area-inset-bottom)`.
   - Otherwise (more than 3, or a text input or textarea) render it in the body
     directly under the bubble, as today. This covers the six-choice hub scene
     and keeps the on-screen keyboard from fighting a pinned bar.
   - The footer has `max-height: 45dvh` with its own `overflow-y: auto`, so
     large text or 200% zoom can never push the story off screen.
   The `fieldset`, `legend` (visually hidden in scene-first), disabled/locked
   behavior, hints and `key={state.run.revision}` stay exactly as in
   `SceneActions`. The recovery buttons (retry, remove, check) render in the
   footer whenever they are present, so they never need a scroll to reach.
4. **Bubble.** `SceneBlocks` render inside one opaque rounded surface with the
   existing ink/paper colors (`#2e2413` on `#fffdf7`, contrast unchanged). A small
   tail points up toward the stage when the backdrop is visible and the stage has
   at least one character. There is no tail in Text mode, and no tail without
   stage characters. SPEECH blocks keep their speaker name line. Preserve
   `white-space: pre-wrap`, escaping and ordering. In forced colors the bubble
   uses `Canvas`/`CanvasText` and a visible border.
5. **Tools row.** The Map control and Text mode button stay in one compact row
   above the art, at least 44 × 44 px, unchanged in behavior. Spec 38 replaces
   this row with a menu.
6. **Reduced chrome.** In scene-first runs remove the dashed border and the
   bottom radius split between art and text (the art card and the bubble are two
   separate surfaces). Keep the existing `game-art` tokens; add no new colors.
7. **Unchanged:** episode and legacy layouts, the engine, packs, commands, the
   queue, save format and rewards. No visible copy changes and no new
   localized strings.

## Fit targets

Measured on a 360 × 640 viewport at default text size with Polish story text.
These are expected values; the browser run decides, and spacing may be tuned
within the rules above to meet them.

- Opening (`arrival`, 1 action) shows art, full text and the action without
  body scrolling.
- Scenes with 3 actions and about 30 words (for example `lantern`) are expected
  to be within a few pixels of the limit. If they scroll, the footer must still
  be visible and the art must stay at least `6rem` tall. Spec 40 shortens the
  text to close the gap.
- The six-choice hub keeps its choices in the body and may scroll. This is
  accepted here; it is revisited with spec 38 (map moves).
- Ending scenes (no actions) show text and result blocks in the body with no
  footer.

## Verification

1. **Component tests** (`GameRun.test.tsx`, `GameEncounter` tests): with a
   scene-first fixture run, assert that a 3-action view renders actions inside
   the footer region and a 6-action view renders them inside the body; a view
   with a text input renders in the body; the caption `h4` is rendered inside the
   backdrop wrapper normally and outside it in Text mode; the tail is absent in
   Text mode and with an empty stage; recovery buttons render in the footer.
2. **Behavior preserved:** existing assertions for locked fieldset, hints,
   Continue, text input bounds, focus on the location heading after a move, and
   Text mode parity (same text, same choices, no commands sent) keep passing.
3. **Browser suite** (`app/ui/scripts/game-expeditions.mjs`,
   `game-honeycomb-board.mjs`): at 360 × 640 assert that the footer is within the
   viewport and its buttons are at least 44 × 44; the backdrop is at least `6rem`
   tall; the body, not the overlay, is the scroller; at 360 × 1500 nothing is
   clipped (existing stage-clipping check); at 200% zoom there is no horizontal
   overflow and the footer stays within 45% of the viewport height; Text mode and
   forced colors still show the text, the heading and the choices. Replace the
   `scrollIntoView({block:'center'})` screenshots with viewport screenshots saved
   to `docs/evidence/37/` (`opening-mobile.png`, `hub-mobile.png`,
   `lantern-mobile.png`, `text-mode-mobile.png`, `forced-colors-mobile.png`).
4. **Gates** (`.github/instructions/build-workflow.instructions.md`): `npm test`
   in `app/ui`, `./scripts/dev-workflow.ps1 -Action sync`, then the Spring-served
   browser suite and a manual pass on a real phone in Polish and English
   controls, with the browser address bar visible and hidden.
5. Add `docs/game-one-screen-scene-acceptance.md` in the style of the other
   acceptance documents, linking the evidence screenshots.
