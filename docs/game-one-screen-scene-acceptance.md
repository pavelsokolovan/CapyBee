# One-screen expedition acceptance

[Spec 37](specifications/37-capybee-one-screen-scene-instruction.md) and its
[interactive prototype](specifications/37-capybee-scene-prototype.html) define
the expedition layout. The Spring-served checks use the reviewed Polish
**Nowy ul, dwa światła** pack and the production catalog artwork.

## Scene layout

The overlay header and visible run status stay above the scrolling scene.
The location heading sits on the artwork and becomes an ordinary heading above
the bubble in Text mode or forced colors. The opening's artwork and full story
text fit above its action at 360 × 640. The six-choice hub scrolls with its
choices in the body. The lantern keeps its three actions in view while the
body scrolls. Text entry, ending prose, and results stay in the body.
Recovery controls stay in the footer.

## Verification

- All 202 frontend tests passed. The expedition fixtures cover the action-count
  boundary, disabled choices and hints, text entry, recovery, endings, the
  location heading's DOM identity, and focus after a move.
- The PowerShell production build and static sync passed. Maven verification
  passed with 107 server tests, including the Spring/PostgreSQL browser suite.
  The browser checks cover separate scene and footer scroll areas, minimum art
  height, touch targets, tall-stage clipping, a CSS viewport equivalent to 200%
  zoom, and keyboard Text mode and forced-colors parity. The opening is checked
  with English and Polish controls at 360 × 640 and 360 × 560.
- A real-phone pass in both control languages with the browser address bar
  visible and hidden is pending. The viewport-height checks use Chromium
  emulation.

## Browser evidence

The captures show the full 360 × 640 viewport.

- [Opening with one pinned action](evidence/37/opening-mobile.png)
- [Hub with six inline choices](evidence/37/hub-mobile.png)
- [Lantern with three pinned actions](evidence/37/lantern-mobile.png)
- [Meadow encounter in Text mode](evidence/37/text-mode-mobile.png)
- [Meadow encounter in forced colors](evidence/37/forced-colors-mobile.png)
