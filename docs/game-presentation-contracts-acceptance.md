# Spec 30.2 — Public presentation contract acceptance

Baseline: `996c537`. Implementation date: 2026-11-15.
See the [specification](specifications/30.2-public-presentation-contracts-instruction.md)
and [public contract](game-presentation-contracts.md).

## Implemented behavior

Public view schema 2 now has optional `presentation` schema 1. It carries
validated local artwork IDs, localized traits/needs and accepted recruited
companions. The existing capabilities endpoint advertises `presentation-v1`.
Projection uses entered scenes and existing flags; moving alone cannot reveal a
hero. Metadata is read-only and contains no flag names, discovery conditions,
future bindings, full catalog or presentation source files.

Authored characters use reviewed public actor IDs. Generated characters keep
their accepted engine-owned IDs/names and bind through validated cast slots,
so a hero is not duplicated by its authored reference. Replaying commands,
declining help, revisiting and reading views cannot manufacture party members.
Short replies retain the accepted envelope. Snapshot/store schemas are unchanged;
provider-disabled resume reconstructs identical metadata from the pinned Pack.

React receives a bounded whitelist object keyed only to public characters and
places. Unsupported/malformed envelopes degrade independently of valid
story/actions. Old views retain absent optional fields; v1 ignores metadata.
Unknown catalog versions preserve safe text/membership with neutral local art.
Unknown/wrong-family/hostile asset references use the fixed 30.1 fallbacks;
unknown expressions use neutral. No markup is parsed or passed to a renderer.

## Pack and resource compatibility

Pack **1.2.0** contains four character definitions, fifteen scene/backdrop
bindings, seven place/terrain bindings and twenty allowed art IDs. Routes,
mechanics, generation slots and recruitment flags match 1.1.0. Version 1.2.0
reuses the existing reviewed completion bindings without changing rewards or
Mission semantics. Production/provider flags are unchanged.

Indexed resource SHA-256 for 1.2.0:

```text
fcc77f64ad4ea557f4f142c7f3ce904f1774af26aab22323ff65ae8c1498560c
```

This resource digest sorts `pack.json` plus manifest `files` lexicographically,
then hashes UTF-8 path, NUL and exact bytes for each. It is distinct from the
host's preparation fingerprint, which additionally incorporates normalized
manifest fields using its existing algorithm.

Versions 1.0.0 and 1.1.0 are unchanged in source and retained in server/CLI
packaging. Indexed file counts including the manifest are 5, 29 and 31 for
1.0.0, 1.1.0 and 1.2.0 respectively. Server JAR resources were compared byte for
byte against each canonical version. Existing saved versions keep their view
and mechanics; they are not upgraded to illustrated metadata on resume.

Packaged CLI verification exposed a pre-existing assumption that a directory
basename was the Pack ID. Numeric retained-version directories were unusable.
The CLI now discovers the ID through a bounded, strictly parsed manifest and
then uses the normal full loader. The expected-ID host API remains strict.
Generic loader and CLI tests cover this distinction.

## Verification record

- Standalone engine `clean verify`: validators, optional metadata projection,
  generic discovery/recruitment cases, short replies, malformed/duplicate JSON,
  bounds, immutable collections and retained-version compatibility.
- Reviewed reference routes exercise authored and generated-fallback 1.2.0,
  including all three resolutions and provider-disabled snapshot/resume at
  every accepted step. Public character/enrichment IDs agree, party members
  are unique, and both previous versions resume through the new engine.
- A shared JSON golden is round-tripped by Java session DTO tests and decoded
  by TypeScript. Command envelopes cover APPLIED/REPLAYED/REJECTED. The common
  production DTO fixtures include nullable metadata; a legacy v1 fixture
  remains unchanged.
- Frontend: **20 files / 155 tests passed**, including nine focused presentation
  decoder/catalog/wire tests. Configured coverage passed: 89.45% statements,
  86.58% branches, 96% functions, 97.32% lines. These metrics cover the
  repository's configured offline/session scope. Fixture changes were followed
  by the affected contract/decoder checks, all passing.
- Production frontend build and official PowerShell static sync completed;
  both Spring static directories match `dist/` byte for byte. The frontend
  imports serializable catalog metadata without enabling the development art
  gallery or bundling its SVG rendering components into gameplay.
- The exact backend Maven gate and existing Spring/PostgreSQL browser suite
  passed with deterministic providers and process-only placeholder OAuth
  configuration. Browser assertions cover no hero disclosure after MOVE,
  encountered trait/need and art, accepted recruitment, same-revision command
  replay and exact reload. Existing recovery/privacy/offline/multitab checks,
  all endings and EN/PL shell checks also passed.

After the retained-directory fix, standalone engine `clean verify` and the full
backend/browser gate were rerun successfully. Final engine counts are 144 core,
54 session and 52 CLI tests, with no failures/errors/skips in those modules.
The browser's offline movement check now waits for the accepted destination and
confirms the server's location before reload, instead of treating a shared
status notification as a map-render barrier. No production queue/controller
change was needed.

The official package script built and verified the directory and archive, with
both versions retained. Byte comparisons verified indexed resources in the
CLI distribution and server JAR against all three canonical versions. The
packaged CLI then cold-resumed all **four reviewed routes for each version**:
12 completed route runs across 24 provider-disabled process launches. These
cover all three resolutions, optional recruitment, declined encounters and
revisits, using the actual numeric retained-version directories.

The archive is a **development** candidate built from the working tree, not a
promoted release. Its SHA-256 is
`e7b0350a634d57cb0639f52604f30941a7f50a0139ca9af062e42dbb8c7d5bbf`.
The exact artifact/resource hashes, route inventory and gate outcomes are in
the [machine-readable verification record](evidence/30.2/verification.json).

## Scope and limits

This supplies data for 30.3–30.5. It does not introduce playable encounter entry,
the visible party, portrait rendering, generated art selection or new prefetch.
Dynamic games continue through their existing text-first public view without
invented metadata. The engine remains independent of React, Spring and product
art vocabulary; concrete assets and traits are Pack data.

Tests use fictional content and deterministic providers. No paid provider call,
production deployment, new data persistence or real-child input was required.
Full visual/mobile/old-save release certification remains Spec 30.7.
