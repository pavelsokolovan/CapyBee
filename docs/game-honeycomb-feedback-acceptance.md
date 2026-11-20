# Honeycomb feedback acceptance

Five Hive encounter tiles fill when the corresponding saved objective is done.
A local completion animates on the next map opening. Text mode and the map
card expose the completion label. The existing travel and visit indicators
remain available. [Spec 43](specifications/43-capybee-honeycomb-feedback-instruction.md)

The expedition encounter fades in after an accepted local scene action.
The ending shows a single honeycomb-cell glyph after the server confirms an
optional claim. Reduced motion displays all three feedback states without
animation. The existing one-cell-per-day policy applies to the ending reward.

## Verification

- UI component checks cover visited and completed states, local versus passive
  updates, and map reopening.
- The production UI build synced to both Spring static directories.
- All 220 UI tests and 107 backend tests passed. The Spring-served Chromium
  suite at 360 × 640 verified a visit, the accepted lantern objective,
  first-open fill, reopened map, Text mode, reduced motion, and scene
  motion. It also verified the confirmed cell glyph and the daily cap.
