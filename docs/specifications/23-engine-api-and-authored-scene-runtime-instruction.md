# 23 — Engine API and Authored Scene Runtime

## Purpose

Second spec of the engine series (see
`docs/specifications/22-engine-core-and-pack-format-instruction.md` and the
intent note `docs/specifications/intent/01-universal-game-engine.md`).

Spec 22 gave the engine a pack format and a loader. This spec makes the engine
**playable**:

1. The **public API** every host uses: `Engine`, `Run`, `Command`, `View`,
   `StepResult`, `Event`, `Issue`, snapshots (§2).
2. The **authored scene format v1** and a runtime that plays it, so a
   hand-written pack can be played start to finish with no AI (§3, §4).
3. **Reference adapters** for ids and randomness (§5).
4. The new **`engine/cli` module**: a console host with a first playable loop,
   scripted input, and save/load (§6).

After this spec, a hand-written pack plays in a terminal:

```
java -jar engine/cli/target/engine-cli-0.1.0-SNAPSHOT.jar \
     --pack-dir engine/fixtures/game-packs/fixture-minimal --locale pl
```

Out of scope: counters, flags, effects, conditions, events raised by content
(24); scene kinds and generation stages (25); the free-text policy (26;
`SubmitText` is reserved); LLM generation (27); console extras such as
`validate`/`simulate`/`record` (28); HTTP and persistence (29); rendering
assets (30).

## Ground truth reviewed

- Spec 22 (this series): `PackLoader`, `PackSource` implementations,
  `SectionValidator`, `ReferenceIndex`, `PackRegistry`, `PackFeature`,
  `LocaleTag`, `PackId`, `PackVersion`, the three fixture packs, and the
  standalone `engine/` project with module `core`. This spec assumes spec 22 is
  implemented. At the time of writing the repository has no `engine/` folder,
  so implement 22 first.
- Spec 22 §1.2 rules apply unchanged: no framework dependency in core, Jackson
  only in `json` folders and under `adapter`, no console I/O in core, no
  wall-clock/random/UUID use outside `adapter`, no vendor or theme words.
- Spring Boot 4 ships Jackson 3 (`tools.jackson.*`); the engine uses Jackson 3
  only (see spec 22 §7 notes).
- `.github/workflows/build-test-deploy.yml` and `app/server` are not touched by
  this spec; the engine's own CI (`engine-ci.yml`, spec 22 §8.4) builds both
  modules.

## Constraints

- All spec 22 constraints hold: universal, headless, deterministic,
  provider-agnostic, closed-world.
- **The API package is the contract.** Only types in
  `com.capybee.engine.api` (plus the content and port types it exposes) are
  public API. Everything else in core is internal; document this in each
  package's `package-info.java`.
- **No state beyond scene navigation.** Counters, flags, sets, effects, and
  conditions arrive in spec 24. Scenes have no effects yet.
- **Authored content only.** A pack that declares `GENERATION` loads but cannot
  start a run until spec 27 (§2.2).
- **Player content is personal data.** Snapshots can contain start-input text.
  The engine logs ids and issue codes only, never player text, views, or
  commands.
- **The console is the only code that touches `System.*`**, and within
  `engine/cli` only `PlayMain` does.
- No change outside `engine/`, `.github/`, and `docs/`.

## 1. Project changes

Add module `cli` to `engine/pom.xml`:

```xml
<modules>
  <module>core</module>
  <module>cli</module>
</modules>
```

`engine/cli/pom.xml`:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <parent>
    <groupId>com.capybee</groupId>
    <artifactId>engine-parent</artifactId>
    <version>0.1.0-SNAPSHOT</version>
  </parent>
  <artifactId>engine-cli</artifactId>
  <name>engine-cli</name>

  <dependencies>
    <dependency>
      <groupId>com.capybee</groupId>
      <artifactId>engine-core</artifactId>
      <version>${project.version}</version>
    </dependency>
    <dependency>
      <groupId>org.slf4j</groupId>
      <artifactId>slf4j-nop</artifactId>
      <scope>runtime</scope>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <scope>test</scope>
    </dependency>
    <dependency>
      <groupId>org.assertj</groupId>
      <artifactId>assertj-core</artifactId>
      <scope>test</scope>
    </dependency>
  </dependencies>

  <build>
    <testResources>
      <testResource>
        <directory>src/test/resources</directory>
      </testResource>
      <testResource>
        <directory>${project.basedir}/../fixtures</directory>
      </testResource>
    </testResources>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-shade-plugin</artifactId>
        <executions>
          <execution>
            <phase>package</phase>
            <goals><goal>shade</goal></goals>
            <configuration>
              <transformers>
                <transformer implementation="org.apache.maven.plugins.shade.resource.ManifestResourceTransformer">
                  <mainClass>com.capybee.engine.cli.PlayMain</mainClass>
                  <manifestEntries>
                    <Multi-Release>true</Multi-Release>
                  </manifestEntries>
                </transformer>
              </transformers>
            </configuration>
          </execution>
        </executions>
      </plugin>
      <plugin>
        <groupId>org.jacoco</groupId>
        <artifactId>jacoco-maven-plugin</artifactId>
        <version>0.8.15</version>
        <executions>
          <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
          </execution>
          <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
          </execution>
          <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
              <rules>
                <rule>
                  <element>BUNDLE</element>
                  <limits>
                    <limit>
                      <counter>LINE</counter>
                      <value>COVEREDRATIO</value>
                      <minimum>0.70</minimum>
                    </limit>
                  </limits>
                </rule>
              </rules>
            </configuration>
          </execution>
        </executions>
      </plugin>
    </plugins>
  </build>
