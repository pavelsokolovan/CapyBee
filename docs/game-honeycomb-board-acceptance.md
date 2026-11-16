# Spec 30.3 — Playable honeycomb board acceptance

Baseline: `16c654b`. Implementation date: 2026-11-16.
See the [specification](specifications/30.3-playable-honeycomb-board-instruction.md).

## Implemented experience

The RPG has one prominent illustrated board before its current moment. Fixed
terrain art and one public player marker show accepted location. HTML labels,
current/visited text, inspection, destination buttons and a semantic list use
public IDs and advertised moves. At 360px the seven-place board remains
illustrated; containers with less than 92px per cell use the list. Terrain is
at least 64px and the player marker is 36px wide. Shared hints appear once.

A destination marked `ENTER_SCENE` submits one existing `MOVE`. The engine
prepares its scene through the established authored/generated fallback path
before committing location and shared scene-entry effects. One accepted
revision exposes the resulting encounter, discovery and party together.
Pending/offline/rejected actions retain the previous accepted view. The board
has no queue, persisted selection, generation call or animation timer.

Only an accepted adjacent location change on the same displayed layout may
animate the player for 200ms. Initial load, replacement, resize/mode changes,
nonlocal relocation and reduced motion use the final position immediately.
User-origin accepted entry focuses the shared current-moment heading; background
updates and replay do not refocus it. Initial/resumed views start at the run
heading. Location-only movement retains useful map focus.

## Mechanics and compatibility

Places schema 2 opts into required destination entries and navigation policy.
Bindings, conditions and source scenes are validated, bounded and private.
Eligibility and execution share the existing condition evaluator. Preparation
failure cannot half-move mechanics, and rollback never resets generation usage.
`GO_TO` remains an explicit relocation without recursive entry.

Current Pack **1.3.0** binds seven destinations. Crossing entry requires the
existing rail preparation; arrival and Continue moments disable map movement.
Entering or leaving a hero's place never recruits them. Existing story choices
remain playable, including decline and rest. Completion eligibility adds only
this version's unchanged reviewed endings. Pack 1.0.0–1.2.0 files remain exact
and retained in server and CLI packaging; their MOVE remains location-only.

Public view schema 2 adds optional `moveBehavior`, `moveHint` and
`movementHint`; capabilities advertise `place-entry-v1`. Unknown behavior and
bad optional hints degrade without authorizing movement or rejecting valid
story. No snapshot, database or storage schema changes are needed.

## Verification

- Standalone engine `clean verify`: **273 tests passed** across core, adapter,
  session and CLI; boundary/index validation and coverage gates passed.
- Frontend coverage: **23 files, 173 tests passed**. The configured offline
  coverage thresholds passed; those percentages do not describe whole-app
  coverage. Controller tests cover one MOVE per click, accepted/pending/rejected
  state, focus, mode parity, long labels and optional-field degradation.
- Exact backend gate `mvn -B -f app/pom.xml -pl server -am verify` passed all
  modules, including **107 server tests**. The final targeted browser gate
  passed again after the stable marker layout adjustment and repackaged the JAR.
- Official PowerShell UI build/static sync passed. Both static directories
  matched `app/ui/dist`, and all **25 static files** matched the final server JAR.
- Spring/PostgreSQL/Chromium checks cover a map-only neighboring-hero route,
  one-revision entry, no recruitment on movement, pending retention, offline
  acceptance/lost acknowledgement/reload, replay, a real second-tab command and
  stale selection reconciliation. Existing auth/account replacement, two-tab,
  PWA cold offline shell, private-storage sentinels, authored/generated fallback,
  three resolutions and equally valid decline/rest checks passed.
- The reference board remained graphical at 360px; tests measured terrain
  >=64px and player >=32px. Normal-motion adjacent movement used **200ms**;
  reduced motion removed its transition. List fallback at 200% zoom had no
  horizontal page overflow. Keyboard inspection/focus, forced colors and hidden
  artwork retained labels and controls. See [browser evidence](evidence/30.3/browser-verification.json).
- Official package/archive verification passed. The actual packaged executable
  completed each of the three 1.3.0 routes in **two fresh JVMs**, with generation
  disabled and a durable checkpoint between them. Retained 1.0.0 authored
  scripts also passed against their explicitly pinned directory.

### Resource and artifact identity

Current Pack indexed-resource SHA-256 (sorted path, NUL, exact file bytes):

```text
916198585c1b4e514c8d97c3e0a33971d9f93bdc8d5f906f71efe89c9848eef3
```

Indexed file counts for 1.0.0 / 1.1.0 / 1.2.0 / 1.3.0 are **5 / 29 / 31 / 31**.
Every indexed file, including each manifest, matched canonical source in both
the packaged server JAR and CLI distribution. The earlier resource hashes are
unchanged. These content hashes differ from host preparation fingerprints.

[Exact artifact evidence](evidence/30.3/artifact-verification.json) records Pack
hashes, cold-process routes, UI parity, CLI/server JAR and SBOM hashes, and the
archive identity. The inspected archive SHA-256 is:

```text
2032e21f2ac854c7722143542dbaefc1a34e4ff9684a11c283443f246d6dda63
```

The candidate truthfully records parent revision `16c654b` plus a dirty working
copy and `development` capability because it was built before this implementation
commit. It is not a clean release candidate. Build outputs remain under
`app/engine/target/distribution/`; evidence JSON is committed with the source.

### Real-run visual evidence

These crops show the complete affected surfaces from Spring-served runs. Tall
board/list captures extend the viewport height for the image; the interaction
checks use a 360×800 viewport before capture.

- [Seven-place mobile board and visible Capy](evidence/30.3/board-mobile.png).
- [Pending destination, accepted marker retained](evidence/30.3/destination-pending.png).
- [Encounter after one accepted MOVE](evidence/30.3/accepted-encounter.png).
- [Desktop board](evidence/30.3/board-desktop.png) and
  [equivalent list](evidence/30.3/board-list.png).
- [200% zoom list fallback](evidence/30.3/board-zoom-list.png) and
  [forced colors](evidence/30.3/board-forced-colors.png).

Mobile, desktop, pending, accepted encounter, zoom and forced-color images were
visually inspected. All story labels remain Polish; these captures use English
shell controls. English/Polish shell copy is covered by the UI/browser suite.

## Release limits

This is a development build, not visual release certification or a deployment.
No live/paid provider was enabled. Illustrated encounter composition and full
text-mode controls, presentation generation/prefetch and final visual release
certification remain Specs 30.5–30.7. Spec 30.4's party display remains integrated.
