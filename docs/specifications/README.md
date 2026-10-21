# CapyBee Specifications

This folder collects the implementation-facing documents that turn the product concept into buildable pieces.

Before you spec something
For anything substantial (new screen, new feature, data model change), write a short intent note first — see intent/README.md. Numbered specs below should always trace back to one.

## Foundational Documents

- `00-overview.md` - product scope, first page, auth flow, data model, and deployment path
- `01-user-stories.md` - MVP user stories with acceptance criteria and priorities
- `02-data-model.md` - current schema and Phase 2 target data model additions
- `03-api-contract.md` - implemented API endpoints and Phase 2 contract targets
- `04-screen-descriptions.md` - detailed MVP screen behaviors, states, and user flows

## Feature & Redesign Instructions

- `05-avatars-adding-instruction.md` - CapyBee avatar asset set and usage rules
- `06-capybee-phrases-instruction.md` - bilingual CapyBee response phrase pools
- `07-honeycomb-cells-instruction.md` - honeycomb map cell design, colors, and data mapping
- `08-missions-redesign-instruction.md` - missions screen redesign
- `09-friendship-tracker-improvements-instruction.md` - friendship tracker UX improvements
- `10-memories-screen-redesign-instruction.md` - memories screen illustration and card hierarchy
- `10.1-memories-card-interactions-instruction.md` - memory card icon sizing, favorite pinning, delete undo
- `11-landing-page-redesign-instruction.md` - minimal kid-facing landing page
- `12-navigation-redesign-instruction.md` - 4-tab bottom navigation + profile in top bar
- `13-onboarding-tutorial-instruction.md` - first-run onboarding tutorial
- `14-session-persistence-instruction.md` - localStorage restore-token session persistence
- `15-offline-write-queue-instruction.md` - offline write queue for check-ins, missions, friendships, memories
- `16-pwa-app-shell-instruction.md` - installable PWA app shell
- `17-pwa-navigation-hang-fix-instruction.md` - PWA navigation hang fix
- `18-missions-categories-and-expansion-instruction.md` - mission categories and catalog expansion
- `19-missions-accordion-list-instruction.md` - missions accordion list UI
- `20-pollen-match-minigame-instruction.md` - Pollen Match mini-game
- `21-hive-builder-tap-minigame-instruction.md` - Hive Builder Tap mini-game
- [22-engine-core-and-pack-format-instruction.md](22-engine-core-and-pack-format-instruction.md) - standalone engine SDK, pack format, loader, and boundary tests
- [24.1-engine-places-and-map-instruction.md](24.1-engine-places-and-map-instruction.md) - optional place graph, movement, and map runtime

All of the above are implemented; see [../../.github/copilot-instructions.md](../../.github/copilot-instructions.md) for current coding conventions.
Spec 22 lives in the independent `app/engine/` Maven module. Console-first runtime
and host integration follow in the series below.

## Engine Roadmap Reconciliation

The earlier planned list below used different ownership for several numbers.
Keep existing numbered files stable; do not renumber them to match that older
list. The current implementation boundary is:

- 23 - Engine API and authored runtime
- 24 - Rules, state, and snapshots
- 24.1 - Places and map runtime
- 25 - Input and safety policy
- 26 - Provider-neutral generation pipeline
- 26.1 - Generation catalog/contracts, generated runtime integration, CLI mode,
  fallback/repair behavior, and artifact snapshot continuity (implemented)
- 27 - Public session lifecycle, host-facing API contract, web host and queue integration
- 27.1-27.3 - Durable local sessions, console coverage, and measurable prefetch behavior
- 27.4 - Versioned full generated-game contract, structural preflight, and capability metadata
- 27.5 - Generated story planning (implemented)
- 28 - Client rendering contract
- 29 - CapyBee Polish game pack and game-pack integration

### Roadmap reconciliation

The web host and server-side SDK wiring move from the earlier "spec 29" plan
to spec 27. The expanded local console host is part of the spec 27 session
contract rather than a separate numbered spec. Existing numbered specification
files remain unchanged.

Spec 26.1 found no existing Spec 24 template-definition package. It therefore
adds the smallest versioned `generation/templates.json` registry and documents
that compatibility addition in [the engine guide](../../app/engine/README.md).