</project>
```

Notes: verify that `maven-shade-plugin` and `slf4j-nop` versions are managed by
the Spring Boot parent and BOM; if not, pin current releases. `slf4j-nop` keeps
the console free of "no SLF4J provider" warnings. The shaded jar is
`engine/cli/target/engine-cli-0.1.0-SNAPSHOT.jar`. If shading causes trouble,
the IDE run configuration for `PlayMain` is an acceptable fallback.

New packages in `engine/core` (`com.capybee.engine`): `api` (public API),
`api.json` (snapshot codec), `content` (scene model), `content.json` (scene
parser and validator), `port` (`IdSource`), `adapter` (reference adapters). In
`engine/cli`: `com.capybee.engine.cli`.

## 2. Public API

Package `com.capybee.engine.api`. All types are immutable records or
interfaces. Signatures are in §8.

### 2.1 Engine

| Member | Meaning |
|---|---|
| `Engine.builder(PackRegistry)` | Creates an `EngineBuilder`. The registry holds already loaded, validated packs. |
| `EngineBuilder.clock(Clock)` | `java.time.Clock`, required. |
| `EngineBuilder.random(RandomGenerator)` | `java.util.random.RandomGenerator`, required. Used only to draw a seed when a request has none. |
| `EngineBuilder.ids(IdSource)` | Run id generation, required. |
| `EngineBuilder.build()` | Throws `IllegalStateException` if a port is missing. |
| `Engine.sectionValidators()` | The `SectionValidator`s for every feature implemented so far (this spec: the scene validator). Hosts build their loader with `new PackLoader(Engine.sectionValidators())`. Later specs add to it. |
| `engine.start(RunRequest)` | Validates the request and returns a `StartResult`. |
| `engine.resume(RunSnapshot)` | Restores a run and returns a `ResumeResult`. |

No port has a default. Nothing in core reads the clock, randomness, or ids on
its own (spec 22 §1.2, rule 5).

### 2.2 Starting a run

`RunRequest(PackId packId, LocaleTag locale, Map<String,String> startInputs,
Long seed)`. `locale` and `seed` may be null; `startInputs` may be empty but not
null.

Validation, in this order. Every defect is an `Issue`, never an exception
(A8). A missing pack stops validation (there is nothing to check against);
otherwise every check runs and all issues are reported together, so a host can
show them at once. `StartResult.run()` is null exactly when any ERROR issue
exists.

| Check | Issue code |
|---|---|
| `packId` is in the registry | `PACK_NOT_FOUND` |
| `locale` (default: pack `defaultLocale`) is one of the pack's locales | `LOCALE_UNSUPPORTED` |
| The pack's features are supported by this engine version. `PLACES` has runtime semantics in spec 24.1; `ASSETS` remains metadata-only. `GENERATION` is **not** supported until spec 27. | `FEATURE_NOT_AVAILABLE` |
| Every start input id is declared by the pack | `START_INPUT_UNKNOWN` |
| Every `required` start input is present and non-blank | `START_INPUT_MISSING` |
| Values fit their kind (below) | `START_INPUT_INVALID` |

Value rules, applied after trimming surrounding whitespace:

- `ENUM`: the value is one of `values`.
- `ENUM_SET`: comma-separated ids, each one of `values`, no duplicates, at most
  `maxItems`.
- `FREE_TEXT`: at most `maxChars` characters. Handling beyond length is spec 26.
  Blank counts as absent.

A blank value for a non-required input is dropped from the stored inputs.

On success: the run id comes from `IdSource`, the seed is `request.seed()` or
`random.nextLong()`, `createdAt` and `updatedAt` come from the clock, and the
state is the start scene (§4).

### 2.3 Running

`Run`: `id()`, `view()`, `apply(Command)`, `snapshot()`.

`Command` is a sealed interface:

| Command | Allowed when | Effect |
|---|---|---|
| `Choose(optionId)` | The current scene has options | Moves to the option's `next` scene |
| `Advance` | The current scene has `next` and no options | Moves to `next` |
| `SubmitText(text)` | Reserved for spec 26 | Always rejected with `INPUT_NOT_EXPECTED` for now |
| `Abandon` | Always, until the run has ended | Ends the run with `ABANDONED` |

`StepResult(boolean accepted, View view, List<Event> events, List<Issue>
issues)`:

- **Accepted:** `accepted` is true, `view` is the new view, the step counter
  and `updatedAt` advance.
- **Rejected:** `accepted` is false, `view` is the unchanged current view, one
  ERROR issue explains why, and **no state changes** (the step counter does not
  advance). Hosts can retry freely.
- `events` is always empty in this spec. The type exists so hosts are written
  against the final shape; spec 24 starts raising events.

### 2.4 View

What the run shows *now*, presentation-neutral and text-first (spec 22 A4, A5).

| Field | Meaning |
|---|---|
| `blocks` | Ordered `Block(kind, speaker, text, refs)`. In this spec: the current scene's blocks, unchanged. Empty after `Abandon`. |
| `options` | `Option(id, label, enabled, hint)`. Empty unless input mode is `CHOICE`. `enabled` is always true until spec 24; `hint` is null. |
| `input` | `InputSpec(mode, maxChars)`. Mode `CHOICE` when the scene has options; `NONE` otherwise. A `NONE` view that has not ended means "continue": the host sends `Advance`. `maxChars` is 0 until spec 26. |
| `status` | `StatusItem(id, label, value)` list. Empty until spec 24. |
| `ended` | True once the run is over. |
| `endReason` | `COMPLETED` or `ABANDONED` when `ended`; null otherwise. |

`Block.kind` is `NARRATION`, `SPEECH`, or `SYSTEM`. `speaker` is set only for
`SPEECH`. `refs` are optional asset refs (`catalog:id`), passed through
untouched; text hosts ignore them.

The view never carries timestamps, ids, or anything else that changes between
otherwise identical runs.

### 2.5 Issues

`Issue(Severity severity, String code, String message)` with severity `ERROR` or
`WARNING`. Messages are developer-oriented English for logs and the console;
hosts show their own text to players and key off `code`.

| Code | Severity | When |
|---|---|---|
| `PACK_NOT_FOUND` | ERROR | Requested pack id is not in the registry |
| `LOCALE_UNSUPPORTED` | ERROR | Locale not declared by the pack |
| `FEATURE_NOT_AVAILABLE` | ERROR | Pack uses a feature this engine version cannot run |
| `START_INPUT_UNKNOWN` | ERROR | Start input id not declared by the pack |
| `START_INPUT_MISSING` | ERROR | Required start input absent or blank |
| `START_INPUT_INVALID` | ERROR | Value breaks its kind's rules |
| `COMMAND_NOT_ALLOWED` | ERROR | Command does not fit the current scene (e.g. `Advance` on a choice scene) |
| `OPTION_UNKNOWN` | ERROR | `Choose` with an id the scene does not have |
| `OPTION_DISABLED` | ERROR | `Choose` with a disabled option (unreachable until spec 24) |
| `INPUT_NOT_EXPECTED` | ERROR | `SubmitText` while no text input is offered |
| `RUN_ENDED` | ERROR | Any command after the run has ended |
| `SNAPSHOT_INVALID` | ERROR | Snapshot JSON is malformed or refers to content that does not exist |
| `SNAPSHOT_VERSION_UNSUPPORTED` | ERROR | `snapshotVersion` not supported |
| `SNAPSHOT_PACK_MISMATCH` | ERROR | Snapshot's pack id or version differs from the loaded pack |

### 2.6 Snapshots

`run.snapshot()` returns a pure-data `RunSnapshot`; `engine.resume(snapshot)`
restores it. Codec: `api.json.SnapshotCodec` (Jackson 3, tree model; instants
written and read as ISO-8601 strings).

```json
{
  "snapshotVersion": 1,
  "runId": "run-1",
  "pack": { "id": "fixture-minimal", "version": "1.0.0" },
  "locale": "pl",
  "seed": 42,
  "startInputs": { "tone": "calm" },
  "createdAt": "2026-10-08T10:00:00Z",
  "updatedAt": "2026-10-08T10:01:30Z",
  "step": 1,
  "state": {
    "sceneId": "boat",
    "visited": ["start", "boat"],
    "ended": false,
    "endReason": null
  }
}
```

Rules:

- A run pins `pack.id` and `pack.version` (G8). Resuming against a different
  version is `SNAPSHOT_PACK_MISMATCH`; the host decides whether to expire or
  migrate (spec 24 sets the migration policy).
- `state.sceneId` and every `visited` entry must exist in the run's locale
  scenes, or `SNAPSHOT_INVALID`.
- `state` is the extension point: spec 24 adds counters, flags, sets, and
  objectives; spec 27 adds generated content. Fields added within the same
  `snapshotVersion` must be optional on read. A breaking change bumps
  `snapshotVersion` and ships a migration.
- Unknown top-level fields are ignored on read (not preserved).
- Treat snapshots as personal data: they can contain start-input text.

### 2.7 Determinism

The sequence of views is a pure function of: pack version, locale, start
inputs, seed, and the accepted commands. Time appears only in snapshots. In
this spec randomness is not consumed after the seed is chosen; the seed is
stored so later specs can use it without changing the API.

## 3. Authored scene format v1

Authored content uses the same schema generated content will use (A9).

### 3.1 Files

For each declared locale, `scenes/<locale>/*.json` entries in the manifest
`files` list. Each file:

```json
{ "schemaVersion": 1, "scenes": [ /* Scene objects */ ] }
```

A pack that does **not** declare `GENERATION` must have at least one scene file
for every locale. A pack that declares `GENERATION` may omit scenes until spec
27 requires authored fallback.

### 3.2 Scene

| Field | Type | Required | Rules |
|---|---|---|---|
| `id` | string | yes | Id format (spec 22 §5.4), unique per locale across all files |
| `start` | bool | no | Exactly one scene per locale has `start: true` |
| `blocks` | object[] | yes | 1–20 blocks |
| `options` | object[] | no | 1–8 options; see below |
| `next` | string | no | Scene id; for scenes that continue on `Advance` |
| `end` | bool | no | `true` ends the run after this scene is shown |

**Exactly one** of `options`, `next`, `end: true` must be present (every scene
has exactly one way forward or ends the run).

**Block:** `kind` (`NARRATION` | `SPEECH` | `SYSTEM`, required), `speaker`
(required for `SPEECH`, forbidden otherwise, 1–40 chars), `text` (required,
1–2000 chars, plain text, `\n` allowed), `refs` (optional string[] of
`catalog:id`, each `[a-z][a-z0-9-]*:[a-z][a-z0-9-]*`; existence is checked by
spec 30).

**Option:** `id` (id format, unique within the scene), `label` (1–120 chars),
`next` (scene id).

Unknown fields in a scene file are WARNINGs (`PACK_UNKNOWN_FIELD`, pointer
names the field).

### 3.3 Validation

`SceneSectionValidator` (a `SectionValidator`, registered by
`Engine.sectionValidators()`) runs after the built-in pack checks pass.

| Code | Severity | When |
|---|---|---|
| `SCENES_MISSING` | ERROR | Pack without `GENERATION` has no scene file for some locale |
| `SCENE_FIELD_INVALID` | ERROR | Scene, block, or option breaks a rule in §3.2 (pointer names the field), including "not exactly one of options/next/end" |
| `SCENE_START_MISSING` | ERROR | A locale has no `start` scene |
| `SCENE_START_MULTIPLE` | ERROR | A locale has more than one `start` scene |
| `SCENE_LOCALE_MISMATCH` | ERROR | Locales differ in scene ids or in a scene's option ids |
| `SCENE_NO_END` | ERROR | No `end` scene is reachable from the start scene |
| `SCENE_UNREACHABLE` | WARNING | A scene cannot be reached from the start scene |
| `SCENE_NO_PATH_TO_END` | WARNING | A reachable scene has no path to any `end` scene |
| `PACK_DUPLICATE_ID` | ERROR | Duplicate scene id per locale (kind `scene:<locale>`), or duplicate option id within a scene (kind `option:<locale>:<sceneId>`) |
| `PACK_DANGLING_REF` | ERROR | A `next` pointing to a missing scene (kind `scene:<locale>`) |

Cycles are legal: arcs may repeat (intent: "arc is loose"). Reachability and
path-to-end checks are graph searches per locale.

The locale-parity rule keeps translations structurally identical; it applies to
authored scenes only.

## 4. Run semantics

State (`RunState`): `sceneId`, `visited` (ordered, unique scene ids ever
shown), `ended`, `endReason`. Plus the run's `step` counter.

**Start.** After validation (§2.2): state is the start scene, `visited` holds
only it, `step` is 0. If the start scene has `end: true`, the run is ended with
`COMPLETED` immediately.

**`apply(command)`:**

1. If the run has ended: reject with `RUN_ENDED`.
2. Check the command against the current scene (§2.3). Failure: reject with the
   matching issue; nothing changes.
3. `Abandon`: set `ended` and `ABANDONED`. The view has no blocks or options,
   input mode `NONE`.
4. Otherwise find the target scene (`option.next` or `scene.next`), set
   `sceneId`, append to `visited` if not already present, and if the target has
   `end: true` set `ended` and `COMPLETED`.
5. Increment `step`, set `updatedAt` from the clock.
6. Build the view of the new scene and return an accepted `StepResult`.

**View building.** `blocks` copy the scene's blocks. If the scene has options,
`options` list them in order (all enabled) and input mode is `CHOICE`;
otherwise `options` is empty and input mode is `NONE`. An ended view keeps the
final scene's blocks (an abandoned view has none).

## 5. Reference adapters

Package `com.capybee.engine.adapter` (the only place nondeterminism may live).

| Adapter | Purpose |
|---|---|
| `SequentialIdSource(String prefix)` | Deterministic ids `prefix-1`, `prefix-2`, … for tests and golden transcripts |
| `UuidIdSource` | `UUID.randomUUID().toString()` for real use |
| `Randoms.seeded(long)` | A `RandomGenerator` from `RandomGeneratorFactory.of("L64X128MixRandom")` with the given seed. The algorithm name is a constant so sequences stay reproducible across JDK versions; fail fast if it is unavailable |
| `Randoms.unseeded()` | Same algorithm, random seed |

Time uses the JDK directly: `Clock.systemUTC()` in real use, `Clock.fixed(…)` in
tests. Run stores and generator test doubles arrive in specs 24 and 27.

## 6. Console host (`engine/cli`)

Package `com.capybee.engine.cli`. The console is a host: it uses only the
public API and the pack loader.

### 6.1 Usage

```
java -jar engine-cli.jar --pack-dir <dir> [--locale <tag>] [--input <key=value>]...
                         [--seed <long>] [--script <file>] [--save <file>]
                         [--load <file>] [--help]
