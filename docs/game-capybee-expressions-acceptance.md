# CapyBee expressions acceptance

The same CapyBee artwork supplies the figure, portrait, and map marker. It
has a rounded muzzle, smaller ears, short antennae, pale wings, and the
honey scarf. The calm, attentive, warm, and playful faces use fixed geometry.
The marker uses the calm face. [Spec 44](specifications/44-capybee-expressions-instruction.md)

The stage and current-character portrait react to accepted Hive objectives
and endings. Playful is a local CapyBee artwork variant. The public
expression vocabulary remains neutral, attentive, and pleased.

## Verification

- All 222 UI tests passed. Coverage includes distinct SVG geometry,
  supported variant and expression fallback, the calm marker, pending and
  accepted story updates, and completion outcomes.
- The production UI built and synced to both Spring static directories.
- All 107 backend tests passed on the full rerun. The Spring-served Chromium
  suite at 360 × 640 exercised all four faces along the Hive route and
  verified the current-character portrait, calm map marker, and Text mode.
  The first run timed out in the existing offline hex-map check.
