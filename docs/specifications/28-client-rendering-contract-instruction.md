# Spec 28 — Web RPG Client and Rendering Contract

**Type:** Umbrella implementation specification
**Parent:** [Universal game engine intent](intent/01-universal-game-engine.md) and [dynamic game generation intent](intent/02-dynamic-ai-game-generation.md)
**Depends on:** Specs 22–27.27.5, particularly the source-neutral web session API in Spec 27.27.4
**Baseline:** Inspected `main` at `48bb38bb1912ed3d64b9dff1ca2a13213d7b2e0f` on 2026-11-14. Source inspection and existing GitHub Actions results only; no application build, provider call or manual gameplay was performed while preparing these specifications.
**Status:** Implemented — delivered in Specs 28.1–28.4
**Primary outcome:** A signed-in family can enter the RPG from Play, prepare and explicitly start an available game, read and act on the engine's view, leave and resume safely, and reach an ending through the integrated Spring-served React/PWA client.

## 1. Delivery boundary

The engine already owns mechanics, safe input handling, generated blueprints,
presentation, persistence and endings. This series adds its web client and the
small public-contract corrections needed to consume those capabilities. It does
not implement a second browser game engine.

The first release is a text-first adventure client. It supports authored Pack
runs, fixed-Pack generated presentation, and dynamic runs through one renderer.
New generated starts remain an explicitly enabled deployment capability. A
technical implementation of this series is not permission to enable paid
generation in production or a claim that the CapyBee Polish game is complete.

## 2. Repository findings

| Finding at the baseline | Owner in this series |
|---|---|
| `AuthenticatedHome.tsx` exposes Pollen Match and Hive Builder Tap; no RPG screen is wired in. | 28.2, 28.3 |
| `gameApi.ts` contains declarations only, requires Pack identity, omits `source`, and leaves places untyped. | 28.1 |
| Server summaries already discriminate `PACK` and `DYNAMIC`; legacy Pack aliases can be null. | 28.1 |
| Profile discovery omits setup steps, input labels/kinds, requiredness and selection limits. | 28.1 |
| Pack discovery returns raw `StartInputDef` values rather than a complete localized setup contract. | 28.1 |
| `View` does not explicitly say whether `ADVANCE` is legal; empty options also occur in text or terminal scenes. | 28.1, 28.3 |
| The sync engine persists arbitrary game payloads, retries POST on `202`, and has no game-action owner namespace. | 28.1, 28.4 |
| Dynamic-start operations are process-local; a status lookup can disappear after restart. | 28.2, 28.4 |
| `DynamicStartOperationService.submit` schedules work before `putIfAbsent`; cancelling its future is not proof that engine publication stopped. | 28.1 release prerequisites |
| Current generic and detective profiles have no reviewed fallback bindings. The operation DTO exposes a fallback code, not a playable fallback descriptor. | 28.2 |
| `classic-detective` targets ages 15–17; the product targets around age 12. Allowlisting alone is not audience eligibility. | 28.1, 28.2 |
| Game API, generation and dynamic starts default to disabled. | 28.1, 28.4 |

The latest standalone engine CI passed. Backend verification failed in
`PostgresDynamicMigrationTest` at line 36 because PostgreSQL JDBC could not bind
`java.time.Instant` without an explicit SQL type. The release-certification
workflow failed without executing jobs. These are baseline verification
blockers, not work completed by drafting Spec 28. An implementation must resolve
them before claiming integrated/release verification; do not lower gates or
silently skip PostgreSQL tests.

## 3. Implementation parts

| Part | Deliverable | Dependencies | Reviewable completion |
|---|---|---|---|
| [28.1 — Public contracts and transport](28.1-game-client-contracts-and-transport-instruction.md) | Source-neutral types/decoders, capability and setup metadata, safe mutation/polling transport, explicit advance affordance, operation prerequisites | Existing engine/server | Contract fixtures and transport tests pass; existing APIs remain compatible |
| [28.2 — Preparation and start](28.2-game-preparation-and-start-instruction.md) | Play entry, available games/current run, localized wizard, disclosure/review, confirmed start and operation recovery | 28.1 | A deterministic HTTP start reaches a persisted run without implicit paid retries |
| [28.3 — Gameplay renderer](28.3-gameplay-renderer-and-run-controls-instruction.md) | Text-first scenes, legal commands, objectives/cast/places, endings, leave/resume/abandon | 28.1; integrated entry from 28.2 | Authored and dynamic fixtures play to endings through the same accessible UI |
| [28.4 — Recovery and release acceptance](28.4-game-client-recovery-and-release-acceptance-instruction.md) | Owner-isolated queue recovery, PWA/auth/multitab resilience, integrated tests and operator documentation | 28.1–28.3 | Browser/PWA restart, provider-disabled resume and regressions pass against Spring/PostgreSQL |

