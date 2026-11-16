# Spec 30.5 — Illustrated expeditions acceptance

Baseline: `5ccf735`. See the [specification](specifications/30.5-illustrated-expeditions-instruction.md).

The accepted current moment now combines a fixed catalog backdrop, current
character cards, shared dialogue and the existing choice/input renderer. Text
is on an opaque surface below the image. The board, party and expandable
history remain integrated without duplicated scene controls.

The English/Polish Text mode control hides encounter and companion art and
forces the board's semantic list. It preserves the response form, memory-only
draft, readable content, choices and focus. It sends no command and stores no
preference; a different run starts in illustrated mode. Forced colors suppress
art and expand text into the available card width. Missing presentation keeps
text-first play; unknown artwork uses neutral fixed catalog art.

No engine/server production code, Pack bytes, public contracts, queue, generation
settings or rewards changed. Pack 1.3.0 and retained saves keep their mechanics.
Generation/prefetch and visual release certification remain 30.6–30.7.

## Verification

- Frontend coverage: **24 files, 177 tests passed**. Tests include EN/PL mode
  controls, draft/form/focus preservation, zero toggle mutations, run replacement,
  pending accepted-state reuse, legacy text and hostile unknown artwork IDs.
  The configured offline coverage thresholds also passed.
- Official UI build/static sync passed; both static directories match `dist`.
- Integrated Spring/PostgreSQL browser gate passed (both preparation integration
  tests, coverage gates and Maven verification). The extended real-run check
  proved identical encounter text/choices, keyboard toggle focus, no toggle
  commands, board-list text mode, 200% zoom without horizontal overflow and
  forced-color text parity. Existing board, recruitment, generated fallback,
  offline/reload, owner/auth and PWA recovery checks also passed.
- The illustrated, text-mode and forced-color screenshots were visually inspected.
  A final frontend build/test pass also covers removal of the redundant board
  list toggle while text mode already forces its list.

## Visual evidence

Real accepted Polish-story encounters with English shell controls:

- [Illustrated mobile encounter](evidence/30.5/illustrated-mobile.png).
- [Equivalent text mode](evidence/30.5/text-mobile.png).
- [Forced colors](evidence/30.5/forced-colors.png).

The browser checks use the production Spring-served bundle and deterministic
providers. Tall screenshot crops extend viewport height to include the encounter.
This acceptance covers encounter composition, not full release certification.
