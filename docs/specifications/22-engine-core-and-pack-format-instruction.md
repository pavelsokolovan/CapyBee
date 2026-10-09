# 22 — Engine Core and Game Pack Format

## Purpose

First spec of the engine series (22–31; see intent note
`docs/specifications/intent/01-universal-game-engine.md`).

The engine is a **universal, headless runtime for AI-assisted interactive
games**. It knows no theme, setting, language, screen, or database. A game is
a **pack** (static data). Anything that drives the engine (a console, a web
server, a test) is a **host**, and every host uses the same in-process API.
CapyBee's first game is one pack plus one host; it is not special to the
engine.

This spec fixes:

1. The architecture and boundaries: headless, host-agnostic, deterministic.
2. The **Engine API principles** that make a console a first-class host
   (full contract: spec 23).
3. A theme-free **vocabulary**, shown to fit three very different games.
4. The **closed-world generation contract** that keeps AI output safe,
   bounded, and cheap.
5. The **Game Pack** format, plus its loader and validator.

Code delivered: standalone `engine/` Maven SDK (`com.capybee:game-engine`),
pure-Java `com.capybee.engine.pack` with tests, and a boundary test that guards
universality. No database, HTTP endpoint, LLM call, UI, or Spring wiring. The
running app does not change.

### Implementation decisions (2026-10-09)

Pavlo requested a separate SDK module so engine development and testing need
none of the app. This overrides the draft's single-module choice: `engine/`
has its own POM with no Spring parent; a root aggregator lists `engine` and
`app/server`. The backend does not depend on the SDK until spec 29. Existing
Docker and backend dev commands therefore stay unchanged. CI verifies the
SDK in a separate job with no application services.

The working namespace `com.capybee.engine` is retained, but all implementation
and tests live outside `app/`. Console-first delivery, explicit features,
one free-text input, JSON/Markdown packs, and one pack with multiple locales
remain as specified. No neutral public product name is invented. The SDK
declares Jackson 3 and JUnit explicitly because it cannot inherit them from
the application. No dependency is added to the backend.

### Series map

| Spec | Scope |
|---|---|
| 22 | This spec: architecture, API principles, vocabulary, generation contract, Pack format + loader |
| 23 | Engine API: `Engine`, `Run`, `Command`, `View`, `Event`, ports, reference adapters, authored-scene runtime, a minimal console loop |
| 24 | Rules and state: counters, flags, sets, effects, conditions, objectives, optional place graph, snapshots |
| 25 | Templates and stages: scene kinds, entity kinds and presets, stage graph, slot schemas |
| 26 | Input and safety policy: free text, scrubbing, moderation, distress path |
| 27 | Generation pipeline: `TextGenerator` port, provider adapter, prompts, validate/repair/fallback, caching, budgets, record/replay |
| 28 | Console host: play/validate/simulate commands, scripted playthroughs, golden transcripts; also usable as a shippable console game shell |
| 29 | Web host integration: HTTP mapping of the Run API, persistent run store, offline queue, events → CapyBee |
| 30 | Client rendering: `View` → React (scene and board screens), asset catalog |
| 31 | CapyBee pack (Polish), Play tab entry, rewards |

The order is deliberately **console-first**: after spec 23 a hand-written pack
is playable in a terminal, and after spec 27 a generated run is too. The web
host (29) comes only once the loop is proven headless.

## Ground truth reviewed

- `app/server/pom.xml` — single Maven module; Spring Boot parent 4.1.0, Java
  25. JaCoCo `check` rule: 70% line coverage on `com.capybee.server.service.*`
  only. No LLM/AI client dependency. Jackson arrives transitively through
  `spring-boot-starter-web`; no source file imports Jackson today (JSON is
  only used through Hibernate `@JdbcTypeCode(SqlTypes.JSON)`).
- `Dockerfile`, `.github/workflows/build-test-deploy.yml`,
  `scripts/dev-workflow.ps1` — all assume one module at `app/server`. The
  Dockerfile copies only `app/server/pom.xml` and `app/server/src`; CI runs
  `mvn -B verify` in `app/server`. There is no parent POM at the repo root.
- `.github/instructions/java-server.instructions.md` — layering
  `web → service → repository → domain`, constructor injection, DTO records,
  tests mirror package structure.
- `app/server/.../domain/game/GameResult.java`, `service/GameService.java` —
  "game" already means mini-game result storage in the host; the engine uses
  different names.
- `app/server/src/main/resources/db/migration/` — latest migration is
  `V11__game_results.sql`. This spec adds no migration.
- `docs/specifications/20-…` and `21-…` — mini-games; inherited host rules: no
  fail state, no cross-child comparison, 1 hive cell per day across games.
- `docs/specifications/README.md` — stale (lists specs only up to 19); fixed
  in the rollout checklist.
- `.github/copilot-instructions.md` — non-negotiables for the CapyBee host: no
  inter-child sharing, minimal PII, no third-party analytics/ads/tracking,
  warm tone, offline-first writes.
- Spring Boot 4 ships **Jackson 3** by default: processing classes live in
  `tools.jackson.*` (immutable `JsonMapper`, unchecked exceptions), while
  `jackson-annotations` keeps the package `com.fasterxml.jackson.annotation`.
  Jackson 2 may also be on the classpath; the engine must use Jackson 3.

## Constraints

- **Universal.** No theme, setting, genre skin, or product name in engine
  code, identifiers, or tests. CapyBee exists only as a pack and as a host.
- **Headless.** The core never prints, reads input, or knows a screen. It
  returns a presentation-neutral `View` (§2).
- **Deterministic.** Time, randomness, and ids come only through injected
  ports.
- **Provider-agnostic.** No vendor, model name, or prompt text in the core.
- Engine core is pure Java: no Spring, JPA, or Hibernate, and no dependency on
  `com.capybee.server.*`. Allowed: JDK, SLF4J API, Jackson 3 (only inside
  `json` and `adapter` packages).
- A pack is static content shipped in the JAR. It never contains per-user data
  and is never mutated at runtime.
- AI never defines rules or the pipeline (§4).
- The engine and pack format are locale-agnostic. CapyBee's first pack is
  Polish only, a documented exception to "Bilingual EN/PL required for every
  string" that belongs to that pack and is recorded in spec 31.
- Host-level safety, privacy, and tone rules in `copilot-instructions.md`
  apply to CapyBee's host and pack. The engine supplies the hooks: free text
  is an explicit pack opt-in and player content never goes to logs.
