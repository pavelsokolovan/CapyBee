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

Three test packs exercise different locales, inputs and optional features:
`fixture-minimal`, `fixture-text-dungeon`, and `fixture-space-courier`.
The source boundary scanner has self-tests for every enforced rule.
Section validators run in registration order and share a reference index
for cross-file links.

## Console-First Roadmap

[Spec 22](../docs/specifications/22-engine-core-and-pack-format-instruction.md)
delivers the pack SDK, not a playable runtime. Spec 23 adds the common
in-process `Engine`/`Run`/`Command`/`View` API and a minimal authored console
loop. Spec 28 expands the console host with play, validate, and simulate
commands. Neither needs Spring or the web app. Web integration follows in
specs 29-31. No empty runtime ports or invented scene schemas are shipped
ahead of those contracts.