# Progressive scene text acceptance

Illustrated expeditions with multiple story blocks start on the first block.
**Read more** or **Czytaj dalej** replaces it with the next block. Story
choices and ending actions stay available. The reading position follows the
current run and ordered text in memory. A reload starts at the first block.
[Spec 42](specifications/42-capybee-progressive-scene-text-instruction.md)

All blocks remain in reading order in the accessibility tree. Text mode and
forced colors display every block. After the last reveal, keyboard focus moves
to the final block when it was on the reveal button.

## Verification

- All 218 UI tests passed. Component tests exercise scope, revised copy,
  unchanged copy across a revision, final focus, and zero commands on reveal.
- The production UI built and synced to both Spring static directories.
- All 107 backend tests passed, including the Spring-served Chromium suite
  with PostgreSQL. At 360 × 640, the suite checked English and Polish controls
  on a three-block Hive ending, keyboard reveal, full accessibility-tree text,
  Text mode, forced colors, and zero game writes while reading.
