# Spec 29 — CapyBee Polish Game and Product Integration

**Type:** Umbrella implementation specification
**Parent:** [Universal game intent](intent/01-universal-game-engine.md)
**Depends on:** Implemented engine/session series and Specs 28.1–28.4
**Baseline:** `967e1d9`, inspected 2026-11-14
**Status:** Draft — split into 29.1–29.5
**Primary outcome:** A child can play a calm Polish CapyBee adventure, explore
fictional companions/places, and optionally choose a real-world Mission step,
within the existing shared daily game-cell policy.

## Evidence and boundary

Specs 28.3/28.4 are committed and pushed. The shared renderer, public places,
legal commands, recovery, owner guards and deterministic browser harness exist.
The first push check failed during Chromium installation on Ubuntu 26.04.
CI runner fix `a8857d9` pins that browser job to Ubuntu 24.04. Its checks must
pass before claiming deployment; this draft does not certify a production release.

The reviewed reference Pack `lanterns-of-willow-marsh` is English only; it is not
already a Polish CapyBee game. `app/engine/reference-game-packs/` has no dedicated
CapyBee Pack. 28.3 exposes a place list, not a graphical hex-map encounter game.
`GameService` accepts generic client game-key/metrics results; that endpoint is
not authoritative evidence of RPG completion. `useHoneycombCells` maps saved
game entries, while Spec 20 defines a shared one-game-cell-per-day UI policy.
The current Mission DTO exposes catalog entries; arbitrary generated custom
Missions must not be assumed to exist. Pack free-text setup is deliberately
restricted by 28.2 and needs a persistence/privacy audit before enablement.

This series implements the product layer without making the SDK CapyBee-aware.
Do not recreate web transport, auth, save/recovery or game mechanics. The Polish
Pack is a documented exception to bilingual story content; application copy
remains EN/PL. Existing English/dynamic sources and their saves remain valid.
No production flags, paid provider calls, social sharing, analytics, clinical
claims, streaks or competitive scoring are authorized by drafting these specs.

## Delivery parts

| Part | Outcome | Dependencies |
|---|---|---|
| [29.1](29.1-capybee-polish-authored-pack-instruction.md) | Polish Authored Pack | 28.4 |
| [29.2](29.2-capybee-theme-generation-and-safety-instruction.md) | Theme-first Generation and Safety | 29.1 |
| [29.3](29.3-game-hex-map-and-encounters-instruction.md) | Accessible Hex Map and Encounter UI | 29.1; 29.2 only for generated variants |
| [29.4](29.4-rpg-missions-and-daily-cell-integration-instruction.md) | Missions and Daily Game-cell Integration | 29.1; integrate with 29.3 UI when available |
| [29.5](29.5-capybee-game-release-acceptance-instruction.md) | Polish Game Release Acceptance | 29.1–29.4 |

Implement each part as its own reviewable commit after applicable checks, using
`spec <number> is implemented - <description>`. Map work may follow 29.1 without
waiting for paid generation. Missions/reward work may develop against authored
endings and integrate with the final map UI later. 29.5 closes all dependencies.
The parent stays Draft until all required parts pass.

## Product decisions carried from intent

The map is a Honeycomb Board; encounters use fictional animals/abstract helpers
with a trait and a need, and short Hive Expedition scenes. Old and new worlds
coexist. Remembering home and pausing are valid resolutions. Fictional allies
are never substitutes for real friends. Endings suggest one reviewed optional
real-life step; accepting it does not complete a Mission. Narrative outcomes
do not grade feelings or alter a child's worth/reward eligibility.

Use preset mood/interests only with explicit review and an optional transient
fictional wish. No personal notes or identity enter generation. Provider terms,
live-quality/cost certification and production activation remain separate
operator decisions. Any change to the inherited daily-cell/calendar policy must
be specified explicitly rather than introduced accidentally in implementation.
