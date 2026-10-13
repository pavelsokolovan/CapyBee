---
description: "Use when implementing the standalone game engine SDK, pack validation, engine tests, or game pack content. Owns universality and headless boundaries."
applyTo: "engine/**/*.java,engine/pom.xml,engine/src/**/resources/game-packs/**,app/server/src/main/resources/game-packs/**"
---

# Engine Standards

- The SDK lives in `engine/`, a standalone Maven module with no Spring Boot
  parent or application dependency. Root Maven only aggregates the modules.
- Universal and headless: no Spring, JPA, Hibernate, or
  `com.capybee.server` imports. Jackson 3 only in `json` folders and under
  `adapter`. `EngineBoundaryTest` enforces these rules.
- No theme, setting, genre skin, or product name in engine code, identifiers,
  or tests. Game-specific rules, player-facing text, and prompts are pack data.
- Return a presentation-neutral, text-first `View`; console I/O only in `cli`.
  The run API and minimal console arrive in spec 23, not spec 22.
- Time, randomness, and ids come through ports. No vendor/model names outside
  `adapter`. Do not create future ports before their owning spec.
- AI output is untrusted: pack-derived schemas, closed vocabularies, bounded
  values and authored fallback (spec 22, G1-G10). Never log player content.
- Apply U1-U5 from spec 22 before adding engine behavior. No host-specific
  safety, vocabulary, or locale assumptions in the SDK.
- Domain types are immutable records with defensive collection copies.
  Validators collect issues and use `ReferenceIndex` for cross-file links.
- `PLACES` is optional: `places.json` is required only for that feature;
  links govern direct `Move`, while `GO_TO` relocates without changing scenes.
  Keep place history ordered in run snapshots and expose resolved map data only
  through `View.places` (spec 24.1).
- Host-specific code stays in `app/server` with its existing layering.
  Do not wire the SDK into Spring before the web-host spec.
- Plain JUnit tests mirror engine packages under `engine/src/test/java`.
  Run `mvn -f engine/pom.xml verify` independently; 70% line coverage on every
  `com.capybee.engine.*` package. Never lower the backend service gate.
- Pack changes bump `version` and keep `files` synchronized with disk
  (`PackIndexLintTest`). Test fixtures live in `engine/src/test/resources`.