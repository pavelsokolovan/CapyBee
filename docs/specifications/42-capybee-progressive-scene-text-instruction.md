# Spec 42 — CapyBee progressive scene text
**Status** — Implemented. See [acceptance](../game-progressive-scene-text-acceptance.md).

**Depends on** — Specs 36–39 and 41.

**Intent** — [Illustrated Honeycomb RPG](intent/03-illustrated-honeycomb-rpg.md)

Present one authored block at a time in illustrated expedition bubbles.
A child can read the next block with a tap or keep playing through the
existing actions. Text mode and screen readers offer the whole beat at once.

## Behavior

- Apply to the expedition layout selected by `sceneFirstKind` when the view
  has at least two blocks. Include illustrated endings. A block is a whole
  item from `view.blocks`, with its original text and speaker if present.
  The Anonymous Note episode, legacy runs, and zero- or one-block scenes
  use their ordinary full text rendering.
- Show the first block and a localized **Read more** / **Czytaj dalej** button.
  Each activation replaces the visible block with the next block. The
  control takes at least 44 CSS pixels vertically and works by touch,
  click, Enter, or Space. After the final block, remove the control. If it
  had focus, move focus to the final block without scrolling the page.
- Render all blocks once in reading order. Visually clip the other blocks
  while keeping them in the accessibility tree. Each retains its speaker
  and formatting. There is no additional live region. Text mode and forced
  colors show all blocks and omit the reveal control. Returning to the
  illustrated mode restores the current reading position.
- Keep reading position in memory for the current run and ordered block
  content. Different copy or a different run opens at the first block.
  Identical copy keeps its position through revision updates. A reload
  starts at the first block. A reveal sends no command or save, opens no
  map, and leaves story actions, focus on navigation, and results available.

## Implementation and verification

Extend the existing scene block renderer and expedition bubble styles.
Use the existing scene-action and navigation paths. Verify keyboard focus,
English and Polish labels, full Text mode and forced-color copy, scope,
and revision changes with component or browser checks. In the Spring-served
fixture at 360 × 640, use a multiblock Hive ending to verify one block at a
time, keyboard reveal, full Text mode and forced-color copy, and zero game
writes for reading. Run the repository UI tests and static sync.