Implement in that order. Component work for 28.3 may use deterministic fixtures
after 28.1, but a part is complete only after its stated integration and checks.
Keep the web entry behind a disabled rollout flag until 28.4 passes. Each part
is its own implementation commit; the parent stays Draft until all four pass.

## 4. Non-negotiable behavior

- The server's accepted run, revision, options and result are authoritative.
  The browser never compiles rules, predicts a next scene or advances optimistically.
- Every new start is online-only and requires a final user confirmation. Lost
  responses, reconnect, reload, remount and auth restoration never authorize a
  fresh paid start.
- Optional story wishes and gameplay free text remain in memory only. No URL,
  IndexedDB, local/session storage, service-worker cache, logs or analytics may
  contain them. A normalized accepted story may reflect the supplied fiction.
- Existing OAuth/session restoration remains the authentication boundary.
  No provider credential or new long-lived auth token enters the client.
- At most one unresolved command per owner/run in this client. Retried durable
  commands retain their original UUID, payload and expected revision.
- Shell/disclosure/error copy is EN/PL. Story locale belongs to the run and is
  not changed by switching shell language or refreshing profile metadata.
- Profile discovery and start eligibility are enforced by the host, including
  audience policy. The older-teen detective profile is not the default child game.
- Leave is reversible navigation; abandon is a confirmed engine command;
  delete is a separate destructive API and is not offered by this series.
- Empty/disabled discovery must not hide a saved run that can still be read.

## 5. Scope kept for later specifications

Spec 29 owns the CapyBee Polish pack, honeycomb RPG art/map, recruitment content,
check-in/mood personalization, Mission handoff and RPG reward/honeycomb policy.
This series does not post mini-game results or grant rewards for an RPG ending.

Also out of scope: a run library/archive/trash UI, sharing, multiplayer, chat,
profile uploads, arbitrary assets/URLs, generated illustrations, client-side
mechanical simulation, offline generation/execution, notifications, new provider
adapters, and a general redesign of existing app screens.

Use the existing two engine intent notes; a third intent note and a waiting
period are unnecessary for this already accepted roadmap item. Preserve their
historical decisions and link this delivery series from the specification index.

## 6. Acceptance and evidence

Spec 28 is complete only when all child criteria pass and one signed-in family
can discover, prepare, start, command, leave, resume and finish both a supported
Pack game and a dynamic fixture through the integrated client. Dynamic resume
must work with new starts/profile/provider disabled, while the game API remains
enabled. No provider credentials are required for automated acceptance.

Record the tested revision, commands, browser/viewport and outcomes in a short
content-free verification note. Do not commit generated stories, private text,
snapshots, database dumps or paid live evidence. Engine/backend certification
alone does not prove this client complete; browser-only tests alone do not prove
server persistence or restart recovery.

After all parts pass, mark this parent and its index entry implemented. Follow
the repository's `spec <number> is implemented - <short description>` commit rule
for implementation. Preparing these documents does not implement any part.


## Implementation completion — 2026-11-14

Specs 28.1–28.4 implement public contracts, preparation/start, shared gameplay
and owner/offline/auth/PWA/multitab recovery. Local engine/backend/frontend,
integrated PostgreSQL/browser and package/offline/compatibility gates passed.
Historical baseline findings above are retained as the original inspection.
The operational contract and tested limits are recorded in
[game-web-client.md](../game-web-client.md) and
[acceptance evidence](../game-web-client-acceptance.md). Production flags remain
disabled by default; live-provider/device certification and activation are
operator actions. Spec 29, rewards and the graphical game map are not implemented.