- No schema change, endpoint, UI, or new backend dependency in this spec.
  The independent SDK declares Jackson 3 and test-scoped JUnit itself.

## 1. Architecture

### 1.1 Parts

| Part | What it is | Changes with |
|---|---|---|
| **Engine core** | Generic runtime: pack loading, rule interpretation, run state machine, generation orchestration, `View` building | Code release |
| **Pack** | Static, versioned game definition: rules, scene and entity templates, stages, prompts, texts, authored scenes, optional assets | Content release (ships in the JAR) |
| **Run** | One play: start inputs, state, generated content so far, transcript | Every play |
| **Host** | Anything that drives the engine: console, web server, test harness, bot | Host release |
| **Adapter** | An implementation of an engine port: storage, text generation, clock, random, ids | Per environment |

Rule of thumb: *if switching to another game would change it, it belongs in
the pack; if switching from console to web would change it, it belongs in the
host or an adapter. The engine is what remains.*

### 1.2 Packages and dependency rules

```
com.capybee.engine
 ├─ pack       model, issues, sources, validation plumbing               (22)
 │   └─ json   Jackson-based PackLoader and parsers                      (22)
 ├─ api        Engine, Run, Command, View, Event, StepResult             (23)
 ├─ rules      counters, flags, sets, effects, conditions, state         (24)
 ├─ template   scene kinds, entity kinds, stages, slot schemas           (25)
 ├─ input      free-text handling and safety policy                      (26)
 ├─ generation stage runner, prompt assembly, validate/repair/fallback   (27)
 ├─ port       interfaces implemented by adapters                        (23+)
 ├─ adapter    no-Spring implementations: in-memory store, fake/record/replay
 │             generator, seeded random, fixed clock, provider adapters  (23, 27)
 └─ cli        console host: renders View to a terminal, reads stdin     (23, 28)
```

Rules, enforced by `EngineBoundaryTest` (§8.1):

1. The engine never imports `com.capybee.server..`, Spring, Jakarta
   Persistence, or Hibernate.
2. Jackson appears only in `json` subpackages and under `adapter`. Domain
   types stay Jackson-free.
3. No theme vocabulary in engine sources (the list lives in the test and grows
   with new games).
4. **Console I/O only in `cli`:** `System.out`, `System.err`, `System.in`,
   `System.console`, `Scanner(System…)`.
5. **Determinism:** wall clock, randomness, and id generation only through
   ports. Direct use of `Instant.now`, `LocalDateTime.now`,
   `System.currentTimeMillis`, `new Random`, `ThreadLocalRandom`,
   `Math.random`, and `UUID.randomUUID` is allowed only under `adapter` and
   `cli`.
6. **No vendor names** (LLM providers, model names) outside `adapter`.
7. Dependencies point host → engine only. The engine reaches a host only
   through returned values (`View`, events) and ports.

### 1.3 Independent SDK module

`engine/` is a standalone Maven module producing a plain JAR. It has no Spring
parent or application dependency. `mvn -f engine/pom.xml clean verify` builds
and tests it with only a JDK and Maven. It can also be opened as a separate
IDE project. The root POM aggregates the SDK and backend without becoming
their shared parent; `mvn verify` at the repo root verifies both.

Compile-time isolation prevents an SDK dependency on the host, and the source
boundary test also protects purity and universality inside the SDK. Its own
JaCoCo gate covers every engine package; the backend service gate is untouched.

The console host (`cli`) will live in the SDK module and use the same API as
the eventual web host. Spec 23 adds the minimal authored loop; spec 28 decides
the full console launch and commands. No runnable console is delivered in 22.

Backend builds, Docker packaging, and dev scripts still point at `app/server`
because the app does not consume the SDK yet. Spec 29 will add the host-to-SDK
dependency and reactor-aware deployment/dev builds when actually needed.

### 1.4 Ports (reserved names)

| Port | Responsibility | Defined in |
|---|---|---|
| `PackSource` | Read a pack's files by relative path | **22** |
| `Clock`, `RandomSource`, `IdSource` | Injected time, randomness, ids | 23 |
| `RunStore` | Persist and load run snapshots | 24 |
| `InputGuard` | Screen and reduce free text before it reaches content | 26 |
| `TextGenerator` | One LLM call → validated JSON text | 27 |

Only `PackSource` is implemented in this spec. Do not create empty interfaces
for the others. There is deliberately no event-sink port: events are returned
to the host in each step result (§2).

### 1.5 Universality checklist

Every spec in the series (and every PR touching the engine) must pass these:

- **U1.** Could a different game (space courier, text dungeon) use this
  without any engine change?
- **U2.** Does any engine type, id, string, or test name a theme, a genre
  skin, a screen, or a product?
- **U3.** Can the console host exercise it with no Spring, database, network,
  or API key?
- **U4.** Are time, randomness, and ids injected?
- **U5.** Is text sufficient to play it? (Assets must be optional decoration.)

## 2. Engine API principles

Principles only. Spec 23 fixes the types and signatures.

| # | Principle |
|---|---|
| A1 | **One in-process API.** Every host is an adapter over it: console (28), HTTP (29), tests. The HTTP API is a thin JSON mapping of the same types. |
| A2 | **Shape.** `Engine` loads packs and creates runs. `Run` is a handle. `run.apply(Command)` returns a `StepResult` holding the new `View`, the `Event`s raised, and any `Issue`s. `run.view()` re-reads the current view. `run.snapshot()` gives serializable state; `engine.resume(snapshot)` restores it. |
| A3 | **Small closed command set.** Start inputs are part of the run request. In a run: choose an option, submit text, advance, skip. Hosts map their gestures (tap, key press, voice) onto these. |
| A4 | **Presentation-neutral, text-first `View`.** Ordered blocks (kind, speaker, text, optional asset refs), options, the expected input mode (none, choice, text, choice-or-text, with limits), display-marked status values, and an `ended` flag. Serializable as is. A text host can ignore asset refs and lose nothing essential. |
| A5 | **Resolved text only.** Everything in a `View` is final text in the run's locale, taken from pack strings or generated content. Hosts do not translate or compose engine output. |
| A6 | **Deterministic.** Same pack version, start inputs, seed, and generator responses produce an identical transcript. |
| A7 | **Synchronous in v1.** `apply` blocks while a generator call runs (seconds). Hosts choose threading (a console simply waits). Streaming or async is a later, additive extension. |
| A8 | **No exceptions for content problems.** Bad generated content is repaired or replaced by authored fallback and reported as an `Issue`. Exceptions mean programmer error or infrastructure failure. |
| A9 | **Authored = generated format.** A hand-written scene and a generated scene use the same schema. A pack can be fully authored (no LLM), fully generated with authored fallback, or mixed. |
| A10 | **Reference adapters ship with the engine:** in-memory run store, fake/record/replay generator, seeded random, fixed clock, scripted input, console renderer. Any pack can be played and tested with no Spring, database, network, or API key. |
| A11 | **The console is a real host.** It serves as a development harness (play, validate, simulate, record transcripts) and can be the shell of a shippable console game: a pack plus `cli`. |

