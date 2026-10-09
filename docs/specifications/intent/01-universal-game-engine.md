# Intent: Universal AI-assisted game engine, and CapyBee's first game on it

Status: → spec 22 (engine series 22–31)
Date: 2026-10-08

## Problem

Two needs, one solution.

1. **A better game for CapyBee.** The two mini-games (Pollen Match, Hive
   Builder Tap) are tiny fixed loops: no story, no characters, no reason to
   come back, and nothing that rehearses the real social steps the app exists
   for (greeting someone, asking to join, being brave once). Hand-writing
   enough story content for a 12-year-old is not realistic for a solo hobby
   project. Light Claude models (Haiku-class) make endless variety cheap, but
   only if they fill a hardcoded structure instead of inventing a game every
   time.
2. **A reusable engine.** The engine must not be tied to CapyBee, or to any
   theme or vibe, and must be drivable from anything, including a plain
   console: to test the engine without the web stack, and even to make a
   console game. Built as CapyBee feature code (Spring, database, React, hive
   vocabulary), it would be untestable without the whole app.

Approach: a universal, headless engine plus data-only "packs". CapyBee's game
is one pack plus the web host. A console is another host.

## Decisions so far

### Engine (universal)

- **Theme-free.** No theme, setting, language, screen, or database in engine
  code. Everything game-specific is a pack: rules, scene kinds, entity kinds,
  generation stages, prompts, texts, optional assets.
- **One in-process API, many hosts.** Console, web server, and tests are
  adapters over the same API. The engine returns a presentation-neutral,
  text-first `View` and accepts a small set of commands.
- **Deterministic and headless.** Time, randomness, and ids are injected; no
  console, UI, or network code in the core. Reference adapters (in-memory
  store, fake/record/replay generator, console renderer) ship with the engine.
- **Console is a first-class host,** for development and testing and, if
  wanted, as a shippable console game.
- **Packs can be fully authored, fully generated (with authored fallback), or
  mixed.** The AI layer is optional.
- **AI role:** light Claude models through a provider-agnostic port. AI fills
  slots in pack-defined templates. It never defines rules or the pipeline,
  and never draws (it can only reference asset ids).
- **Console-first delivery:** prove the generation loop in the console before
  building the web host, persistence, and UI.
- **Process:** specs 22–31, one at a time.

### First game (CapyBee pack, Polish)

- **Shape:** a combination of three directions: Honeycomb Board (hex-map RPG)
  as the map, Befriend & Recruit (heroes with a trait and a need) as the
  content inside hexes, Hive Expeditions scenes (backdrop, character line,
  choices) as the encounter view.
- **Theme first:** a run starts by generating a main theme from the child's
  preferences, today's mood, and an optional free-text wish. Scenario,
  quests, heroes, and dialogues are then generated from that theme. In the
  engine this is simply the pack's declared generation stages.
- **Loose arc:** Arrival → Obstacle → Helper → Choice → Resolution → … is not
  a fixed sequence; steps may repeat.
- **Dialogue:** choices, with free text available as one of the options.
- **Language:** Polish only at the start.

## Who it affects

- **Developer (Pavlo):** plays and tests packs from the console, writes new
  packs, and can reuse the engine for other games.
- **Child:** new game entry in the Play tab next to the mini-games; reads
  today's mood from the check-in; every run ends by pointing to one real-life
  step (suggested as a Mission); may earn the daily honeycomb cell under the
  existing game-cell cap.
- **Parent:** needs a clear disclosure that the child's choices and optional
  free text are sent to a third-party AI provider. Mission history stays as
  is; game transcripts are not part of it.
- **Touches (CapyBee host only):** Play tab game list, Missions, check-ins,
  honeycomb cells, and the safety/privacy rules in `copilot-instructions.md`.

## Constraints

### Engine

- **Universality must be testable.** Fixture packs of different shape,
  locale, and feature set (text-only authored, rooms-and-items text game,
  generated with assets and free text) load with the same code and, from
  spec 28, play to an ending in the console. Playing needs no Spring,
  database, network, or API key.
- **Headless and deterministic.** Same pack version, inputs, seed, and
  generator responses produce the same transcript.
- **Provider-agnostic.** No vendor, model name, or prompt text in engine
  code; the LLM is reached only through a port.
- **Closed-world generation with bounded cost.** Structured output validated
  against pack-derived schemas, a per-run call/token budget, and authored
  fallback so a failure never becomes an error screen.
- **Safety hooks live in the engine:** free text is an explicit per-pack
  opt-in, and no player content is written to logs.
- **Single Maven module for now.** The package boundary is enforced by tests;
  extract a module when a second game actually consumes the engine.

### First game (CapyBee)

- **No PII / real names, and a new risk.** Free text and chosen interests
  leave our system for an LLM API. Needs scrubbing, no identifiers in
  prompts, no content in logs, and provider retention terms checked.
- **No social features between children.** Content generated from one
  child's free text is never cached or shared for others. Only content built
  from preset (non-personal) inputs may be pre-generated and shared.
- **Bilingual EN/PL: documented exception.** The CapyBee pack is Polish only
  at first. The UI around the game stays bilingual. The engine and pack
  format support any locales.
- **No padlocks / hearts / human figures.** Heroes are animals or abstract
  shapes; the Old World motif stays the lantern.
- **Homesickness is never a problem to fix.** No "it'll get better". Heroes
  are fictional and never a replacement for real friends.
- **Distress signals in free text** (sadness, fear, self-harm hints) need a
  defined safe path that does not go through the generator.
- **Fly.io cost and latency:** per-child daily budget, no always-on process.
  LLM latency plus scale-to-zero cold starts must not feel broken.
- **Offline-first writes** where possible; generation itself needs network.
- **Calm design:** short sessions, skippable at any moment, no streaks, no
  competition (inherited from specs 20–21).

## Open questions

1. How is the console host launched (IDE run configuration, fat JAR, or a
   Maven profile)? Decided in spec 28.
2. Does the engine get its own neutral name (for the console tool, docs, and
   a future extraction) instead of living under the CapyBee namespace?
3. LLM provider terms: data retention and zero-retention availability for a
   children's app; where the API key lives; daily budget per child.
4. Free-text safety: rules vs. a model-based pre-check; what the child sees on
   a distress signal; whether a parent is told.
5. Where "preferences" come from: picked per run (chips at start) or stored
   on the child profile? Per-run keeps data minimal.
6. Who produces the SVG part catalog, and how many parts before it feels
   repetitive?
7. Does a run share the existing 1-cell/day game cap or get its own?
