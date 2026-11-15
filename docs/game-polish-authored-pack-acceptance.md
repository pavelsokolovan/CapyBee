# Spec 29.1 — authored Polish Pack acceptance

Tested 2026-11-15 in the implementation tree based on `7101e96`. No live provider
call, production activation or Mission/reward write was introduced.

Pack: **capybee-new-hive 1.0.0**, locale/default **pl**. Canonical resources and
reviewed inventory are in
[the Pack](../app/engine/reference-game-packs/capybee-new-hive/CONTENT-INVENTORY.md).

Indexed resource fingerprint (SHA-256):
`2d3440263967bb1b12b2efc4a938cef2925713930f9a54655a37882c3241d439`.
The inventory defines the exact path/NUL/byte digest algorithm; documentation is
outside that runtime index. Pack/resource version and digest are asserted by
engine tests and the server classpath copy is compared against the canonical
manifest and every indexed file.

## Content and mechanical acceptance

The Pack has seven named places in a bounded HEX arrangement, fifteen authored
scenes, three fictional animal companions with a trait/need each, optional help
and decline routes, repeated visits, explicit Continue, legal/unavailable direct
moves, and three reachable resolutions. Remembering the old home uses a lantern;
exploration and rest remain valid. All outcomes share the same technical result
class, without affection/health/emotional scores or a mandatory recovery arc.

Names and encounter descriptions are authored Polish public narrative blocks
with stable actor refs. The universal runtime/console and authored schemas are
unchanged. Generated character panels/resources are not needed for this edition;
29.2 owns optional generation. Story/setup resources are Polish, while the
existing application shell labels the story language in both EN and PL.

Each ending includes one reviewed optional real-world suggestion or neutral
rest. Proposed catalog bindings are recorded in the inventory; 29.1 does not
activate host reward/completion eligibility or save/complete a Mission. Abandon
is tested separately and has no completed outcome.

## Verification

| Check | Result |
|---|---|
| Standalone `mvn -f app/engine/pom.xml clean verify` | Pass: 135 core, 14 adapter, 52 session and 51 CLI tests; all existing coverage gates retained |
| Authored finite-state proof | All 2,111 reachable mechanical states can reach one of the three authored resolutions |
| Strict route and resume suite | Four reviewed routes pass; snapshot JSON is resumed after every accepted route revision; unavailable MOVE leaves state unchanged |
| Fresh console process | Each route pauses after its first accepted revision and finishes in a new provider-disabled process using the same durable store |
| `npm run test:coverage` | 136 tests pass; existing thresholds retained |
| `scripts/dev-workflow.ps1 -Action sync` | Production UI built and both Spring static locations synchronized |
| Exact backend reactor `mvn -B -f app/pom.xml -pl server -am verify` | 97 tests pass, including classpath packaging and real PostgreSQL HTTP/browser/restart checks |
| Integrated Polish browser extension | Pack selection, all three endings, three optional companions, authoritative map movement, exact reload/continue, EN/PL shell labels, 360px, 200% zoom and reduced motion pass |
| CLI distribution package/verifier | Pass; canonical new Pack and strict authored scripts included |
| Release-package failure injection | Pass: tampering, unexpected files and missing required files rejected |
| Packaged authored scripts | Four routes pass using the frozen distribution JAR and copied Pack resources with provider disabled |
| `dev.ps1 -Action game-certify-offline -SkipBuild` | Existing English success/partial/postponed certification and dynamic offline certification pass against the same frozen candidate |
| `git diff --check` | Pass |

The existing English reference Pack, authored/dynamic snapshot tests, offline
certification and recovery scenarios in the ordinary engine/backend suites pass.
Existing English resources and stored formats are unchanged. The server/browser
host alone allowlists the new Pack in its test catalog. Production catalog and
API/client/provider flags keep their defaults; development/operator allowlisting
is explicit.

Environment: Linux, Java 25.0.4.1, Node 24.22.1, PostgreSQL 16.16 and Chromium
156.0.8078.79. Maven uses the environment proxy/system CA trust store. The
application-context verification uses a process-local loopback OAuth test
registration, as in 29.3/29.4; authentication/provider doubles remain test-only.
The rendered Polish rest screen was visually inspected at 360px.

## Exact development candidate

Built once from current verified JARs with `scripts/package-engine.ps1 -SkipBuild`.
This is a dirty/SNAPSHOT development candidate, not a tagged production release.
Package checks and authored route execution use its exact bytes.

- Candidate: `0.1.0-SNAPSHOT-7101e96674df-64425a83be8d`
- Archive SHA-256: `64425a83be8d2e53ee39a366976cec0d70ca902093387de17c9c57640f017460`
- CLI JAR SHA-256: `2f4d171f3a416c5e3fa7dd9e47ebf4d4e6d0616cd9930122952065aa826511f8`
- Server JAR SHA-256: `0624d37462cd28da60b8b36dcfdd7d41a731369b933c9b8024f48abb923dd296`

No story transcript, wish, child data or credentials is included in this evidence.
Generated build/static metadata is excluded from the implementation commit.

Independent native-speaker/emotional-framing signoff, screen-reader/audio output,
physical mobile PWA checks and production/live-provider release acceptance remain
29.5 operator checks. No deterministic result certifies provider suitability or
live Polish story quality/cost. The authored Pack is complete without generation;
29.2 and the proposed host eligibility bindings remain separate work.
