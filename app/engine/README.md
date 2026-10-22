# Game Engine SDK

## Reference generated game

`reference-game-packs/lanterns-of-willow-marsh` is the primary manual full-game example: a 15–25 minute, English, family-friendly adventure with provider-backed presentation and a complete offline fallback. Packs under `fixtures/game-packs` remain focused test assets rather than player recommendations. Its README contains stable success, partial-success, and responsible-postponement scripts.

A standalone Java 25 Maven module. It does not inherit from Spring Boot and
does not depend on the web application, a database, network services, or API
keys. Its artifacts are `com.capybee:engine-core`,
`com.capybee:engine-adapter-anthropic`, `com.capybee:engine-cli`, and
`com.capybee:engine-session`, all at `0.1.0-SNAPSHOT`; each is a plain JAR,
not a Boot executable JAR. The working Java namespace remains
`com.capybee.engine`; it is not a dependency on the product.

## Build And Test

From the repository root:

```sh
mvn -f app/engine/pom.xml clean verify
mvn -f app/engine/pom.xml -Dtest=PackLoaderTest test
mvn -f app/engine/pom.xml install
```

Or open `app/engine/` as a standalone IDE project and run `mvn verify` there.
Reports are in `target/surefire-reports/` and `target/site/jacoco/` inside
this module. Every engine package must reach 70% line coverage. Tests need
only the JDK and Maven; after dependencies are cached, `mvn -o verify` works.

The provider-neutral `TextGenerator` port, generation pipeline, and
`StructuredInputInterpreter` live in `engine-core`. Core has no provider or
network dependency. `engine-cli` includes the optional Anthropic adapter for
explicit generated play; authored play never configures or invokes it. Verify
the adapter and CLI through the reactor with:

```sh
mvn -f app/engine/pom.xml -pl cli -am verify
```

Live adapter configuration requires `GENERATION_MODEL_ID` and
`ANTHROPIC_API_KEY`. The model ID has no implicit default; use an explicitly
pinned model value. API credentials are never read from packs or logged. The
adapter tests use a local stub server and need no key or external network.

## Generated Play

The normal player entry point is session-backed and saves automatically:

```powershell
./dev.ps1 game --pack-dir ./app/engine/fixtures/game-packs/fixture-generated-full-minimal --generated
```

Or invoke the packaged cross-platform artifact directly:

```sh
java -jar app/engine/cli/target/engine-cli-0.1.0-SNAPSHOT.jar play --pack-dir <pack-directory> --generated
```

Use `--memory` for explicitly temporary play. Player commands include `/help`, `/look`,
`/status`, `/objectives`, `/characters`, `/map`, `/save`, `/quit`, and confirmed `/abandon`.
`/quit` detaches without ending the saved run.

Generation is opt-in. Authored packs and the default console path remain
provider-free. A generation-enabled pack indexes `generation/catalog.json`,
`generation/templates.json`, stage/slot fallback JSON, and the existing
locale-specific prompt and scene files. The v1 template registry is the small
versioned addition introduced by spec 26.1 because no Spec 24 template package
or template-definition format existed in this checkout. Templates declare
trusted actor slots, objective allow-lists, and tone vocabularies; the catalog
declares stages, dependencies, budgets, scene slots, trusted choices, and
validated fallbacks.

Run generated play with `--generated`. `GENERATION_MODEL_ID` and
`ANTHROPIC_API_KEY` are read from the environment only; if unavailable, the
configured authored fallbacks are used and reported. `--live-smoke --script
<file>` is a separate opt-in real-provider test and may incur provider cost.
It exits non-zero unless the real adapter generates or repairs the required
stages and at least one later scene without fallback. It is excluded from
ordinary tests and builds.

Use `--session` for the session-backed local console. `/quit` leaves the run
active; `/abandon` ends it. `--store-dir <dir>` preserves run snapshots between
console launches, while omitting it keeps runs in memory. `--run <uuid>` selects
a specific saved run; otherwise the current open run is resumed unless
`--new` is supplied. `--debug` prints in-process call and prefetch diagnostics.

