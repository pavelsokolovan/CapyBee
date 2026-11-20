# Spec 44 — CapyBee expressions
**Status** — Implemented. See [acceptance](../game-capybee-expressions-acceptance.md).

**Depends on** — Specs 30.1, 30.2, 36, and 43.

**Intent** — [Illustrated Honeycomb RPG](intent/03-illustrated-honeycomb-rpg.md)

## Art

Update the fixed `character.capy` SVG for the stage, portrait, and map marker.
Keep the honey scarf, and draw smaller ears, a longer rounded muzzle, two short
antennae, and small pale wings. Keep the existing view boxes, anchors, and
orientation support. The marker uses a calm face and simplified lines.

Give CapyBee four readable expressions. Neutral looks calm, attentive looks
curious, pleased gives a warm closed-eye smile, and playful uses a wink and
small open smile. Playful is a local artwork variant for CapyBee and is
available in the development gallery. The wire expression vocabulary stays
at neutral, attentive, and pleased.

## Story reactions

Use the latest accepted public view for illustrated Hive expeditions. The
stage and current-character portrait show the reaction.

- A completed rest ending is calm. The other completed Hive endings are warm.
- At the lantern, meadow, reed bank, willow, and workshop, read the saved
  objective mapped to that place in spec 43. CapyBee is attentive while it
  is open, warm after completion, and playful after helping Borys.
- At the crossing CapyBee is attentive. The clearing and other Hive views
  use the calm face. Use accepted art bindings and objective states, with
  the calm face as the fallback.

The reaction represents the current story beat. Pending choices, map
inspection, reveal taps, language, and reload timing do not drive it.
Other characters retain their supplied expressions. History cards and map
markers keep their existing roles. Expressions are decorative in the scene
and convey no additional rules or instructions.

## Verification

Check fixed rendering, distinct faces, variant fallback, and the neutral
marker. Test the projection for pending and accepted objectives, endings,
and a non-Hive view. In Spring-served Chromium at 360 × 640, play an accepted
route through calm, attentive, warm, and playful faces. Check the stage,
current portrait, small map marker, and Text mode. Run the UI suite and
production static sync.
