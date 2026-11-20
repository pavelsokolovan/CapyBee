# Map sheet and scene menu acceptance

The expedition controls follow the layout and two-step travel design in spec
38 and its prototype. The Spring-served checks use the reviewed Polish
**Nowy ul, dwa światła** pack and the production catalog artwork.
[Spec 38](specifications/38-capybee-map-sheet-and-scene-menu-instruction.md) ·
[Interactive prototype](specifications/38-capybee-map-sheet-prototype.html)

## Travel and controls

Map opens a native bottom sheet over the scene. It starts with the current
place selected. Selecting a tile changes the detail card. Its travel button
uses the server's advertised destinations and keeps the accepted map visible
while the command is pending. An accepted encounter closes the sheet and
focuses the new location heading. Location-only travel keeps the sheet open
and focuses the new current tile. Rejected or stale travel keeps the sheet
open with the accepted current tile selected and the run status visible.

Menu holds Text mode and End adventure when they apply. Escape closes the
disclosure, and cancelling End adventure restores focus to its summary.
Text mode preserves drafts and switches the map to selectable text rows.
Forced colors and narrow viewports use the same rows. The legacy map and
the Anonymous Note controls retain their existing behavior.

## Verification

- All 208 frontend tests passed. They cover selection, authorization, prerequisites, pending and
  rejected moves, focus restoration, and Text mode draft and storage parity.
- The PowerShell production build and static sync passed. Maven verification
  passed all 107 server tests, including the Spring/PostgreSQL browser suite. The integrated
  browser checks exercise the seven-tile map at 360 × 640, keyboard travel,
  dismissal by button, Escape and backdrop, a 200% zoom-equivalent viewport,
  forced colors, offline acknowledgement recovery, replay, and a stale
  command from a competing tab. English and Polish controls were exercised.
- A manual pass on a physical phone with Polish and English controls is pending.

## Browser evidence

These captures show the full 360 × 640 viewport.

- [Scene menu open](evidence/38/menu-open.png)
- [Map sheet with the current place selected](evidence/38/map-sheet.png)
- [Locked crossing with its prerequisite](evidence/38/map-sheet-hint.png)
- [Selectable list in Text mode](evidence/38/map-sheet-list.png)