```

| Option | Meaning |
|---|---|
| `--pack-dir <dir>` | Required. A pack folder; its name must be the pack id. Loaded with `DirectoryPackSource` |
| `--locale <tag>` | Run locale; default: pack default |
| `--input key=value` | A start input; repeatable. For `ENUM_SET`, comma-separate the values |
| `--seed <long>` | Fixed seed; default: random |
| `--script <file>` | Read commands from a UTF-8 file instead of stdin, one line per entry; lines are echoed (§6.3) |
| `--save <file>` | Overwrite the file with the snapshot JSON after the run starts and after every accepted step |
| `--load <file>` | Resume from a snapshot file. Cannot be combined with `--locale`, `--input`, or `--seed` |
| `--help` | Print usage and exit 0 |

Exit codes: `0` played to the end, quit, or input ended; `1` the pack is invalid
(issues printed to stderr); `2` usage error; `3` the run could not start or
resume (issues printed to stderr).

On Windows consoles that do not show non-ASCII text, run `chcp 65001` first. The
host reads and writes UTF-8 explicitly.

### 6.2 Loop

1. Parse arguments. Derive the expected pack id from the last segment of the
   normalized `--pack-dir` path (a trailing slash is fine). A segment that is
   not a valid pack id → print the issue and exit 1.
2. `new PackLoader(Engine.sectionValidators()).tryLoad(id, new
   DirectoryPackSource(dir))`. Print each issue to stderr as
   `ERROR|WARNING <code> <file> <pointer>: <message>`. Any ERROR → exit 1.
3. Build the engine with `Clock`, a `RandomGenerator`, and an `IdSource` supplied
   by the caller (§6.4). Start (or resume) the run. Errors → print issues to
   stderr, exit 3.
4. Print the title line, then loop:
   - print a blank line and `ConsoleRenderer.render(view)`;
   - if the view has ended, stop (exit 0);
   - print the prompt `> ` and read a line (from the script or stdin); on end of
     input print a blank line and `[eof]`, then stop (exit 0) without sending
     any command.
5. Map the line to a command (below), call `run.apply`, and:
   - **accepted:** print any WARNING issues as `! WARNING <code>: <message>`,
     then continue the loop with the new view;
   - **rejected:** print `! <code>: <message>`, re-print the prompt without
     re-rendering the view.

Input mapping (after trimming):

| Input | Input mode | Command |
|---|---|---|
| `q` or `quit` (any case) | any | `Abandon` |
| `?` | any | none; print the help line |
| an integer N from 1 to the option count | `CHOICE` | `Choose(options[N-1].id)` |
| text equal to an option id | `CHOICE` | `Choose(thatId)` |
| anything else | `CHOICE` | none; print `! Type a number from 1 to <n> (q to quit).` |
| empty line | `NONE`, not ended | `Advance` |
| anything else | `NONE`, not ended | none; print `! Press Enter to continue (q to quit).` |

Help line: `Type a number to choose, Enter to continue, q to quit, ? for help.`

`--save` writes the snapshot after the run starts and after every accepted step
(so a quit or crash can be resumed). `--load` re-reads the pack from `--pack-dir`
and requires the same pack id and version as the snapshot.

### 6.3 Rendering

`ConsoleRenderer.render(View)` is a pure function returning text without a
leading blank line or trailing newline:

1. One line per block: `NARRATION` → the text; `SPEECH` → `<speaker>: <text>`;
   `SYSTEM` → `[<text>]`. Text containing `\n` stays multi-line. No quote marks
   are added.
2. If there were blocks: one blank line.
3. If `status` is non-empty: one line of `[<label> <value>]` items separated by
   two spaces.
4. Options: `  <n>) <label>`. A disabled option appends ` (unavailable)` and, when
   there is a hint, ` — <hint>`.
5. Input mode `NONE` and not ended: `  [Enter]`.
6. Ended: `  [end]` for `COMPLETED`, `  [abandoned]` for `ABANDONED`.

In `--script` mode, after each prompt the host prints the consumed script line:
`> <line>`. Interactive mode prints nothing extra (the terminal echoes typing).

Chrome strings (`[Enter]`, `[end]`, `[abandoned]`, `[eof]`, the help line, `!`
messages) are host chrome, fixed in one class `ConsoleChrome`. They are
language-neutral where possible; the engine's own output (`View`) is always in
the run's locale.

### 6.4 Classes

```java
public final class PlayMain {            // the only class that touches System.*
    public static void main(String[] args) { ... }   // builds ConsoleApp with System streams, systemUTC,
}                                                    // UuidIdSource, Randoms.unseeded(); exits with the code

