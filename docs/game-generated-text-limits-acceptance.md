# Polish generated scene word limits

Polish scene narration and each dialogue line have a 20-word ceiling. Generated
action labels have two to five words. The engine counts Polish diacritics, digits,
and internal apostrophes and hyphens consistently. Standalone icons and
punctuation do not count. [Spec 41](specifications/41-capybee-generated-text-limits-instruction.md)

Hive Pack 1.5.0 ships short authored and fallback copy together. The server and
CLI retain Hive 1.0.0 through 1.4.0 for existing saves. Both maintained dynamic
profiles apply the limits to Polish blueprint scene and choice intents because
their fallback can display these fields verbatim. A resumed dynamic run uses
the policy saved in its continuation.

A rejected scene uses the existing repair allowance and then the complete
validated fallback. Repair receives the affected field paths and the active
bounds. The choice IDs, their order, and their effects come from the trusted
scene definition.

## Verification

- Engine tests cover word boundaries, invalid configuration, Pack-load
  diagnostics, provider repair, exhausted budgets, blueprint review, fallback
  bootstrap, and exact resume under historical and current policies.
- All 15 Hive 1.5.0 authored scenes and their matching generated fallbacks
  satisfy the limits. Their routes, rules, and illustration bindings match
  Hive 1.4.0.
- The Spring/PostgreSQL suite exercised the lantern through generated output
  at the narration limit and through fallback after length rejection. At
  360 × 640 in Chromium, the art, full bubble, and three action buttons fit
  without scrolling. Text mode showed the same text and actions.
- The browser run exercised generated prefetch and command replay when scenes
  share a generation template. Scene artifacts now merge by scene ID so the
  current and earlier scenes are preserved during replay.
- The backend suite passed 107 tests using local PostgreSQL and test-only
  loopback OAuth settings. All 215 UI tests passed, and the production UI
  build synced to the Spring host.
- The standalone engine verification passed 284 tests and its coverage and
  boundary checks. Official archive verification passed, with matching Polish
  generic-adventure policy and byte-for-byte parity for the indexed Hive
  1.0.0–1.5.0 content in both the CLI and Spring Boot archives.
