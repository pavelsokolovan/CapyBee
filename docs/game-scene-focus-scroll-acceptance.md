# Scene focus and scroll acceptance

A locally accepted action that moves focus to a heading first resets the
scene's scroll position. Expeditions reset their scene body. Legacy and
Anonymous Note runs reset the adventure overlay. Entering a run clears any
overlay scroll left over from setup or review.
[Spec 39](specifications/39-capybee-scene-focus-and-scroll-instruction.md)

Background revisions and rejected story choices preserve the reader's scroll
and focus. Location-only travel and rejected or stale map moves retain the map
sheet's focus behavior. The scene body, map sheet and footer contain overscroll.
The story behind an open map sheet keeps its position.

## Verification

- All 215 frontend tests passed. They exercise instant resets on both axes,
  focus with `preventScroll`, all three layouts, and background and rejected
  updates.
- Production build and static sync passed. The Spring-served browser checks
  exercise a scrolled review, local advances with English and Polish controls,
  keyboard travel, and scrolling against the map backdrop. At a 200%
  zoom-equivalent viewport, a saved scene reserves room for the focused
  location heading above the scrollable footer.
- A manual pass on a physical phone with Polish and English controls is pending.

## Browser evidence

- [After Continue at 360 × 480](evidence/39/after-advance-small-viewport.png)
- [After a story choice at the 200% zoom-equivalent viewport](evidence/39/after-advance-200-zoom.png)
