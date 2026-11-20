# Spec 38 — CapyBee: Map sheet and scene menu

**Depends on:** 30.3, 32, 33, 36 and 37

**Baseline:** `4c4f71b` plus specs 36 and 37

**Prototype:** https://claude.ai/artifact/5vpHhcuRmuwK5HpvKiUaiV (simplified
placeholder art; the crossing is unlocked by visiting the workshop, whereas the
real pack uses the `rail-ready` flag)

Turn the optional map into a bottom sheet that opens on top of the scene, make
each hex a single tap target, and move the rarely used controls (Text mode,
End adventure) into one small menu. The scene screen then has two quiet
controls, **Mapa** and **Menu**, instead of a row of buttons and a trailing
"End adventure" button.

This amends spec 33 (travel control placement) and spec 37 (tools row). It does
not change movement rules, commands or story text.

## Decisions taken from the prototype

- **Travel:** tap a hex to select it, read its card, then tap **Idź** (not a
  one-tap move). A move opens an encounter, so it is a commitment; the card is
  also where the description and any prerequisite appear. The prototype has a
  one-tap alternative for comparison; the spec does not use it.
- **Menu:** a small disclosure with two items, not a full ARIA menu.

## Baseline problems

- In compact mode each hex carries its own label, state text and travel button,
  or the "no route" text, so a seven-place map is mostly text and buttons.
- The map is an inline `details` (`.game-scene-map`) that pushes the story down
  when opened.
- Descriptions live in a separate "About these places" disclosure, away from the
  place they describe.
- Text mode and End adventure are separate controls in the body, taking space
  on every beat.

## Behavior

Applies to runs where `sceneFirstKind(run) === 'expedition'`. The legacy board
(non-compact `GamePlaces`) and the Anonymous Note `EpisodeMap` are unchanged.

### Tools row

1. The tools row moves out of the scrolling body into the fixed top region of
   the spec 37 layout, directly under the overlay header and above the status
   line. It holds two buttons, each at least 44 px tall: **Mapa**
   (`boardCopy.map`, with a small decorative map icon) and **Menu**
   (`boardCopy.menu`, with a decorative ⋯ glyph). Neither is icon-only.
2. The standalone Text mode button and the body's End adventure button are
   removed from scene-first runs. They move into the menu.

### Menu

3. **Menu** is a `details.game-scene-menu` disclosure whose `summary` is the
   button. Its panel lists plain buttons, each at least 44 px tall:
   - **Text mode** (`encounterCopy[locale].text`), the existing toggle with
     `aria-pressed`, same behavior, same text-preference state, no commands, no
     storage. Shown only when `presentation` exists, as today.
   - **End adventure** (`runCopy[...].abandon`), shown only while the run is
     `ACTIVE`, disabled while `locked`. It opens the existing abandon dialog
     unchanged. After Cancel, focus returns to the Menu summary (replace the
     `abandon` ref target).
   Choosing an item closes the menu and returns focus to the summary. Escape
   closes the menu and returns focus to the summary, and does not close the
   adventure overlay (stop propagation as the abandon dialog does). If neither
   item applies, do not render Menu.

### Map sheet

4. **Mapa** opens a native `<dialog>` via `showModal()`, anchored to the bottom
   of the overlay, with a dimmed backdrop, rounded top corners, a title
   (`boardCopy.title`) and a **Close** button (`boardCopy.close`). Maximum
   height `88dvh`; the place grid scrolls inside it and the detail card (below)
   stays pinned at the bottom of the sheet. Close by the button, Escape, or a tap
   on the backdrop. Set `onKeyDown={e => e.stopPropagation()}` and handle
   `onCancel` like the abandon dialog, so Escape and Tab never reach the
   adventure overlay's handlers.
5. Focus: on open, focus the sheet title. On close by the user, return focus to
   **Mapa**. On close after an accepted move (see 9), focus follows spec 32: the
   new location heading.
6. Contents, top to bottom: the scene-wide `places.movementHint` once, if present;
   the board; the detail card. Remove the "About these places" disclosure and
   its copy. The existing 7-tile hex grid keeps `hexLayout()`; the graphical
   layout still requires a tile width of 92 px, so keep the sheet's horizontal
   padding to 12 px or less (a 360 px phone gives about 95 px tiles for a
   seven-column layout; anything narrower falls back to the list). Text mode,
   narrow widths and maps without usable art or coordinates use the same
   selectable list.

### Tiles and list rows