public final class ConsoleApp {
    public ConsoleApp(InputStream in, PrintStream out, PrintStream err,
                      Clock clock, IdSource ids, RandomGenerator random) { ... }
    public int run(String[] args) { ... }            // returns the exit code; fully testable
}

public final class ConsoleArgs {
    public static ParseResult parse(String[] args) { ... }
    public record ParseResult(ConsoleOptions options, String error) {}   // exactly one is non-null
}
public record ConsoleOptions(Path packDir, LocaleTag locale, Map<String, String> inputs, Long seed,
                             Path script, Path save, Path load, boolean help) {}
public final class ConsoleRenderer { public static String render(View view) { ... } }
final class ConsoleChrome { /* constants */ }
```

`ConsoleApp` reads and writes UTF-8 and never calls `System.*` itself.

## 7. Fixtures

Spec 22 defined three fixture packs. This spec adds authored scenes to two of
them and updates their manifests. Locations: `engine/fixtures/game-packs/<id>/`.

### 7.1 `fixture-minimal` (pl)

`pack.json`:

```json
{
  "schemaVersion": 1,
  "id": "fixture-minimal",
  "version": "1.0.0",
  "name": "Fixture — Minimal",
  "locales": ["pl"],
  "defaultLocale": "pl",
  "features": [],
  "startInputs": [
    { "id": "tone", "kind": "ENUM", "required": false, "labelKey": "input.tone",
      "values": ["calm", "silly"] }
  ],
  "contentPolicy": { "freeTextAllowed": false },
  "files": ["strings/pl.json", "scenes/pl/story.json"]
}
```

`strings/pl.json`:

```json
{ "input.tone": "Jaki ma być nastrój?" }
```

`scenes/pl/story.json`:

```json
{
  "schemaVersion": 1,
  "scenes": [
    {
      "id": "start",
      "start": true,
      "blocks": [
        { "kind": "NARRATION", "text": "Stoisz na brzegu cichego jeziora. Na wodzie kołysze się mała łódka." }
      ],
      "options": [
        { "id": "boat", "label": "Wsiadam do łódki", "next": "boat" },
        { "id": "walk", "label": "Idę wzdłuż brzegu", "next": "shore" }
      ]
    },
    {
      "id": "boat",
      "blocks": [
        { "kind": "NARRATION", "text": "Łódka płynie powoli. Po chwili widzisz na środku jeziora małą wyspę." },
        { "kind": "SPEECH", "speaker": "Żaba", "text": "Cześć! Pomożesz mi znaleźć latarnię?" }
      ],
      "next": "island"
    },
    {
      "id": "shore",
      "blocks": [
        { "kind": "NARRATION", "text": "Na brzegu leży gładki, ciepły kamień. Siadasz na nim i patrzysz na wodę." }
      ],
      "options": [
        { "id": "back", "label": "Wracam do łódki", "next": "boat" },
        { "id": "stay", "label": "Zostaję jeszcze chwilę", "next": "calm-end" }
      ]
    },
    {
      "id": "island",
      "blocks": [
        { "kind": "NARRATION", "text": "Razem z Żabą znajdujecie starą latarnię. Zapala się, gdy tylko jej dotykasz." }
      ],
      "end": true
    },
    {
      "id": "calm-end",
      "blocks": [
        { "kind": "NARRATION", "text": "Robi się cicho i spokojnie. To wystarczy na dziś." }
      ],
      "end": true
    }
  ]
}
```

### 7.2 `fixture-text-dungeon` (en)

`pack.json`:

```json
{
  "schemaVersion": 1,
  "id": "fixture-text-dungeon",
  "version": "1.0.0",
  "name": "Fixture — Text Dungeon",
  "locales": ["en"],
  "defaultLocale": "en",
  "features": ["PLACES"],
  "startInputs": [],
  "contentPolicy": { "freeTextAllowed": false },
  "files": ["strings/en.json", "scenes/en/dungeon.json"]
}
```

`strings/en.json`: `{}`.

`scenes/en/dungeon.json` (rooms are authored scenes for now; spec 24.1 adds
`places.json` and map movement to this fixture):

```json
{
  "schemaVersion": 1,
  "scenes": [
    {
      "id": "cell",
      "start": true,
      "blocks": [
        { "kind": "NARRATION", "text": "You wake in a cold stone cell. The door hangs open." }
      ],
      "options": [
        { "id": "corridor", "label": "Step into the corridor", "next": "corridor" },
        { "id": "listen", "label": "Stay and listen", "next": "listen" }
      ]
    },
    {
      "id": "listen",
      "blocks": [
        { "kind": "NARRATION", "text": "Water drips somewhere far away. Nothing else moves." }
      ],
      "next": "cell"
    },
    {
      "id": "corridor",
      "blocks": [
        { "kind": "NARRATION", "text": "A long corridor. A faint draught comes from the left, and a stair rises on the right." }
      ],
      "options": [
        { "id": "left", "label": "Go left", "next": "armory" },
        { "id": "right", "label": "Go right", "next": "stairs" },
        { "id": "back", "label": "Go back to the cell", "next": "cell" }
      ]
    },
    {
      "id": "armory",
      "blocks": [
        { "kind": "NARRATION", "text": "Racks of rusty tools line the walls. Nothing here is useful." }
      ],
      "options": [
        { "id": "back", "label": "Return to the corridor", "next": "corridor" }
      ]
    },
    {
      "id": "stairs",
      "blocks": [
        { "kind": "NARRATION", "text": "Worn steps lead up toward a square of daylight." }
      ],
      "options": [
        { "id": "climb", "label": "Climb the stairs", "next": "exit" },
        { "id": "back", "label": "Return to the corridor", "next": "corridor" }
      ]
    },
    {
      "id": "exit",
      "blocks": [
        { "kind": "NARRATION", "text": "Daylight. You made it out." }
      ],
      "end": true
    }
  ]
}
```

### 7.3 `fixture-space-courier` (en)

Unchanged. It declares `GENERATION`, so it loads and validates but
`engine.start` returns `FEATURE_NOT_AVAILABLE` (console: exit 3).

### 7.4 Golden transcripts

Test resources in `engine/cli/src/test/resources/`:

- `scripts/minimal-boat.txt`: two lines, `1` and an empty line.
- `golden/minimal-boat.txt` (expected output of `--pack-dir …/fixture-minimal
  --locale pl --script scripts/minimal-boat.txt`):

```
== Fixture — Minimal ==

