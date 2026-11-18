# Spec 30.6 — Generation and prefetch acceptance

Baseline: `7d417fb`. Implementation date: 2026-11-18.
See the [specification](specifications/30.6-generation-and-prefetch-instruction.md).

Scene generation now receives its accepted theme explicitly and trusted catalog
references for the player, bound backdrop and scene-bound characters. These
references guide narration; the provider cannot replace artwork bindings or
return executable artwork. Existing strict output review, safety, authored
fallback and immutable Pack art remain authoritative. This does not add AI
artwork generation or arbitrary provider-selected imagery.

Prefetch discovers enabled choices, the actual conditional Continue target
(including the command's projected step), and eligible map-entry destinations.
It deduplicates targets and preserves ON_ENTRY restrictions. Existing session
fanout, concurrency, reserves, single-flight joins and usage accounting remain
in charge. Preparing a scene does not enter it or change the public view,
location, revision, discovery or recruitment. Production provider flags are
unchanged; disabled providers continue using authored fallbacks.

Pack 1.4.0 opts non-ending slots into PREFETCHABLE timing. Endings stay ON_ENTRY.
Mechanics, catalog artwork and reviewed completion bindings match 1.3.0. Exact
1.0.0–1.3.0 Packs remain retained in server and CLI packaging. There are no
browser production changes, new queues or persistence schemas.

## Verification

- Standalone engine `clean verify` passed, including boundary/index lint,
  session concurrency/budget tests and CLI tests, with coverage gates met.
- Final focused tests passed for exact trusted character asset vocabulary,
  accepted theme context, disabled-choice filtering, legal neighboring map
  prefetch without entering/revealing the character, fallback and cold resume.
- Exact backend `mvn -B -f app/pom.xml -pl server -am verify` passed all
  modules: 152 core, 14 adapter, 55 session and 107 server tests. This includes
  Spring/PostgreSQL/Chromium authored/generated-fallback runs, all resolutions,
  recruitment, offline/uncertain reload, replay, two tabs, text mode, privacy
  sentinels, PWA and provider-disabled restart recovery.
- No frontend production source changed. The integrated suite served the
  existing production-built client against the new Pack.
- Official CLI package/archive verification passed. Current 1.4.0 and retained
  1.0.0–1.3.0 indexed resources match canonical bytes in both server JAR and CLI
  distribution. Existing UI static files also match the server JAR.
- The packaged CLI completed all three current board routes through two fresh
  JVMs per route with providers disabled, plus retained authored scripts.

See [exact artifact evidence](evidence/30.6/artifact-verification.json) for
resource hashes, cold-process results, JAR/SBOM/archive hashes and UI parity.
Current Pack resource SHA-256 (sorted path, NUL, exact indexed bytes):

```text
590e9f5a51a509f01a1bd71c9730adb3ef0e48008559ebd3fb1d9dfc4374b6f8
```

The verified package is a pre-commit development candidate rooted at `7d417fb`
with its dirty-source marker retained. This is local verification, not a
production deployment or visual release certification. No paid provider was
used or enabled.

## Remaining boundary

Visual release certification remains 30.7. Theme-grounded requests use authored
illustration bindings rather than asking a provider to invent or select new
artwork. Existing validated dynamic profiles keep their separate capabilities.