### 2.1 Illustrative `View` and console rendering

Illustrative only; spec 23 fixes the schema.

```json
{
  "blocks": [
    { "kind": "NARRATION", "text": "The ship drifts past a quiet moon." },
    { "kind": "SPEECH", "speaker": "Mira", "text": "Fuel is low. Land or push on?" }
  ],
  "options": [
    { "id": "land", "label": "Land on the moon" },
    { "id": "push-on", "label": "Push on to the station" }
  ],
  "input": { "mode": "CHOICE" },
  "status": [ { "label": "Fuel", "value": "3 / 10" } ],
  "ended": false
}
```

A console host renders it as:

```
The ship drifts past a quiet moon.
Mira: "Fuel is low. Land or push on?"

[Fuel 3 / 10]
  1) Land on the moon
  2) Push on to the station
> _
```

A web host renders the same `View` with backdrops and portraits. Neither
needed an engine change.

## 3. Vocabulary

One word, one meaning, across all specs. "Owner" is the spec that defines the
type; spec 22 fixes name and meaning.

### 3.1 Terms

| Term | Meaning | Owner |
|---|---|---|
| Engine | The generic, headless runtime | 22 |
| Host | Anything that drives the engine through the API (console, web server, test) | 22 |
| Adapter | An implementation of an engine port | 22 |
| Pack | Versioned static bundle that defines one game | 22 |
| Feature | Optional engine capability a pack declares it uses: `GENERATION`, `PLACES`, `ASSETS` | 22 |
| Start input | A field the host supplies when starting a run (declared by the pack) | 22 |
| Run | One play from start inputs to ending; pins pack id + version; holds state | 23 |
| Command | What a host sends into a run | 23 |
| View | What a run shows now: blocks, options, input mode, status | 23 |
| Block, Option | One piece of displayed content; one selectable action | 23 |
| Event | Typed notification returned in a step result when an `EMIT_EVENT` effect fires; declared in the manifest; the host decides what it means | 22, 23 |
| Scene | One interaction unit: blocks, options, optional text prompt, effects, next step. Authored or generated | 23 |
| Scene kind | Pack-defined template for scenes: slots, allowed effects, allowed next kinds | 25 |
| Stage | Pack-declared generation step with input/output schema and prompt; stages form a pack-declared graph | 25, 27 |
| Entity, Entity kind | A thing in the world (a character, a room, an item) and its pack-defined type with typed attributes; presets are named attribute bundles | 25 |
| State | Counters (bounded numbers), flags (booleans), sets (named collections of entities) | 24 |
| Effect | Typed state change from a closed set: `COUNTER_ADD`, `COUNTER_SET`, `FLAG_SET`, `SET_ADD`, `SET_REMOVE`, `GO_TO`, `EMIT_EVENT`, `END_RUN` | 24 |
| Condition | Typed predicate over state, from a closed set | 24 |
| Objective | Condition-based goal that becomes completed; the engine's neutral word for quest, mission, or task | 24 |
| Places (feature) | Optional graph of nodes and links, with layout metadata for renderers | 24 |
| Asset ref | `catalog:id` hint attached to blocks, places, or entities; engine passes it through, text hosts ignore it | 30 |
| Authored content | Scenes written by hand; also serves as fallback when generation is off, fails, or is invalid | 23 |

### 3.2 One vocabulary, three games

Illustrative; each pack decides its own details.

| Engine term | CapyBee game (spec 31) | Space-courier fixture | Text-dungeon fixture (console) |
|---|---|---|---|
| Start inputs | mood, interests, free-text wish | cargo, hazards, free-text wish | none |
| Stages | theme → scenario → … → scenes | scenario → scenes | none (fully authored) |
| Counters | Warmth, Courage, Curiosity | Fuel, Trust | Health |
| Sets | `party` (befriended heroes) | `crew` | `inventory` |
| Places | hex board (layout hint `hex`) | star map (`graph`) | rooms (`list`) |
| Entities | heroes with a trait and a need | crew, stations | monsters, items |
| Objectives | the real-life step at the end | deliver the cargo | find the key |
| Events | `echo.suggested` → host creates a Mission suggestion | `run.completed` | none |
| Assets | SVG backdrop and hero parts | decorative only | none |
| Host | CapyBee server + React | test harness | `cli` |

CapyBee words (hex, hive, heroes, Mission, honeycomb cell) appear only in the
right-hand mapping above, never in engine code.

## 4. Closed-world generation contract

Later specs must satisfy these rules. This spec states them; it does not
implement them.

| # | Rule |
|---|---|
| G1 | **AI never defines rules or the pipeline.** Counters, limits, conditions, effect types, session limits, and even which generation stages exist and in what order are pack data. Generation fills slots in pack-defined templates. |
| G2 | **Structured output only.** Every stage returns JSON validated against a schema derived from the pack. Invalid → repair once → authored fallback. Never an error screen. |
| G3 | **Closed vocabularies.** Ids for kinds, presets, scene kinds, effect and condition types, events, and asset refs come from the pack. An unknown id makes the output invalid. |
| G4 | **Bounded values.** Every number has pack limits (e.g. a counter change within ±N); every text slot has a max length. The engine clamps or rejects; it never trusts. |
| G5 | **The engine decides, AI proposes.** Deterministic engine code applies validated effects to run state. The generator cannot write state directly. |
| G6 | **AI draws nothing, and text suffices.** Visuals are asset refs only; no generated markup, SVG, or HTML. Everything needed to play is in text, so a console loses nothing essential. |
| G7 | **Free text is data, not instructions.** Player text is length-limited, scrubbed, marked untrusted in prompts, and influences content only through slots (spec 26). |
| G8 | **Pinned and versioned.** A run pins `packId` + `version`, and every generated artifact carries a schema version, so content can be re-validated or retired after a pack update (spec 24). |
| G9 | **Zero-LLM completion.** A pack ships authored content good enough to finish a run with no LLM call. A pack without the `GENERATION` feature is simply fully authored. |
| G10 | **Provider-agnostic.** The LLM is reached only through the `TextGenerator` port. Engine code names no vendor, model, or prompt text. Each stage declares a cost class (`LIGHT`, `STANDARD`), and adapters map classes to concrete models through configuration. |

