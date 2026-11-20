# Polish Pack generation and privacy (29.2)

The current Pack is `capybee-new-hive:1.1.0`, in
`app/engine/reference-game-pack-versions/capybee-new-hive/1.1.0`.
Version 1.0.0 remains byte-for-byte in its original canonical directory and is
packaged under `game-packs/capybee-new-hive/retained/1.0.0`. The version-aware
registry selects 1.1.0 for new starts and the exact retained version on resume.
No snapshot migration or applied Flyway migration changes.

## Content and universal contract

The 15 scenes, seven places, authored rules, flags, choices, movement and three
equally valid SUCCESS outcomes are unchanged. Optional generation changes only
presentation. `AUTHORED_GRAPH`, catalog schema 2 / contract 1, uses the existing
full presentation schemas, strict scene/actor/objective/choice IDs, quality
review and artifact schema 3. Unlike `FULL_GAME`, it permits authored revisits,
skipped phases and uniform result classes. `length` is explicitly 0/0 (no
narrative route-length promise); structural reachability and declared endings
still validate. It cannot be substituted for a dynamic profile fallback.

Initialization is THEME → SCENARIO → QUEST → CAST → initial SCENE; subsequent
scenes resolve only on entry. The accepted theme is included in scenario context.
Every stage and all 15 slots have indexed Polish fallbacks, without external
assets or URLs. Presets are tone (cozy default/curious), up to two interests
(nature/making/animals), optional editable mood (heavy/okay/good; initially
unset). No check-in is required or read. No notes, child identity, age, history,
friendships or precise location are included in provider envelopes.

## Transient start and retention audit

`POST /api/game/pack-starts` accepts requestId, packId, packVersion,
packFingerprint, locale, selections, optional suggestion, disclosureRevision=1,
confirmed=true and optional expectedCurrentRunId. The fingerprint covers public
Pack resources, never the wish. The fictional wish is at most 240 Unicode code
points and only available online with generated narration selected. EN/PL review
masks it as “idea added” and explains external AI, accepted-save behavior and
reviewed fallback. Public metadata and disclosure are refreshed before each
explicit start; changed identities require another review.

* The request and guarded wish exist only in operation/worker memory. Request
  DTO diagnostics are redacted. Neither request nor response bodies are logged.
* Operation keys contain owner + random request UUID; replay signatures contain
  validated public presets/version/fingerprint/locale/replacement only. No wish
  hash. Registry results contain run UUID/status/code only; completed entries
  expire after ten minutes. No durable pending-start row is created.
* Browser markers retain only owner, request UUID, path and time. Starts and
  text bypass IndexedDB queues, history, URLs, caches, local/session storage and
  metrics. Reload/lost response uses GET, never an automatic retry POST.
* The wish is exposed only to THEME's untrusted setup data after the local guard.
  Later stages receive accepted artifacts and allowlisted presets. Literal
  normalized wish echoes in decoded provider strings are rejected before
  acceptance; fallback is used. The provider wrapper clears its thread scope.
* Only accepted snapshot content and enum startInputs are published. No raw
  body, guard transcript, private setup, command or raw-wish outbox event is
  persisted. Legacy `/runs` rejects free-text/unknown keys and generated
  `AUTHORED_GRAPH` starts so they cannot bypass the transient boundary.
* Publication suspends the expected owned ACTIVE run and inserts the accepted
  Pack snapshot in one transaction. Stale replacements/uniqueness failures
  leave the old run intact. Provisional run counts become accepted run counts;
  child/global ledger charges survive cancellation/failure. GET can recover a
  published operation by its deterministic owned run identity after restart.

Cancellation is serialized with publication, best effort and body-free. Calls
already sent can cost money. Cancellation/deadline checks prevent subsequent
provider calls; the local pipeline may finish fallback work before releasing
its slot. Operations have a three-minute publication deadline, bounded worker
queue, at most two concurrent preparations by default, and 1,000 registry entries.
Existing host rate/run/child/global call limits and provider output token ceilings
apply; Pack limits are 20 total attempts, two per stage and at most one repair.
No new worker/daemon or retry of a paid start is installed.

## Safety and operator decision

The host supplies one reviewed local `InputGuard` to preparation and gameplay
(and the existing dynamic coordinator). Scrubbing precedes it. Polish/English
identifying information, distress/emergency phrases and instruction injection
are blocked before any provider call. Distress returns optional trusted-adult
support in EN/PL; emergency copy offers nearby trusted adult/local emergency
help. This finite local policy makes no clinical-triage claim; it needs ongoing
operator/content review before live child use.

**Operator decision: PENDING — live child-audience provider use is not approved.**
No evidence of contractual approval for provider retention, child-audience
eligibility, subprocessing, regional handling or deletion has been supplied.
An operator must review those current terms and record an accountable decision
before setting `CAPYBEE_GAME_PACK_PROVIDER_APPROVED=true`. Pack narration uses
the shared `CAPYBEE_GAME_GENERATION_ENABLED` switch, which defaults to `true`.
Provider approval defaults to `false`. Deterministic fixtures explicitly enable them
for tests only; no paid calls or live certification are authorized by this work.
Host completion rollout remains separately disabled by default.

## Verification

`VersionedPresentationTest` checks unchanged mechanics, all inventory routes,
complete provider-disabled fallback/resume and retained 1.0.0 compatibility.
`PackPreparationTest` checks dependency order, lazy initial scene, private
context/snapshot exclusion, literal echo rejection, Polish safety with zero
provider/interpreter calls, cancellation, replay and operator approval.
Spring/PostgreSQL acceptance checks private table exclusions, 202/replay,
cancellation and atomic replacement, then an actual provider-disabled host
restart with the same published view. Browser acceptance checks masked review,
240-code-point limit, offline wish, lost response, GET recovery, one explicit
attempt, reload and all browser persistence/cache/log sentinels. Existing English,
dynamic, offline queue, map and completion suites remain required.

Verified 2026-11-15 with Java 25, Node 24, PostgreSQL 16 and Chromium:
SDK clean verify **254 tests** (137 core / 14 adapter / 52 session / 51 CLI),
backend reactor verify **103 server tests** including PostgreSQL/browser and
actual provider-disabled restart, UI coverage **139 tests** and workflow static
sync. All configured coverage gates pass. Pack index: 28 runtime resources.

Development candidate `0.1.0-SNAPSHOT-e0056703144d-4a96870fb1ed` was built from the
working tree over `e0056703144db7ae3ae1fa0f6345757a7d7e8ed5` (dirty=true;
not a release-certified artifact). Archive SHA-256:
`4a96870fb1ed3561a455df48fda37b7cc5ee11c7a5f0427af1b724ac2e06128b`.
Package verifier and checksum/missing/unexpected-file failure injection pass.
Clean-room English and dynamic offline certification pass, as do 12 packaged
Polish strict routes (four each: retained 1.0.0, authored 1.1.0, generated 1.1.0),
with provider disabled. CLI directory names must match Pack IDs; retained
version resources were copied to a fresh ID-named directory for these checks.
29.5 release/content/device/assistive-technology acceptance remains pending;
no paid live certification or production rollout was performed.