7. Each place is **one button** (≥ 44 × 44 px) containing the artwork
   (`aria-hidden`), the place label and a state text, and **no** travel button.
   - Current: player marker, double outline, visible text `boardCopy.youAreHere`.
   - Visited: the existing check mark, and visually hidden `copy.visited`.
   - Reachable: normal outline.
   - Unavailable: dashed outline and reduced opacity (no padlock or lock icon),
     and visually hidden `boardCopy.noRoute`.
   - Selected: the existing selected inset outline and `aria-pressed="true"`.
   Do not override the accessible name with `aria-label`; the label and state
   texts are the name. In list rows the same state text is visible.
8. **Detail card** (pinned, `aria-live="polite"`): the selected place's label as
   `h4`; one state line (current, visited, "can go here" text, the place's
   `moveHint`, or `noRoute`); the description, or `copy.noDescription`; and, only
   when the place is a legal destination and not current, one **Idź** button
   (`board.enter`, at least 48 px, `aria-label` "Idź: {label}"). When the sheet
   opens, the current place is selected, so the card never starts empty and
   there is no new prompt copy. While `locked`, the button is disabled and
   `copy.mapLocked` is shown. A prerequisite appears only in the card, next to the
   place it blocks.

### Travel

9. **Idź** sends the existing `MOVE` command. While pending, the sheet stays open
   with the accepted map and all controls locked. When the controller reports the
   move saved:
   - if the destination `moveBehavior` is `ENTER_SCENE`, close the sheet and
     focus the location heading (set the close in the existing layout effect
     for the `requested.entry` branch);
   - otherwise keep the sheet open, select the new current place and focus its
     tile, as the board does today.
   On a rejected or stale move, keep the sheet open, reselect the current place
   and show the existing status text. Movement authorization, legal-move
   filtering and persistence are unchanged; adjacency and links still authorize
   nothing.

### Copy

10. Add `menu` and `close` to `boardCopy` (EN: "Menu", "Close"; PL: "Menu",
    "Zamknij"). Delete the now-unused `aboutPlaces`. Reuse all other strings.
    Remove unused CSS: `.game-scene-map`, `.game-board-travel`,
    `.game-board-descriptions`.

## Out of scope

- The hub scene's six story options. If they duplicate map moves in the
  current pack, resolve that in the pack/text work (spec 40), not here.
- Focus and scroll reset after a scene changes (spec 39).
- Map reveal animations and reward feedback (spec 43).

## Verification

1. **Component tests** (`GamePlaces.test.tsx`, `GameRun.test.tsx`): the compact
   board renders one button per place and zero per-tile travel buttons; selecting a
   tile updates the card; the travel button appears only for a legal,
   non-current place; locked state disables it and shows `mapLocked`; a
   prerequisite hint appears in the card for the blocked place; the current place
   is selected on open; text mode and narrow width render the same selectable list;
   no `aria-label` override on tiles; the "About these places" disclosure is gone.
2. **Sheet behavior:** opens and closes with button, Escape and backdrop;
   Escape does not close the adventure overlay; focus goes to the title on open,
   back to **Mapa** on user close, and to the location heading after an accepted
   `ENTER_SCENE` move; a non-entry move keeps the sheet open and focuses the new
   tile; rejected and stale moves keep it open.
3. **Menu:** Text mode toggles without commands or storage and preserves
   draft/form/focus (existing 30.5 assertions); End adventure opens the dialog and
   Cancel returns focus to the Menu summary; the menu is hidden when no item
   applies; Escape closes only the menu.
4. **Browser suite** (`game-expeditions.mjs`, `game-honeycomb-board.mjs`,
   `game-hex-map.mjs`): at 360 × 640, Mapa and Menu are within the viewport and at
   least 44 × 44; the seven tiles render graphically with at least 92 px tile
   width; tapping the crossing before its prerequisite shows the hint and no
   button; tapping a reachable place, then **Idź**, enters it, closes the sheet
   and focuses the heading; keyboard-only travel works; 200% zoom has no
   horizontal overflow and the card stays inside the sheet; forced colors and
   Text mode show text and state. Save viewport screenshots to
   `docs/evidence/38/` (`menu-open.png`, `map-sheet.png`, `map-sheet-hint.png`,
   `map-sheet-list.png`).
5. **Gates:** `npm test` in `app/ui`, `./scripts/dev-workflow.ps1 -Action sync`,
   the Spring-served browser suite, and a manual pass on a real phone in Polish
   and English controls.
6. Add `docs/game-map-sheet-acceptance.md` in the style of the other acceptance
   documents, linking the evidence.