Stoisz na brzegu cichego jeziora. Na wodzie kołysze się mała łódka.

  1) Wsiadam do łódki
  2) Idę wzdłuż brzegu
> 1

Łódka płynie powoli. Po chwili widzisz na środku jeziora małą wyspę.
Żaba: Cześć! Pomożesz mi znaleźć latarnię?

  [Enter]
> 

Razem z Żabą znajdujecie starą latarnię. Zapala się, gdy tylko jej dotykasz.

  [end]
```

- `scripts/dungeon-loop.txt`: five lines `2`, empty, `1`, `2`, `1`. The path is
  cell → listen → cell (a loop) → corridor → stairs → exit. Generate
  `golden/dungeon-loop.txt` from a first run, then review it by hand against the
  scene file.

Golden comparison trims trailing whitespace on each line and normalizes
`\r\n` to `\n`.

## 8. Code skeleton

Signatures are the contract; bodies are up to the implementation. Records copy
collections defensively.

```java
// com.capybee.engine.port
public interface IdSource { String nextId(); }

// com.capybee.engine.content — no Jackson imports
public enum BlockKind { NARRATION, SPEECH, SYSTEM }
public record Block(BlockKind kind, String speaker, String text, List<String> refs) {}   // speaker nullable
public record SceneOption(String id, String label, String next) {}
public record Scene(String id, boolean start, List<Block> blocks, List<SceneOption> options,
                    String next, boolean end) {}                    // exactly one of options / next / end
