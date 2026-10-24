# Intent: Dynamic AI game generation

Status: → specs 27.17–27.27
Date: 2026-10-24

## Problem

The current generated-game path changes presentation inside an authored game
pack. A player's theme or story wish cannot produce a genuinely different
premise, cast, objectives, routes and bounded consequences without a developer
first authoring those mechanics.

Players need a short preparation flow that can request a new game definition
while keeping execution deterministic and engine-controlled.

## Who it affects

- **Players:** choose friendly presets, optionally provide a short fictional
  idea, review it without having the text echoed, and start or cancel.
- **Host developers:** render the same host-neutral preparation contract in
  the console now and other clients later.
- **Engine maintainers:** validate and compile an untrusted AI blueprint into
  the existing supported mechanics instead of adding another runtime.

## Constraints

- Existing authored games and fixed-pack generated-presentation games remain
  compatible.
- The model may propose only bounded, supported mechanics. Deterministic code
  validates, assigns trusted IDs and compiles the whole definition before play.
- Optional player text is transient, safety-checked after confirmation and
  never persisted or included in diagnostics; the accepted game may reflect it.
- No provider SDK, UI framework, database, CapyBee theme or account-profile
  dependency enters engine core.
- Offline behavior is labeled honestly and uses a complete reviewed fallback;
  it is not described as a newly AI-generated game.
- A saved game restores its exact accepted definition and does not regenerate
  its blueprint.

## Open questions

Exact profile and blueprint schemas, mechanical limits, bounded playability
proof, compiler contracts, persistence format and CLI release behavior are
intentionally assigned to Specs 27.18–27.27. None changes the privacy, trust or
compatibility boundaries accepted in Spec 27.17.
