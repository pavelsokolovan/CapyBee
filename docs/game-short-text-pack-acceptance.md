# Short-text pack acceptance

**Nowy ul, dwa światła** 1.5.0 uses short Polish narration and choice labels.
The hub keeps its rest choice and uses the map sheet for travel. The reviewed
endings, movement rules, places, and artwork stay as they were in 1.4.0.
[Spec 40](specifications/40-capybee-short-text-pack-1-5-0-instruction.md)

The supplied arrival and lantern passages were trimmed to fit the word
budgets. The Polish read-through checked the informal voice, optional help
and rest, the fiction cue, and room for both the old and new home.

## Compatibility

The 1.5.0 generation catalog drops the five retired hub choice IDs to match
the trusted scene. Pack validation counts map entries as potential edges in
authored graphs. Live travel still uses the same navigation rules and
prerequisites. The server and CLI package retain 1.4.0 at its exact version,
including its original six hub choices and prefetch timing.

## Verification

Pack tests check the text budgets, unchanged scene mechanics, exact ending
bytes, fallback parity, Polish quality profile, and a 1.4.0 snapshot resumed
through the current registry. Completion tests cover all three reviewed
endings on both versions. The browser suite exercises the map route, the three
companions, and all three endings on 1.5.0.

## Browser evidence

At 360 × 640, each scene shows its artwork, complete narration, and pinned
actions without scrolling the page.

- [Arrival](evidence/40/arrival-mobile.png)
- [Hub](evidence/40/board-mobile.png)
- [Lantern](evidence/40/lantern-mobile.png)
- [Mela](evidence/40/mela-mobile.png)
- [Borys](evidence/40/borys-mobile.png)
- [Luna](evidence/40/luna-mobile.png)