public record SceneSet(LocaleTag locale, Map<String, Scene> scenes, String startId) {}

// com.capybee.engine.content.json — Jackson allowed
public final class SceneSectionValidator implements SectionValidator { ... }
public final class SceneSets {
    public static Map<LocaleTag, SceneSet> load(Pack pack) { ... }   // pack must already be validated
}
```

```java
// com.capybee.engine.api
public interface Engine {
    static EngineBuilder builder(PackRegistry packs) { return new EngineBuilder(packs); }
    static List<SectionValidator> sectionValidators() { return List.of(new SceneSectionValidator()); }
    StartResult start(RunRequest request);
    ResumeResult resume(RunSnapshot snapshot);
}

public final class EngineBuilder {
    EngineBuilder(PackRegistry packs) { ... }       // package-private; hosts use Engine.builder(packs)
    public EngineBuilder clock(Clock clock) { ... }
    public EngineBuilder random(RandomGenerator random) { ... }
    public EngineBuilder ids(IdSource ids) { ... }
    public Engine build() { ... }                 // IllegalStateException if a port is missing
}

public record RunRequest(PackId packId, LocaleTag locale, Map<String, String> startInputs, Long seed) {}
public record StartResult(Run run, List<Issue> issues) { public boolean ok() { ... } }   // run null iff errors
public record ResumeResult(Run run, List<Issue> issues) { public boolean ok() { ... } }