## 5. Game Pack format

### 5.1 Layout

```
game-packs/<pack-id>/
├── pack.json                      manifest — this spec
├── strings/<locale>.json          player-visible fixed texts (labels, stat names) — this spec
├── prompts/<locale>/system.md     voice and tone rules; feature GENERATION — file here, used by 27
├── prompts/<locale>/*.md          stage prompt fragments — spec 27
├── scenes/<locale>/*.json         authored scenes, including fallback — spec 23
├── rules.json                     counters, flags, sets, limits, display rules — spec 24
├── places.json                    place graph; feature PLACES — spec 24
├── templates/*.json               scene kinds, entity kinds, stage graph — spec 25
└── assets/catalog.json            asset ids and tags; feature ASSETS — spec 30
```

Locations:

- Host packs: `app/server/src/main/resources/game-packs/<pack-id>/`
  (classpath `game-packs/<pack-id>/`).
- Test fixtures: `engine/src/test/resources/game-packs/<fixture-id>/`.

**Files index.** `pack.json` lists every other file in `files`. The loader
reads only listed files and never lists directories, because classpath
directory listing is unreliable inside a Spring Boot fat JAR.
`PackIndexLintTest` (§9) keeps the index honest against the files on disk.

### 5.2 `pack.json` manifest

| Field | Type | Required | Rules |
|---|---|---|---|
| `schemaVersion` | int | yes | Pack format version, owned by the engine. Must be `1`. |
| `id` | string | yes | Id format (§5.4). Must equal the id the host asks for (= folder name). |
| `version` | string | yes | `MAJOR.MINOR.PATCH`. |
| `name` | string | yes | 1–80 chars. Human-readable. |
| `description` | string | no | ≤ 300 chars. |
| `locales` | string[] | yes | ≥ 1 unique locale tags (§5.4). |
| `defaultLocale` | string | yes | Member of `locales`. |
| `features` | string[] | yes | May be empty. Unique, from `GENERATION`, `PLACES`, `ASSETS`. Declares which optional engine capabilities the pack uses, so validators can require the right files and hosts can check compatibility (a console host ignores `ASSETS`). |
| `startInputs` | object[] | yes | May be empty. See below. |
| `events` | object[] | no | Events the engine may raise. See below. |
| `contentPolicy` | object | yes | See below. |
| `files` | string[] | yes | Every other pack file as a relative path (§5.1). |

Unknown fields are a WARNING (`PACK_UNKNOWN_FIELD`), not an error: typos stay
visible and the format stays forward compatible.

