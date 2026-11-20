# Spec 43 — CapyBee honeycomb feedback
**Status** — Implemented. See [acceptance](../game-honeycomb-feedback-acceptance.md).

**Depends on** — Specs 29.4, 30.3, 38, 39, and 42.

**Intent** — [Illustrated Honeycomb RPG](intent/03-illustrated-honeycomb-rpg.md)

## Place completion

Use the existing saved Hive objectives to fill expedition map hexes. A
`DONE` objective fills its matching tile with honey gold. Use these bindings
for `capybee-new-hive` and intersect them with the run's actual places.

| Objective | Place |
| --- | --- |
| `carry-memory` | `old-lantern` |
| `listen-mela` | `quiet-meadow` |
| `help-borys` | `reed-bank` |
| `mark-luna-rest` | `willow-glen` |
| `prepare-rail` | `hive-workshop` |

Keep travel availability, visits, and the current marker independently
visible. The list and selected-place card also say **Completed** /
**Ukończone**. Ordinary visits and destinations without a mapped objective
keep their existing map behavior.

Animate the first fill seen on the map after a locally submitted, saved
action changes the objective to `DONE`. Queue that visual feedback until the
next map opening. A resumed run or passive update shows the saved fill
immediately. Reopening the map does not replay it.

## Ending and scene feedback

Show one honeycomb-cell glyph alongside the existing acknowledgement after
the server confirms a cell was added to the hive. A local claim animates
once when confirmed. Earlier claims display a filled cell on resume.
Use the existing optional claim, owner-scoped queue, and daily cap.
Place fills are visual progress within the adventure.

Apply a brief opacity transition to the expedition encounter after locally
accepted scene commands. Keep the existing focus and scroll behavior from
spec 39. Reduced motion disables all three animations while retaining the
filled tiles, cell glyph, and localized acknowledgement. Use the map's
existing list behavior in Text mode and forced colors.

## Verification

Cover completion mappings, visits, local accepted actions, passive updates,
reopened maps, and reduced motion. In the Spring-served browser suite, finish
the lantern objective and inspect the map at 360 × 640 in graphical and Text
modes. Check scene feedback and the server-confirmed hive cell, including
the existing once-per-day behavior. Run the UI suite and production static sync.