public interface Run {
    String id();
    View view();
    StepResult apply(Command command);
    RunSnapshot snapshot();
}

public sealed interface Command {
    record Choose(String optionId) implements Command {}
    record Advance() implements Command {}
    record SubmitText(String text) implements Command {}      // reserved: rejected until spec 26
    record Abandon() implements Command {}
}

public record StepResult(boolean accepted, View view, List<Event> events, List<Issue> issues) {}
public record Event(String id, Map<String, String> payload) {}
public record Issue(Severity severity, String code, String message) {
    public enum Severity { ERROR, WARNING }
}

public record View(List<Block> blocks, List<Option> options, InputSpec input,
                   List<StatusItem> status, boolean ended, EndReason endReason) {}
public record Option(String id, String label, boolean enabled, String hint) {}            // hint nullable
public record InputSpec(InputMode mode, int maxChars) {}
public enum InputMode { NONE, CHOICE, TEXT, CHOICE_OR_TEXT }                               // TEXT modes arrive in spec 26
public record StatusItem(String id, String label, String value) {}
public enum EndReason { COMPLETED, ABANDONED }

public record PackRef(PackId id, PackVersion version) {}
public record RunState(String sceneId, List<String> visited, boolean ended, EndReason endReason) {}
public record RunSnapshot(int snapshotVersion, String runId, PackRef pack, LocaleTag locale, long seed,
                          Map<String, String> startInputs, Instant createdAt, Instant updatedAt,
                          long step, RunState state) {}

