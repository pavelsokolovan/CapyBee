# Game Engine SDK

A standalone Java 25 Maven module. It does not inherit from Spring Boot and
does not depend on the web application, a database, network services, or API
keys. The published artifact is `com.capybee:game-engine:0.0.1-SNAPSHOT`, a
plain JAR, not a Boot executable JAR. The working Java namespace remains
`com.capybee.engine`; it is not a dependency on the product.

## Build And Test

From the repository root:

```sh
mvn -f engine/pom.xml clean verify
mvn -f engine/pom.xml -Dtest=PackLoaderTest test
mvn -f engine/pom.xml install
```

Or open `engine/` as a standalone IDE project and run `mvn verify` there.
Reports are in `target/surefire-reports/` and `target/site/jacoco/` inside
this module. Every engine package must reach 70% line coverage. Tests need
only the JDK and Maven; after dependencies are cached, `mvn -o verify` works.

The provider-neutral `TextGenerator` port, generation pipeline, and
`StructuredInputInterpreter` live in `engine-core`. Core has no provider or
network dependency. `engine-cli` includes the optional Anthropic adapter for
explicit generated play; authored play never configures or invokes it. Verify
the adapter and CLI through the reactor with:

```sh
mvn -f engine/pom.xml -pl cli -am verify
```

Live adapter configuration requires `GENERATION_MODEL_ID` and
`ANTHROPIC_API_KEY`. The model ID has no implicit default; use an explicitly
pinned model value. API credentials are never read from packs or logged. The
adapter tests use a local stub server and need no key or external network.

## Generated Play

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

The root POM is an aggregator, not a shared parent. `mvn verify` at the
repository root verifies both the engine and backend; backend tests still
need their existing environment. Backend-only builds and Docker deployment
remain unchanged. The backend will depend on the SDK when its host is added
in spec 29, not before.

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

Four test packs exercise different locales, inputs and optional features:
`fixture-minimal`, `fixture-text-dungeon`, `fixture-space-courier`, and
`fixture-generated`.
The source boundary scanner has self-tests for every enforced rule.
Section validators run in registration order and share a reference index
for cross-file links.

## Console-First Roadmap

[Spec 22](../docs/specifications/22-engine-core-and-pack-format-instruction.md)
delivers the pack SDK. Specs 23-26.1 now provide the in-process runtime,
rules/state, safety boundary, generation pipeline, generated-play CLI, and
artifact continuity. Spec 27 retains ownership of the public session lifecycle
and host-facing API contract; specs 28-31 cover the expanded console, web host,
client rendering, and game pack. None of these engine modules needs Spring or
the web application.