Live-provider failures are reported as fixed codes, never raw response bodies:
`PROVIDER_BILLING_UNAVAILABLE` indicates insufficient API credits;
`PROVIDER_STRUCTURED_OUTPUT_UNSUPPORTED` indicates the configured model rejects
structured output; `PROVIDER_REQUEST_INVALID` indicates another HTTP 400
request error. Check API billing in the provider console for the first case,
and the provider's supported-model list for the second. A chat subscription
does not itself establish available API credits. Never share an API key when
reporting these diagnostics.

Generated artifacts are typed, safety-checked, cached per run, and included in
run snapshots. Resume validates the artifact envelope against the pack and
does not regenerate content already shown. Provider summaries contain stage,
status, call count, timing, usage, and stable error codes only; prompts,
responses, player text, and credentials are not logged.

The [app POM](../pom.xml) is an aggregator, not a shared parent.
`mvn -f app/pom.xml verify` from the repository root verifies both the engine
and backend; backend tests still need their existing environment. Spec 27 adds the framework-neutral session
contract and the backend's engine dependency. Packs are only available to the
web host when copied into its classpath under `game-packs/<pack-id>`; the
CapyBee Polish pack remains the responsibility of spec 29.

## Pack API

```java
var loader = new PackLoader(List.of());
var source = new DirectoryPackSource(Path.of("path/to/my-pack"));
LoadResult result = loader.tryLoad(new PackId("my-pack"), source);
if (result.ok()) {
    Pack pack = result.pack().orElseThrow();
}
```

Public types live in `com.capybee.engine.pack`; the loader lives in
`com.capybee.engine.pack.json`. `tryLoad` collects content issues;
`load` throws `PackValidationException` with the same issues. Source I/O
failures and programming errors remain exceptions. Issue messages never
include file contents. Sources are immutable and loader state is per call.

Packs are immutable JSON/Markdown data. `pack.json` indexes every other
file. Sources never enumerate classpath directories and can read packs
inside JARs. File reads are bounded; directory sources reject traversal
and symlink escapes. JSON rejects duplicate keys and trailing documents.

Five test packs exercise different locales, inputs and optional features:
`fixture-minimal`, `fixture-text-dungeon`, `fixture-space-courier`, and
`fixture-generated`. `fixture-generated-full-minimal` is the technical schema-v2
full-game contract fixture; `fixture-generated` intentionally remains the
schema-v1 `CONTENT_ONLY` compatibility fixture.
The source boundary scanner has self-tests for every enforced rule.
Section validators run in registration order and share a reference index
for cross-file links.

## Roadmap Reconciliation

[Spec 22](../../docs/specifications/22-engine-core-and-pack-format-instruction.md)
delivers the pack SDK. Specs 23-26.1 now provide the in-process runtime,
rules/state, safety boundary, generation pipeline, generated-play CLI, and
artifact continuity. The web host and server-side SDK wiring move from the
earlier spec 29 plan to spec 27. The expanded local console host is included
in spec 27 rather than being a separate numbered spec. Spec 28 owns the client
rendering contract, and spec 29 owns the CapyBee Polish pack. The framework-
neutral session module does not depend on Spring or the web application.

Generation catalog schema v2 can declare a versioned `FULL_GAME` run contract.
Pack loading validates its five ordered story phases, acyclic and finishable
trusted graph, generated slot and fallback coverage, ending outcomes, route
length, and minimum provider-call budget before play. Hosts read the resulting
typed `GeneratedGameProfile`; they do not infer capability from pack names or
reparse the graph. Schema-v1 packs such as `fixture-generated` remain supported
and report `CONTENT_ONLY`. Full generated games use a typed narrative plan for
the trusted five-phase graph; generated continuity facts guide later prose but
never routes, mechanics, legal actions, or outcomes. Artifact v1 remains the
content-only format and full games use artifact v2. Full-game cast and quest
artifacts now provide display names, discovered-character metadata, and quest
presentation while trusted rules remain the sole owner of objective state.
Spec 27.7 owns richer result and ending behavior.
