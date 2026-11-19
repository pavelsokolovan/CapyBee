# Spec 33 — CapyBee clear travel map

**Depends on:** 29.3, 30.3, 31 and 32

**Baseline:** `5e42269`

Simplify the optional map in **Nowy ul, dwa światła** for both reviewed and
generated stories. Apply the same travel controls in the illustrated board and
the map's list. A saved run with an older map still opens from the same Map control.

1. Mark the accepted location with the player marker on the board and a
   localized “You're here” label in both layouts. Keep visited places
   recognizable and use text as well as color to communicate movement state.
2. Give each destination advertised by the accepted view one labeled travel
   button. Keep current and unavailable places readable with no travel button.
   Keep the accepted destination and movement controls in place while a move
   is pending, and disable travel until the controller resolves it.
3. Put each route prerequisite alongside its destination. Show any scene-wide
   movement restriction once above the map. Move place descriptions into one
   optional disclosure and drop the general map instructions.
4. Keep the illustrated layout where it fits. Use the same list in text mode,
   at narrow widths, and for maps without usable artwork or coordinates.
   Keep 44 × 44 CSS pixel travel targets, wrapping labels, and keyboard focus
   on the accepted scene heading after entry.
5. Use the existing commands, movement authorization, and persistence.
   Verify current-place cues, the bridge prerequisite, read-only disclosures,
   keyboard travel, pending and stale moves, map/list parity and exact resume
   in the Spring-served browser suite.