**`startInputs[]`** — what the host supplies to start a run. Common fields:
`id` (id format, unique), `kind` (`ENUM` | `ENUM_SET` | `FREE_TEXT`),
`required` (bool), `labelKey` (string key that must exist in every declared
locale's strings file).

| Kind | Extra fields | Rules |
|---|---|---|
| `ENUM` | `values` | ≥ 2 unique ids |
| `ENUM_SET` | `values`, `maxItems` | ≥ 2 unique ids; `maxItems` between 1 and `values.length` |
| `FREE_TEXT` | `maxChars` | 1 to `MAX_FREE_TEXT_CHARS` (300); needs `contentPolicy.freeTextAllowed = true`; at most `MAX_FREE_TEXT_INPUTS` (1) per pack |

**`events[]`** — `id` (dotted event id, §5.4) and `payload`: up to 8
`{ "name": <id>, "type": "STRING" | "INT" | "BOOL" | "REF_ID" }` fields with
unique names. The engine can only raise events declared here (G3).

**`contentPolicy`** — `freeTextAllowed` (bool, required); `audience`
(optional `{ "minAge", "maxAge" }`, ints with 1 ≤ min ≤ max ≤ 99; metadata for
prompts, never per-user data); `forbiddenTags` (optional string[] of ids: tags
that generated content and referenced assets or entities must not carry;
enforced by later specs).

### 5.3 Example and fixtures

Test fixture `fixture-space-courier`, deliberately unlike CapyBee in vibe and
locale:

```json
{
  "schemaVersion": 1,
  "id": "fixture-space-courier",
  "version": "1.0.0",
  "name": "Fixture — Space Courier",
  "description": "Test pack with a different vibe than CapyBee.",
  "locales": ["en"],
  "defaultLocale": "en",
  "features": ["GENERATION", "ASSETS"],
  "startInputs": [
    { "id": "cargo", "kind": "ENUM", "required": true, "labelKey": "input.cargo",
      "values": ["medicine", "mail", "seeds"] },
    { "id": "hazards", "kind": "ENUM_SET", "required": false, "labelKey": "input.hazards",
      "values": ["asteroids", "pirates", "solar-storm"], "maxItems": 2 },
    { "id": "wish", "kind": "FREE_TEXT", "required": false, "labelKey": "input.wish",
      "maxChars": 140 }
  ],
  "events": [
    { "id": "run.completed",
      "payload": [ { "name": "summary", "type": "STRING" }, { "name": "stars", "type": "INT" } ] }
  ],
  "contentPolicy": {
    "freeTextAllowed": true,
    "audience": { "minAge": 10, "maxAge": 14 },
    "forbiddenTags": ["weapon"]
  },
  "files": ["strings/en.json", "prompts/en/system.md"]
}
```

`strings/en.json`:

```json
{
  "input.cargo": "What are you delivering?",
  "input.hazards": "What should we watch out for?",
  "input.wish": "Anything you'd like in the story?"
}
```

`prompts/en/system.md`: one short paragraph, e.g. "You write short, friendly
scenes for a space courier game. Keep sentences short."

The three fixtures differ in shape on purpose. Loading all three with the same
code is the universality check (§9), and from spec 28 each one must also play
to an ending in the console.

| Fixture | Locale | Features | Start inputs | Events | Shape |
|---|---|---|---|---|---|
| `fixture-minimal` | `pl` | none | one `ENUM` | none | Fully authored, text only |
| `fixture-text-dungeon` | `en` | `PLACES` | none (`strings/en.json` is `{}`) | none | Console-style game: rooms and items, no AI, no assets |
| `fixture-space-courier` | `en` | `GENERATION`, `ASSETS` | `ENUM`, `ENUM_SET`, `FREE_TEXT` | `run.completed` | Generated, with assets and free text |

### 5.4 Ids, versions, locales

- **Ids** (pack ids, start input ids, enum values, payload names, tags):
  `[a-z][a-z0-9]*(-[a-z0-9]+)*`, at most 48 chars.
- **Event ids:** dot-separated, `[a-z][a-z0-9-]*(\.[a-z][a-z0-9-]*)+`
  (e.g. `run.completed`), at most 64 chars.
- **String keys:** `[a-z][a-zA-Z0-9._-]*`, at most 80 chars.
- **Locale tags:** `^[a-z]{2,3}(-[A-Z]{2})?$` (`pl`, `en`, `pt-BR`).
- **Pack `version`:** PATCH = text or authored-content tweaks; MINOR =
  additive content; MAJOR = removes or renames ids or changes rules so
  in-flight runs may break (the host must expire or migrate them — spec 24).
- **`schemaVersion`** changes only when an older loader could not read the pack.

### 5.5 `strings/<locale>.json`

A flat JSON object: string key → non-blank text (at most 400 chars). `{}` is
valid. Holds fixed player-visible texts defined by the pack (input labels,
counter names, …), so the engine and hosts never hardcode them. Every
`labelKey` referenced by the manifest must exist in **every** declared locale.
Later specs add their own references through the `ReferenceIndex` (§6.3).
Unused-key warnings are out of scope until all reference sources exist.

### 5.6 Required files (this spec)

For every declared locale, `strings/<locale>.json` must be in `files`. If the
pack declares feature `GENERATION`, `prompts/<locale>/system.md` must also be
in `files` for every locale. Later specs extend `PackLayout.requiredFiles`
(spec 23 authored scenes, spec 24 rules and places, spec 25 templates, and so
on). A pack without `GENERATION` needs no prompts.

## 6. Loading and validation

All problems are collected and reported together; stop early only when
continuing is meaningless (steps 1–3).

### 6.1 Algorithm

1. Read `pack.json` through `PackSource`. Missing → `PACK_MANIFEST_MISSING`, stop.
2. Parse JSON. Syntax error → `PACK_JSON_INVALID` (with line and column), stop.
3. Check `schemaVersion`. Unsupported → `PACK_SCHEMA_UNSUPPORTED`, stop.
4. Parse manifest fields. Each defect → `PACK_FIELD_INVALID` with a JSON
   pointer (e.g. `/startInputs/1/maxItems`); unknown fields →
   `PACK_UNKNOWN_FIELD`. Parsing never lets a model constructor throw: check
   format first (`PackId.isValid(..)` etc.), then build the record.
5. Manifest `id` must equal `expectedId`, else `PACK_ID_MISMATCH`.
6. `features`: every value known and unique, else `PACK_FIELD_INVALID`
   (pointer `/features/<i>`).
7. Validate the `files` index: path rules, duplicates, required files (§5.6).
   Then read each file: missing → `PACK_FILE_MISSING`; size limits →
   `PACK_TOO_LARGE`; empty → `PACK_FILE_EMPTY`; leading BOM → strip and
   `PACK_BOM_STRIPPED`; every `.json` file must parse → `PACK_JSON_INVALID`.
8. Locale rules → `PACK_DEFAULT_LOCALE_INVALID`.
9. Strings files → `PACK_STRINGS_INVALID`. Define every key as kind
   `stringKey:<locale>` in the reference index.
10. Start-input rules → `PACK_FREE_TEXT_NOT_ALLOWED`,
    `PACK_TOO_MANY_FREE_TEXT_INPUTS`. Reference each `labelKey` once per
    locale (`stringKey:<locale>`). Define each start input id (kind
    `startInput`) and each event id (kind `event`).
11. Only if there are no ERRORs so far: run the registered
    `SectionValidator`s in registration order.
12. `ReferenceIndex.check()` → `PACK_DUPLICATE_ID`, `PACK_DANGLING_REF`
    (a missing string key in one locale is a dangling `stringKey:<locale>`).
13. The result holds a `Pack` only if there are no ERRORs. Warnings never
    block loading.

### 6.2 Issue codes

| Code | Severity | When |
|---|---|---|
| `PACK_MANIFEST_MISSING` | ERROR | `pack.json` cannot be read |
| `PACK_JSON_INVALID` | ERROR | A `.json` file fails to parse; message includes line and column |
| `PACK_SCHEMA_UNSUPPORTED` | ERROR | `schemaVersion` not supported by this engine |
| `PACK_FIELD_INVALID` | ERROR | Manifest field missing, wrong type, or breaking a rule in §5 (pointer names the field; includes unknown or duplicate `features`) |
| `PACK_UNKNOWN_FIELD` | WARNING | Unrecognised manifest field |
| `PACK_ID_MISMATCH` | ERROR | Manifest `id` differs from the requested id |
| `PACK_FILE_PATH_INVALID` | ERROR | Path absolute, contains `..` or `\`, longer than 120 chars, or extension not `.json`/`.md` |
| `PACK_FILE_DUPLICATE` | ERROR | Same path listed twice |
| `PACK_FILE_MISSING` | ERROR | Listed file cannot be read |
| `PACK_REQUIRED_FILE_MISSING` | ERROR | A required file (§5.6) is not in the index |
| `PACK_FILE_EMPTY` | ERROR | `.md` file blank or `.json` file empty |
| `PACK_TOO_LARGE` | ERROR | A file over 256 KB, or the pack over 2 MB |
| `PACK_BOM_STRIPPED` | WARNING | File started with a UTF-8 BOM (stripped) |
| `PACK_DEFAULT_LOCALE_INVALID` | ERROR | `defaultLocale` not in `locales` |
| `PACK_STRINGS_INVALID` | ERROR | Strings file is not a flat string map, has a bad key, or a blank value |
| `PACK_FREE_TEXT_NOT_ALLOWED` | ERROR | A `FREE_TEXT` input while `freeTextAllowed` is false |
| `PACK_TOO_MANY_FREE_TEXT_INPUTS` | ERROR | More than one `FREE_TEXT` input |
| `PACK_DUPLICATE_ID` | ERROR | Same id defined twice within one kind |
| `PACK_DANGLING_REF` | ERROR | Reference without a definition (includes missing string keys per locale) |

### 6.3 Extension point

Later specs add `SectionValidator`s (rules and places in 24, templates in 25,
assets in 30). A validator receives the loaded `Pack` (raw file text) and a
`PackValidationContext`. It must be pure (no I/O), report problems through the
context, and register ids through the `ReferenceIndex` instead of checking
cross-file links itself. It parses its own JSON inside its feature's `json`
subpackage. Validators run only after the built-in checks pass, so they may
assume a well-formed manifest.

## 7. Code skeleton

Signatures are the contract; bodies are up to the implementation. All records
copy collections defensively (`List.copyOf`, `Set.copyOf`, `Map.copyOf`).

```java
// com.capybee.engine.pack — no Jackson imports in this package

public record PackId(String value) {
    public static boolean isValid(String text) { /* §5.4 */ }
    public PackId { if (!isValid(value)) throw new IllegalArgumentException("Invalid pack id: " + value); }
}

public record PackVersion(int major, int minor, int patch) implements Comparable<PackVersion> {
    public static Optional<PackVersion> tryParse(String text) { /* "1.2.3" only */ }
}

public record LocaleTag(String value) {
    public static boolean isValid(String text) { /* §5.4 */ }
}

public enum PackFeature { GENERATION, PLACES, ASSETS }

public sealed interface StartInputDef {
    String id();
    boolean required();
    String labelKey();

    record EnumInput(String id, boolean required, String labelKey, List<String> values) implements StartInputDef {}
    record EnumSetInput(String id, boolean required, String labelKey, List<String> values, int maxItems) implements StartInputDef {}
    record FreeTextInput(String id, boolean required, String labelKey, int maxChars) implements StartInputDef {}
}

public record EventDef(String id, List<PayloadField> payload) {
    public record PayloadField(String name, PayloadType type) {}
    public enum PayloadType { STRING, INT, BOOL, REF_ID }
}

public record ContentPolicy(boolean freeTextAllowed, Audience audience, List<String> forbiddenTags) {
    public record Audience(int minAge, int maxAge) {}   // the audience component is nullable
}

public record PackManifest(
        int schemaVersion, PackId id, PackVersion version, String name, String description,
        List<LocaleTag> locales, LocaleTag defaultLocale, Set<PackFeature> features,
        List<StartInputDef> startInputs, List<EventDef> events,
        ContentPolicy contentPolicy, List<String> files) {}

/** Immutable. files: relative path -> UTF-8 text, BOM stripped. */
public record Pack(PackManifest manifest, Map<String, String> files) {
    public Optional<String> file(String relativePath) { ... }
    public String requireFile(String relativePath) { ... }   // IllegalStateException if absent
}

public interface PackSource {
    Optional<String> read(String relativePath);   // UTF-8 text; empty if absent
    String describe();                            // e.g. "classpath:game-packs/fixture-space-courier"
}
public final class ClasspathPackSource implements PackSource {
    public ClasspathPackSource(ClassLoader loader, String basePath) { ... }
}
public final class DirectoryPackSource implements PackSource {       // tooling and tests
    public DirectoryPackSource(Path root) { ... }                    // rejects paths that escape root
}
public final class InMemoryPackSource implements PackSource {        // tests and tooling
    public InMemoryPackSource(Map<String, String> files) { ... }
    public InMemoryPackSource with(String path, String content) { ... }
    public InMemoryPackSource without(String path) { ... }
}

public record PackIssue(Severity severity, String code, String file, String pointer, String message) {
    public enum Severity { ERROR, WARNING }
}
public final class PackValidationException extends RuntimeException {
    public List<PackIssue> issues() { ... }
}
public record LoadResult(Optional<Pack> pack, List<PackIssue> issues) {
    public boolean ok() { ... }                  // pack present and no ERROR issues
}

public final class ReferenceIndex {
    public void define(String kind, String id, String file, String pointer) { ... }
    public void reference(String kind, String id, String file, String pointer) { ... }
    public List<PackIssue> check() { ... }       // PACK_DUPLICATE_ID + PACK_DANGLING_REF
}
public final class PackValidationContext {
    public void error(String code, String file, String pointer, String message) { ... }
    public void warn(String code, String file, String pointer, String message) { ... }
    public ReferenceIndex references() { ... }
}
public interface SectionValidator {
    void validate(Pack pack, PackValidationContext context);   // pure: no I/O
}

public final class PackLayout {
    public static final Set<Integer> SUPPORTED_SCHEMA_VERSIONS = Set.of(1);
    public static final int MAX_FILE_BYTES = 256 * 1024;
    public static final int MAX_PACK_BYTES = 2 * 1024 * 1024;
    public static final int MAX_FREE_TEXT_CHARS = 300;
    public static final int MAX_FREE_TEXT_INPUTS = 1;
    public static List<String> requiredFiles(PackManifest manifest) { /* §5.6 */ }
}

public final class PackRegistry {
    public PackRegistry(Collection<Pack> packs) { ... }   // duplicate ids → IllegalArgumentException
    public Optional<Pack> find(PackId id) { ... }
    public Pack require(PackId id) { ... }
    public Collection<Pack> all() { ... }
}
```

```java
// com.capybee.engine.pack.json — the only package in this spec that imports Jackson

public final class PackLoader {
    public PackLoader(List<SectionValidator> sectionValidators) { ... }
    public LoadResult tryLoad(PackId expectedId, PackSource source) { ... }   // never throws for content problems
    public Pack load(PackId expectedId, PackSource source) { ... }            // throws PackValidationException on any ERROR
}
// package-private helpers: ManifestParser, StringsParser, JsonDocuments (parse with line/column errors)
```

Jackson notes for the implementer:

- Use Jackson 3 (`tools.jackson.*`). Build one immutable `JsonMapper` with
  `JsonMapper.builder()`; the engine creates its own, not a Spring bean.
- Jackson 3 exceptions are unchecked (`tools.jackson.core.JacksonException`);
  convert them into `PackIssue`s with line and column.
- Do not import `com.fasterxml.jackson.databind.*` — that is the Jackson 2
  line. Several tree-API method names changed in Jackson 3, so check them
  against the Jackson 3 migration notes instead of copying Jackson 2 habits.
- The standalone module declares `tools.jackson.core:jackson-databind`.
  Confirm with `mvn -f engine/pom.xml dependency:tree`; the tree must contain
  Jackson 3 and no Spring, JPA, or application dependency. Jackson annotations
  retain the `com.fasterxml.jackson.annotation` namespace as noted above.

## 8. Quality gates

### 8.1 `EngineBoundaryTest`

JUnit 5, no Spring context. Reads every `.java` file under
`src/main/java/com/capybee/engine` (surefire's working directory is the SDK
directory `engine`). Each file's zone is its first folder under the engine
root (`pack`, `adapter`, `cli`, …); a `json` folder anywhere in the path marks
a JSON zone. A violation fails the test with the offending file and line.

| Rule | Applies to | Forbidden |
|---|---|---|
| B1 | All engine files | `com.capybee.server`, `org.springframework`, `jakarta.persistence`, `org.hibernate` |
| B2 | Files outside a `json` folder and outside `adapter` | `tools.jackson`, `com.fasterxml.jackson` |
| B3 | All files, except `package` and `import com.capybee.engine.` lines | Theme vocabulary: substrings `capybee` and `honeycomb` (case-insensitive), plus `hive` and `capy` as a word or camelCase word start (regex below). The list lives in one constant and grows whenever a new game's vocabulary appears. |
| B4 | Files outside `cli` | Console I/O: `System.out`, `System.err`, `System.in`, `System.console`, `new Scanner(System` |
| B5 | Files outside `adapter` and `cli` | Nondeterminism: `Instant.now(`, `LocalDate.now(`, `LocalDateTime.now(`, `ZonedDateTime.now(`, `OffsetDateTime.now(`, `System.currentTimeMillis`, `new Random(`, `ThreadLocalRandom`, `Math.random`, `UUID.randomUUID` |
| B6 | Files outside `adapter` | Vendor and model names, case-insensitive: `anthropic`, `claude`, `openai`, `haiku`, `sonnet` |

Suggested B3 regex for the word-level terms (`archive` passes; `HiveCell` and
`getHiveCell` fail):

```
\b(?i:hive|capy)(?![a-z])|(?<=[a-z0-9_])(?:Hive|Capy)(?![a-z])
```

The scan covers every engine `.java` file, `package-info.java` included, so
Javadoc and comments must describe the boundary in words without the literal
forbidden strings. The test fails if the engine directory is missing or
contains no files, so it can never pass vacuously. B4–B6 have nothing to catch
until `cli` and `adapter` exist, but they ship now so the first violation fails
the build, not a review.

### 8.2 JaCoCo gate

Configure the SDK's own `check` execution in `engine/pom.xml`. Do not touch
the existing service rule in `app/server/pom.xml`. The SDK gate uses:

```xml
<rule>
  <element>PACKAGE</element>
  <limits>
    <limit>
      <counter>LINE</counter>
      <value>COVEREDRATIO</value>
      <minimum>0.70</minimum>
    </limit>
  </limits>
  <includes>
    <include>com.capybee.engine.*</include>
  </includes>
</rule>
```

### 8.3 Scoped Copilot instructions

Create `.github/instructions/engine.instructions.md`:

```markdown
---
description: "Use when implementing the standalone game engine SDK, pack validation, engine tests, or game pack content."
applyTo: "engine/**/*.java,engine/pom.xml,engine/src/**/resources/game-packs/**,app/server/src/main/resources/game-packs/**"
---

# Engine standards

- Standalone SDK in `engine/`: no Spring parent or app dependency. Verify it
  with `mvn -f engine/pom.xml verify`; root Maven aggregates Java modules.
- The engine is universal and headless. Pure Java only: no Spring, JPA,
  Hibernate, and nothing from `com.capybee.server`. Jackson 3 only in `json`
  folders and under `adapter`. `EngineBoundaryTest` enforces this.
- No theme, setting, genre skin, or product name in engine code, identifiers,
  or tests. Everything game-specific is pack data. If you are about to
  hardcode a counter, hero, mood, or player-facing text in engine code, move
  it to the pack.
- The engine never prints or reads input. It returns a presentation-neutral
  `View`. Console I/O lives only in `cli`.
- Time, randomness, and ids come through ports (`Clock`, `RandomSource`,
  `IdSource`). No vendor or model names outside `adapter`.
- AI output is untrusted input: validate against pack-derived schemas, closed
  vocabularies, and bounded values (spec 22 §4, rules G1–G10).
- Ask the universality checklist (spec 22 §1.5, U1–U5) before adding anything.
- Domain types are immutable records. Validators collect all problems; they do
  not fail on the first.
- Host-specific code (CapyBee) lives under `com.capybee.server` and follows
  the normal layering (web → service → repository → domain).
- Engine tests mirror the package structure under
  `engine/src/test/java/com/capybee/engine/`. JaCoCo gate: 70% on
  `com.capybee.engine.*`.
- Changing a pack: bump `version` and keep the `files` index in sync
  (`PackIndexLintTest`).
```

## 9. Testing (mandatory per `copilot-instructions.md`)

Engine tests are plain JUnit 5 with no collaborators to mock, so the Mockito
service-test pattern does not apply here. Tests mirror the package structure
under `engine/src/test/java/com/capybee/engine/`.

- **`PackIdTest`, `PackVersionTest`, `LocaleTagTest`** — valid and invalid
  formats, length limits, `tryParse` edge cases (`1.2`, `01.2.3`, `1.2.3-rc1`).
- **`PackLoaderTest`** (uses `InMemoryPackSource` built from the
  `fixture-space-courier` files):
  - valid pack loads; all manifest fields map correctly, including `features`,
    all three start input kinds, the event, and the content policy;
  - a pack without `GENERATION` loads without any prompt file; a pack with
    `GENERATION` and no `prompts/<locale>/system.md` → `PACK_REQUIRED_FILE_MISSING`;
  - one test per code in §6.2, each produced by mutating the valid source with
    `with`/`without`;
  - two defects at once → both codes are reported;
  - `PACK_UNKNOWN_FIELD` and `PACK_BOM_STRIPPED` are warnings and the pack
    still loads;
  - `tryLoad` never throws on content problems; `load` throws
    `PackValidationException` carrying every issue;
  - a `SectionValidator` runs after built-in checks pass, does not run when
    built-in checks fail, and can define or reference ids so that
    `PACK_DUPLICATE_ID` and `PACK_DANGLING_REF` are reported;
  - a `labelKey` missing from only one of two locales → `PACK_DANGLING_REF`
    naming that locale.
- **`ReferenceIndexTest`** — define/reference/duplicate/dangling combinations.
- **`PackSourceTest`** — `ClasspathPackSource` reads a fixture and returns empty
  for an absent file; `DirectoryPackSource` rejects `../` escapes;
  `InMemoryPackSource.with/without` do not mutate the original.
- **`PackFixturesLoadTest`** — loads `fixture-minimal` (`pl`, no features),
  `fixture-text-dungeon` (`en`, `PLACES`), and `fixture-space-courier` (`en`,
  `GENERATION` + `ASSETS`) from the classpath with the same loader. This is the
  **universality check** at pack level: three unrelated games, shapes, and
  locales, zero engine changes.
- **`PackIndexLintTest`** — for each folder under
  `src/main/resources/game-packs` and `src/test/resources/game-packs` (skip a
  missing root), the manifest `files` list must equal the files on disk except
  `pack.json`.
- **`PackRegistryTest`** — find/require/all, duplicate ids rejected.
- **`EngineBoundaryTest`** — §8.1, plus one self-test per rule B1–B6 proving it
  detects a violation (run the scanner on a temp directory containing a bad
  file for each rule) and one proving an allowed zone passes (e.g.
  `System.out` inside a `cli` folder).

## 10. Open questions for Pavlo

1. **Names.** `com.capybee.engine` and the folder `game-packs` are working
   names. Should the engine get its own neutral name (for docs, the console
   tool, and a future extraction), and should its root package leave the
   CapyBee namespace now or at extraction time? Renaming later is an IDE
   refactor plus the pack folder name.
2. **Package vs module (§1.3), resolved.** The engine is a standalone SDK in
  `engine/`, with its own POM, dependencies, tests, and coverage gate. The
  root POM aggregates it and the backend; neither inherits from the root.
3. **Console-first order, resolved.** The minimal authored console loop is
  spec 23; the full console host is spec 28. Generated runs are proven there
  before web integration (29). No console runtime is part of spec 22.
4. **`features` in the manifest** (`GENERATION`, `PLACES`, `ASSETS`) instead of
   guessing capabilities from the files present. Explicit declaration lets
   validators and hosts fail early. Confirm.
5. **One free-text start input per pack.** Matches "a free-text wish" and keeps
   the prompt-injection and PII surface small. Raise `MAX_FREE_TEXT_INPUTS` if
   you expect more.
6. **JSON + Markdown packs.** JSON mirrors the LLM output schemas and needs no
   extra dependency; long prompts live in `.md` to avoid JSON escaping. Prefer
   YAML for hand-authoring? It would add a dependency.
7. **Pack id and locale.** The CapyBee pack is assumed to be one pack with
   `locales: ["pl"]`, not a pack per language. Confirm.

## 11. Rollout checklist

- [x] Record the standalone-module and console-first decisions. Retain the
  specified working namespace and format defaults; neutral naming and
  first-game locale confirmation remain future product questions (§10).
- [x] Confirm the superseded first-draft files are absent:
  `22-adventure-engine-core-and-pack-format-instruction.md` and
  `intent/01-ai-adventure-game-engine.md`; they are superseded by this spec
  and `intent/01-universal-game-engine.md`.
- [x] Add package `com.capybee.engine` with a `package-info.java` stating the
  boundary rules from §1.2 in words (no forbidden literal strings).
- [x] Implement the model and plumbing types in `com.capybee.engine.pack` (§7).
- [x] Implement `PackLoader` and parsers in `com.capybee.engine.pack.json` on
  Jackson 3; confirm with `mvn -f engine/pom.xml dependency:tree`.
- [x] Add fixtures `fixture-minimal`, `fixture-text-dungeon`, and
  `fixture-space-courier` under `engine/src/test/resources/game-packs/`.
- [x] Add all tests from §9, including aggregation of manifest-field defects
  with independent file, policy, and reference defects.
- [x] Add the SDK JaCoCo rule for `com.capybee.engine.*` (§8.2); run
  `mvn -f engine/pom.xml clean verify`.
- [x] Add the root Maven aggregator and an independent SDK CI job requiring
  no application services; leave the backend dependency and deployment unchanged.
- [x] Add `.github/instructions/engine.instructions.md` (§8.3).
- [x] Update `docs/specifications/README.md`: add the missing specs 20 and 21,
  add 22, and list specs 23–31 under "Planned Documents".
- [x] Update the `docs/specifications/intent/README.md` index with the row
  `01-universal-game-engine.md | → spec 22 (series 22–31) | 22`.
- [x] Confirm nothing user-facing changed: no endpoint, migration, backend
  Maven dependency, or UI edit. SDK dependencies are declared only in `engine/pom.xml`.

Verification (2026-10-09): the clean standalone build reports 157 tests,
zero failures or errors, and all coverage checks met. One symlink-escape test
is skipped on the local Windows account because symlink creation is unavailable;
the test runs where the filesystem/account supports it. Jackson databind/core
are 3.1.2; no Spring, persistence, or application dependency is in the SDK tree.
Root `mvn verify` also verifies the SDK, but the backend context-load test fails
locally because PostgreSQL at `localhost:5432` is unavailable. Its other 74
tests pass. Full reactor verification still requires the backend environment.

## Notes

- This spec is a revision of the first draft. The first draft encoded CapyBee's
  game design in the engine (a board with hex cells, a recruit effect, a
  theme-then-scenario pipeline, `adventure-packs`), had no host-facing API, and
  assumed every pack uses an LLM. This revision moves all of that into the
  CapyBee pack (spec 31), adds the API principles (§2) and the universality
  checklist (§1.5), makes generation optional (`features`), and reorders the
  series console-first.
- This spec fixes the vocabulary but deliberately does not define types for the
  API, rules, or templates; those stay with specs 23–25.
- Spec 23 (Engine API) is next. It defines the run API and reference adapters,
  and ends with a hand-written pack playable in a terminal.
- If anything here conflicts with the repo at implementation time, the repo
  wins; flag the difference in the PR instead of silently following either.