// com.capybee.engine.api.json — Jackson allowed
public final class SnapshotCodec {
    public static String toJson(RunSnapshot snapshot) { ... }
    public static SnapshotParse fromJson(String json) { ... }          // never throws for bad content
    public record SnapshotParse(RunSnapshot snapshot, List<Issue> issues) {}   // snapshot null iff errors
}
```

Implementation classes (`DefaultEngine`, `RunImpl`, `ViewBuilder`) are
package-private in `api`. `InputMode` already lists the text modes so the enum
does not change in spec 26; this spec only ever produces `NONE` and `CHOICE`.

```java
// com.capybee.engine.adapter — nondeterminism allowed here
public final class SequentialIdSource implements IdSource {
    public SequentialIdSource(String prefix) { ... }
    public String nextId() { ... }                // prefix-1, prefix-2, ...
}
public final class UuidIdSource implements IdSource {
    public String nextId() { ... }
}
public final class Randoms {
    public static RandomGenerator seeded(long seed) { ... }
    public static RandomGenerator unseeded() { ... }
}
```

## 9. Quality gates

- Spec 22 gates continue to apply to core: `NoFrameworkOnClasspathTest`,
  `EngineBoundaryTest` (B2–B6), JaCoCo 70%.
- Each core package gets a `package-info.java`. `api`'s states that it is the
  public API and everything else is internal.
- **`engine/cli` gates:**
  - `NoFrameworkOnClasspathTest` (same classes as in core).
  - `CliBoundaryTest`: scans `src/main/java/com/capybee/engine/cli`; theme
    vocabulary (B3) is forbidden, and `System.in`, `System.out`, `System.err`,
    `System.console` are allowed **only** in `PlayMain.java`.
  - JaCoCo 70% on the module. `PlayMain` stays a few lines (it only wires
    `System.*` into `ConsoleApp` and exits), so it needs no exclusion.
- Extend `.github/instructions/engine.instructions.md` with: "The console host is
  `engine/cli`. Only `PlayMain` touches `System.*`; `ConsoleApp` takes streams,
  clock, ids, and random as constructor arguments so it can be tested."
- CI is unchanged: `engine-ci.yml` runs `mvn -B verify` for the whole project.

## 10. Testing

Tests mirror package structure. JUnit 5 and AssertJ only.

**Core (`engine/core`):**

- `SceneParserTest` — valid file; every `SCENE_FIELD_INVALID` rule (each limit,
  exactly-one-of, speaker rules, ref format); unknown field warning.
- `SceneSectionValidatorTest` — one test per code in §3.3, including: pack with
  `GENERATION` and no scenes loads; pack without `GENERATION` and no scenes →
  `SCENES_MISSING`; locale mismatch in scene ids and in option ids.
- `EngineStartTest` — valid start; one test per start-validation code in §2.2;
  default locale; seed drawn from the injected random when absent; ids and
  timestamps come from the injected ports; `fixture-space-courier` →
  `FEATURE_NOT_AVAILABLE`; start scene that is itself an end scene.
- `RunApplyTest` — `Choose`, `Advance`, `Abandon` flows on both authored
  fixtures; every rejection leaves the view, state, and step unchanged;
  `RUN_ENDED` after the end; `SubmitText` → `INPUT_NOT_EXPECTED`; revisiting a
  scene (dungeon loop) keeps `visited` unique.
- `ViewBuilderTest` — input mode and options for each scene shape; ended and
  abandoned views.
- `SnapshotCodecTest` — round trip; each `SNAPSHOT_*` code; unknown top-level
  fields ignored; ISO-8601 instants.
- `ResumeTest` — resuming mid-run and continuing yields the same views as the
  uninterrupted run; pack version mismatch; unknown scene id.
- `DeterminismTest` — two runs with the same pack, inputs, seed, fixed clock,
  and sequential ids, driven by the same commands, produce equal view sequences
  and equal snapshots.
- `AdaptersTest` — `SequentialIdSource`; `Randoms.seeded` repeats the same first
  values for the same seed and differs for another seed.
- `PackFixturesLoadTest` (spec 22) — updated to build the loader with
  `Engine.sectionValidators()`; all three fixtures still load.

**CLI (`engine/cli`):** tests find fixture packs with
`Path.of("../fixtures/game-packs/<id>")` (surefire's working directory is the
module directory `engine/cli`).

- `ConsoleRendererTest` — each block kind; options; a disabled option with and
  without hint; status line; `[Enter]`; `[end]`; `[abandoned]`; empty blocks.
- `ConsoleArgsTest` — valid and invalid combinations; repeated `--input`;
  `--load` conflicts; missing `--pack-dir`; `--help`.
- `ConsoleAppTest` (in-memory streams, fixed clock, sequential ids, seeded
  random) — golden `minimal-boat` and `dungeon-loop`; invalid input messages;
  `q` abandons; end of input prints `[eof]` and sends nothing; exit codes 0, 1
  (broken pack copied to a temp directory), 2, 3 (`fixture-space-courier`);
  `--save` then `--load` produces the same remaining transcript as an
  uninterrupted run; two identical runs produce identical output.
- `CliBoundaryTest`, `NoFrameworkOnClasspathTest` — §9.

## 11. Acceptance criteria

1. `cd engine && mvn -B verify` is green for both modules on a clean checkout,
   with no `app/server`, database, Node, or API key.
2. `java -jar engine/cli/target/engine-cli-0.1.0-SNAPSHOT.jar --pack-dir
   engine/fixtures/game-packs/fixture-minimal --locale pl` plays interactively
   to the end.
3. The scripted golden transcripts pass; the same seed and script give
   identical output; save/load continues a run exactly.
4. `fixture-text-dungeon` plays, including the loop back to the cell.
5. `fixture-space-courier` loads but cannot start (`FEATURE_NOT_AVAILABLE`,
   exit code 3).
6. Core has no console I/O and no framework classes; `PlayMain` is the only
   class touching `System.*`.
7. `git diff --stat -- app Dockerfile fly.toml` is empty.

## 12. Open questions for Pavlo

1. **Command set.** `SubmitText` (reserved, always rejected until spec 26) and
   `Abandon` are in the sealed set now. Keep, or add `SubmitText` only in spec
   26?
2. **Scene text is plain text.** `\n` for line breaks, no markup; hosts choose
   emphasis. A lightweight markup could be added later without breaking this
   format. OK?
3. **Locale parity as an ERROR** (§3.3): every locale must have the same scene
   ids and option ids. Strict keeps translations aligned; relax if you expect
   locales to diverge.
4. **Runnable jar via `maven-shade-plugin`.** Alternative: IDE run configuration
   only. Default is the shaded jar so `java -jar` works anywhere.
5. **Console chrome is fixed English-neutral markers** (`[Enter]`, `[end]`, …)
   for this dev tool. Revisit in spec 28 if the console becomes a shippable game.

## 13. Rollout checklist

- [ ] Spec 22 is implemented and `cd engine && mvn -B verify` is green.
- [ ] Add `port.IdSource` and the `adapter` classes (§5).
- [ ] Add `content` and `content.json` (scene model, parser, validator); register
  the validator in `Engine.sectionValidators()`.
- [ ] Add the `api` types, `DefaultEngine`, `RunImpl`, `ViewBuilder`, and
  `api.json.SnapshotCodec` (§8).
- [ ] Update `fixture-minimal` and `fixture-text-dungeon` (§7) and re-run
  `PackIndexLintTest`.
- [ ] Add module `cli`: POM (§1), `PlayMain`, `ConsoleApp`, `ConsoleArgs`,
  `ConsoleRenderer`, `ConsoleChrome`; add `<module>cli</module>` to
  `engine/pom.xml`.
- [ ] Add scripts and golden files (§7.4).
- [ ] Add all tests from §10.
- [ ] Update `.github/instructions/engine.instructions.md` (§9).
- [ ] Add `engine/README.md` (below).
- [ ] Update `docs/specifications/README.md` (add spec 23) and, if new, the
  intent index.
- [ ] Verify the acceptance criteria in §11.

`engine/README.md`:

```markdown
# engine

Standalone, headless game engine (SDK) and console host. See
`docs/specifications/22-…` and `23-…` for design.

- Build and test: `cd engine && mvn -B verify`
- Play a fixture pack:
  `java -jar cli/target/engine-cli-0.1.0-SNAPSHOT.jar --pack-dir fixtures/game-packs/fixture-minimal --locale pl`
- Scripted run: add `--script <file>`; save and resume with `--save <file>` and
  `--load <file>`.
- Windows consoles: run `chcp 65001` first so non-ASCII text displays correctly.
```

## Notes

- This spec adds no rules, state, or events. Scene navigation is the whole
  state machine; spec 24 adds counters, flags, sets, effects, conditions,
  objectives, and starts raising events. Spec 24.1 adds the optional place graph.
- Spec 24 (Rules and state) extends the scene schema (effects and conditions
  on options) and snapshot `state`. Spec 24.1 adds `places.json`, `Command.Move`,
  `View.places`, and place snapshot fields to `fixture-text-dungeon`.
- If anything here conflicts with the repo at implementation time, the repo
  wins; flag the difference in the PR instead of silently following either.
