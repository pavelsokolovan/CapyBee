# Spec 31 — CapyBee: The Anonymous Note

**Depends on:** 28.2–28.4, 30.2 and 30.5

**Baseline:** `7248436`

## Player experience

Publish **Tajemnicza wiadomość** as a short, authored Polish episode in Play → Adventures. Start from the episode card when there is no active adventure. Use the existing resume and conflict rules when there is a saved adventure. Continue to offer **Nowy ul, dwa światła** and resolve its saved runs against their original pack versions.

| Beat | Location | Player action and result |
| --- | --- | --- |
| 1 | Polana Nowego Ula | Read an unsigned note found at the hive. Its drawing points toward Mela at Cicha Łąka. |
| 2 | Polana Nowego Ula | Visit Mela from the scene or the map. Both actions open the same encounter and save the new location. |
| 3 | Cicha Łąka | Show Mela the note or follow tracks in the flowers. Mela recognizes the handwriting from a trail sign on the first route. The tracks lead to a matching hand-painted arrow on the second. |
| 4 | Cicha Łąka | Reveal that Borys made a trail for newcomers and left a blank sign for the next explorer. A final line identifies what the player discovered. Offer a clear return to Play. |

## Presentation and behavior

1. Put the episode title, compact illustrated scene, immediate goal, and first action above the fold on a 360 × 640 phone at default text size. Show CapyBee, Mela, and the envelope in context. Use the existing art catalog and give the two locations distinct compositions.
2. Use short Polish copy for the episode, its map, its run controls, and its ending. Give the single action visual priority and give both choices equal weight at the fork.
3. Put the two-location map behind a labeled control. Identify the current location and show one travel action when its destination is reachable. Give a reason when a route is closed. Reuse the accepted view's legal moves.
4. Keep text mode accessible from the scene, with the same story and actions. Give controls at least 44 × 44 CSS pixels, preserve keyboard focus, and let larger text wrap. Decorative artwork should be hidden in text mode and forced colors.
5. Use the existing owned run, command, and persistence APIs. Restore the accepted location, scene, clue, and branch after reload. Keep pending and failed-save feedback available and show successful saves briefly. Keep the completed scene visible until the player returns to Play.
6. Verify both branches, map entry, exact resume, text mode, mobile layout, and the ordinary UI and backend build gates. Playtest duration and demand for a follow-up episode as separate product work.
