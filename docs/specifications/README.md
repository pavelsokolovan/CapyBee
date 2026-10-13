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
Spec 22 lives in the independent `engine/` Maven module. Console-first runtime
and host integration follow in the series below.

## Planned Documents

- 23 - Engine API and minimal authored console loop
- 24 - Rules, state, and snapshots
- 24.1 - Places and map runtime
- 25 - Templates, kinds, and generation stages
- 26 - Input and safety policy
- 27 - Generation pipeline and reference adapters
- 28 - Console host, scripted playthroughs, and golden transcripts
- 29 - Web host integration
- 30 - Client rendering and asset catalog
- 31 - Polish game pack, Play tab entry, and rewards
- deployment checklist
