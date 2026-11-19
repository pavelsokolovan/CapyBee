# Spec 32 — CapyBee: Scene-first expeditions

**Depends on:** 28.3, 28.4, 30.3–30.5 and 31

**Baseline:** `af27216`

Apply the episode's scene-first layout to **Nowy ul, dwa światła**, including
saved and generated runs of that adventure. The current location, story, and
available actions should lead the experience.

1. On opening or resuming the adventure, show the current location and a
   compact encounter with its legal actions. The reviewed opening's first
   action fits on a 360 × 640 phone at default text size. Use a full-width action
   treatment for story choices and keep the accepted story in its authored
   language. Localize interface controls in the player's app language.
2. Keep the illustrated board one tap away in a collapsed **Map** control.
   Turn the same board into a list in text mode. Opening, closing and inspecting
   the map are read-only. While a move is pending, keep the accepted map and
   scene visible with commands locked. On entry, move keyboard focus to the new
   location heading and keep the board available for further exploration.
3. Put character details, companions and objectives after the story choices.
   Place current character profiles behind a disclosure and hide the empty
   party panel. Keep the existing announcement when a companion joins.
4. Remove the repeated save and language paragraphs from active play. Announce
   successful saves without adding a visible row. Keep pending and recovery
   messages visible. Retain access to endings, Missions, text mode and the
   regular return to Play.
5. Keep the existing pack and persistence contracts. Verify exact resume,
   map entry, the opening on a 360 × 640 viewport, text-mode parity, legal
   moves during recovery, and both English and Polish controls using the
   Spring-served browser suite.
