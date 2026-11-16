# Spec 30.4 — Befriend and Recruit acceptance

Baseline: `055b4c6`. Implementation date: 2026-11-16.
See the [specification](specifications/30.4-befriend-and-recruit-instruction.md).

## Delivered behavior

Current characters have fixed SVG portraits, public names, traits and needs.
Discovered characters retain their expandable history and descriptions. A
compact party shows only discovered characters included in the accepted public
party list and marked recruited. Joined characters label their original need
as something helped with. Empty parties have calm English and Polish copy.

Identity joins use opaque public IDs; authored and generated characters use the
same renderer. Unknown artwork uses the neutral catalog character. Missing
presentation preserves the previous text panel. Story strings remain escaped
React text, and portraits are decorative beside readable names. No portrait
surface submits a command. Existing authored choices remain the way to help.

Pending, offline and rejected commands retain accepted membership. Initial
and resumed membership is silent; later eligible accepted joins use a polite
announcement without focus changes. Party state is derived from the current
view and has no additional browser persistence.

## Verification

- Frontend coverage run: **22 files, 167 tests passed**, including 12 new
  public-data/component tests. The existing offline coverage thresholds passed;
  these percentages measure the configured offline files, not the entire UI.
- Official PowerShell UI build/static sync passed. Both server static
  directories were compared with `app/ui/dist` and matched exactly.
- Integrated Maven `-B -f app/pom.xml -pl server -am verify` passed: all
  five modules succeeded, including **107 server tests**, with coverage gates
  met. Spring served the production bundle against PostgreSQL and Chromium.
  Process-local OAuth endpoints avoided external discovery; production
  authentication configuration was not changed.
- The browser exercised authored and generated-fallback recruitment, pending
  request retention, one command per help choice, replay and exact reload,
  three companions, valid decline/rest paths and completed runs. Existing
  offline, recovery, two-tab and private-storage sentinel checks also passed.
- Layout checks cover 360px and desktop, 200% zoom without page overflow,
  keyboard history/focus, forced colors, hidden-art text parity and reduced
  motion. See [machine-readable evidence](evidence/30.4/browser-verification.json).
- After centering screenshot captures beneath the sticky navigation, the
  targeted `PreparationHttpIntegrationTest` browser gate passed again. Mobile,
  desktop, needs, joined state, zoom and forced-color images were inspected.

## Visual evidence

Captured from real accepted runs served by Spring, using Polish story text
with English shell labels:

- [Mela before help](evidence/30.4/mela-needs-characters.png) and
  [after accepted help](evidence/30.4/mela-joined-characters.png).
- [Three-member mobile party](evidence/30.4/party-mobile.png),
  [desktop party](evidence/30.4/party-desktop.png) and
  [desktop encounter characters](evidence/30.4/characters-desktop.png).
- [200% zoom](evidence/30.4/party-zoom.png),
  [forced colors](evidence/30.4/party-forced-colors.png) and
  [hidden artwork](evidence/30.4/characters-without-art.png).
- [Generated-fallback party](evidence/30.4/generated-joined-party.png), using
  engine-owned character IDs through the same public-data join.

Screenshots are cropped to the affected surfaces. They illustrate the new
cards and party, not acceptance of the full board or future encounter layout.

## Scope

Pack 1.2.0 and earlier resources, engine/server production code, recruitment
flags, command contracts, storage and provider settings are unchanged. Tests
use deterministic fixtures; no live generation provider is required.

Spec 30.3 remains a board draft. This implements 30.4 independently through
30.2's public contracts. Illustrated encounter composition, generation/prefetch
and complete visual release acceptance remain 30.5–30.7.
