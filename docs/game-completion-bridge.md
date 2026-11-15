# Spec 29.4 — host completion bridge

Implemented against `3752298`. The universal engine has no Mission, calendar,
OAuth or reward dependency. Production completion rollout is **disabled** by
`capybee.game.completion.enabled=false` (environment:
`CAPYBEE_GAME_COMPLETION_ENABLED`). Existing game/provider flags are unchanged.

## Eligibility and reviewed suggestions

Only an owned, persisted PACK run with database status `ENDED`, end reason
`COMPLETED`, a matching ended snapshot/run/Pack identity and the exact reviewed
Pack version/outcome qualifies. Stored host revision must still match. Dynamic
sources, unknown outcomes/versions, active/partial progress, abandoned,
failed/suspended/preparing/cancelled runs do not qualify. Generated presentation
of the same authored Pack does not change its mechanics or reviewed outcome.
Narrative result classes do not affect cell eligibility or anyone's worth.

The current allowlist is `lanterns-of-willow-marsh` version `1.0.0`:

| Authored outcome | Suggestion key/version | Existing catalog step |
|---|---|---|
| `radiant-festival` | `say_thank_you_helper` / 1 | Say thank you to someone who helped you this week |
| `gentle-lighting` | `notice_small_win_today` / 1 | Notice one small thing that went better than you expected |
| `festival-postponed` | `rest` / 1 | Neutral rest alternative; no Mission acceptance |

Catalog titles/time hints use existing EN/PL localization. No new catalog entry
or generated title, instruction, reflection, URL or identifier is created.
The authored Polish Pack from 29.1 now provides reviewed binding proposals in
[its inventory](../app/engine/reference-game-packs/capybee-new-hive/CONTENT-INVENTORY.md).
The completed Polish binding integration (2026-11-15, after 29.2) allowlists
`capybee-new-hive` versions **1.0.0 and 1.1.0 only**, with exact ending/outcome
pairs:

| Ending / authored outcome | Suggestion key/version | Optional step |
|---|---|---|
| `ending-new` / `new-path` | `say_thank_you_helper` / 1 | Thank someone for a small help |
| `ending-two` / `two-lights` | `listen_song_reminds_home` / 1 | Listen to a song that reminds you of home |
| `ending-rest` / `rest-here` | `rest` / 1 | Neutral rest; no Mission to accept |

Both catalog Missions already have EN/PL translations. Authored and generated
presentation of 1.1.0 share these bindings; accepted generated prose never
becomes a Mission identifier. All three endings have equal cell eligibility.
Unknown versions (including other patch versions), mismatched ending/outcome
pairs and other sources cannot inherit eligibility. Retained 1.0.0 completions
can reconcile through the existing server-owned startup/ending path. No new
DTO, migration, retention field, grant policy or rollout flag is added. Production
completion rollout remains disabled. The HTTP fixture's minimal Pack bindings
are test-only, not production eligibility.

## DTOs and routes

All new endpoints are authenticated, owner-scoped and `no-store`, including
errors. `X-Game-Owner` retains the stale-account guard. Reads do not project,
repair or otherwise mutate data.

| Method/path | Contract |
|---|---|
| GET `/api/game/runs/{runId}/completion` | `CompletionDto`, or 202 `{status:"PROCESSING",retryAfterMs:1000}` while an eligible persisted ending awaits server reconciliation |
| POST `/api/game/runs/{runId}/completion/mission` | Accept the stored reviewed catalog suggestion |
| POST `/api/game/runs/{runId}/completion/cell` | Request its optional hive-cell projection |
| DELETE `/api/game/runs/{runId}/completion` | Archive this host completion offer and remove its RPG result projection |
| GET `/api/game/mission-selections` | Distinct active catalog Mission UUIDs selected by this owner through non-archived completed runs; empty when rollout is disabled |

Accept body: `{id: UUID, expectedRevision: number, suggestionVersion: number}`.
It never accepts a Mission ID, timestamp, duration, grade, outcome or metrics.
Idempotency is the owner/run/action identity and stored reviewed version; retries
with another transport UUID still cannot repeat effects. Wrong revisions or
versions fail with 409; missing/archived/ineligible resources are not offered.
Malformed bodies/IDs fail with 400. A stale account hint fails before execution.

`CompletionDto` contains `runId`, `revision`, `completedAt`, `suggestionKey`,
`suggestionVersion`, `missionAccepted`, `cellAdded`, and nullable `mission`:
`{id,code,title,timeHint}`. These are host/catalog values, never generated prose.

Accepting a Mission stores a reviewed selection/bookmark and makes the catalog
row visible as “Chosen in an adventure.” It does not insert Mission completion
or interaction history, move its last-actioned time or add a hive cell. Explicit
“Open Missions” preserves a Mission already expanded in that screen. Missing,
inactive or deleted catalog entries cannot be selected/recreated. Dismiss and
Back send nothing; dismissing the offer does not undo an earlier accepted choice.

The generic POST `/api/games/results` reserves `capybee_rpg`, including attempts
to reuse an RPG result ID through a mini-game key. Pollen Match, Hive Builder and
other existing generic results keep their contract. Generic game history now
also receives no-store/stale-owner protection. It is not proof of RPG completion.

## Schema, projection and interruption recovery

Forward migration **V14__game_completion_bridge.sql** adds:

- `game_run_completions`: run primary key, authenticated owner, accepted revision,
  server completion timestamp, reviewed suggestion key/version, selected catalog
  Mission UUID, Mission/cell acknowledgement flags and an archive tombstone.
- Nullable unique `game_results.rpg_run_id`, cascading from `game_runs`, and a
  constraint requiring this linkage exactly for `capybee_rpg` writes.

The origin constraint is `NOT VALID` to preserve pre-existing untrusted rows;
new/updated rows must satisfy it. Legacy `capybee_rpg` rows without linkage are
excluded from history and cannot be promoted by generic retries. No applied
migration or historical narrative is rewritten.

The durable completed run is the reconciliation work source. An after-commit
host event submits reconciliation to the **existing** game-session executor;
application readiness also reconciles persisted completed runs. Thus a restart
between saving a run and creating its host completion repairs the gap without
a browser completion POST or an additional always-on worker. Reads remain pure.
Rejected executor work is retained in that durable source for the next ending
or startup. The database is authoritative across processes/devices.

Acceptance locks the owned completed run, then its completion row, and commits
acknowledgement and requested projection together. Result insertion is an upsert
by unique run linkage. Server reconciliation also repairs a missing requested
projection. The completion time is `game_runs.ended_at`; metrics are `{}` and
`durationMs` is 0 (not collected), independent of the browser clock or narrative.
Only an explicit cell choice requests this projection. No React effect grants it.

Archival deletes the projection and retains a tombstone; retries/reconciliation
cannot recreate it. Hard deletion of the owned run cascades its completion and
RPG projection. Deleting a catalog Mission nulls the selected linkage while
retaining acknowledgement, so a retry cannot recreate or rebind that Mission.
Deleting ordinary Mission completion history does not recreate that history.
No dialogue, raw wish, transcript, generated reflection or mood note is copied
into this linkage, result metrics or Mission history.

## Offline and local calendar policy

`gameCompletion` is an owner/run-scoped, non-text action in the existing sync
loop. Durable payload is only `kind: CELL|MISSION`, `expectedRevision` and
`suggestionVersion`; a top-level server completion timestamp is only a local-day
UI hint and is never sent as award authority. Its canonical endpoint is derived from the kind. Unknown
payload fields are stripped. Dispatch sends only the accept DTO. Owner changes
abort/forget outgoing choices, and ownerless actions are removed. No engine
CHOOSE, second flusher, text storage or revision rebasing is introduced.
Acceptance is idempotent, so an uncertain response retries the same linkage.
Explicit recovery can reset bounded retries for that original owner/run action.

All game offer checks use the existing browser-local calendar convention across
Pollen Match, Hive Builder and RPG entries. A pending offline RPG cell choice
reserves that local-day UI offer until history is reconciled (an earlier day
does not consume today’s offer), without creating an
optimistic RPG projection. Pending mini-game rows survive history refresh and
reconcile with server rows by ID. Honeycomb history deterministically keeps one
game entry per local day (earliest timestamp, then ID), including duplicate rows
from offline sync, replay and multiple devices. Date formatting/deduplication
re-evaluates in the current local zone; midnight, focus and clock/zone changes
refresh offers. RPG offers use the server's completion date in that local zone.

This remains **Spec 20's UI offer policy**, not a server daily-grant ledger.
Multiple devices may persist multiple opted-in results; the displayed cell is
deduplicated. No extra RPG allowance, Mission reward, score or streak is added.

## Verification

Tested 2026-11-15 on Linux with Java 25.0.4.1, Node 24.22.1,
PostgreSQL 16.16 and Chromium 156.0.8078.79.

| Gate | Result |
|---|---|
| `npm run test:coverage` in `app/ui` | 136 tests pass; existing coverage thresholds preserved |
| `scripts/dev-workflow.ps1 -Action sync` | Production build passes; both Spring static locations synchronized |
| Calendar tests with `TZ=Europe/Warsaw` | Four midnight/DST/local-calendar tests pass in addition to the ordinary UTC run |
| `mvn -B -f app/pom.xml -pl server -am verify` with the documented browser environment | 96 backend tests pass, including PostgreSQL, browser and fresh-host checks; all coverage gates pass |
| `git diff --check` | Pass |

Maven used the environment proxy/system trust store. The application-context
test used the same process-local loopback OAuth registration as 29.3; browser
authentication/providers and eligibility overrides remain test-only. No real
Google sign-in or paid provider request was made. The 360px completion screenshot
was visually inspected. Generated static/build metadata is excluded from the
implementation commit.

The recovery browser fixture now holds the acknowledgement after the server
receives the original offline command, then reloads. This makes its one-POST
assertion deterministic while retaining original-UUID status recovery; a
navigation-cancelled request that never reached the server may still safely
retry only after the existing not-found/revision reconciliation.

Tests cover fabricated generic results and legacy rows, source/outcome/status
and revision guards, cross-owner access, concurrent/duplicate acceptance,
server restart reconciliation, deletion and missing projections, reviewed
catalog selection without Mission completion, offline acceptance/reload,
shared day offers, duplicate history, midnight and DST/zone behavior. Existing mini-game save/service and unrelated offline mutation tests remain
green. The browser suite retains owner/auth, private storage sentinel, stale-tab
movement and cold offline shell coverage.

The test host alone enables completion and fixture eligibility. Production
rollout and Polish Pack acceptance remain work for 29.1/29.5; no live provider
call, production flag change or deployment is part of this implementation.

## Polish binding verification (2026-11-15)

Implemented after `121bc48` (29.2). Policy tests cover exact retained versions,
ending/outcome pairs, null/unknown identities and unchanged English bindings.
PostgreSQL acceptance runs the actual authored routes for all three endings in
1.0.0, authored 1.1.0 and provider-disabled generated 1.1.0: nine completed runs.
Each receives the same reviewed offer, owner guards, idempotent acceptance and
minimal empty-metrics cell projection. Mission completion/interaction tables
remain empty; the rest alternative cannot be accepted as a Mission.

The full backend reactor verify passes **107 server tests**, including migration,
restart/reconciliation, deletion, fabricated results, reserved keys and the real
Spring/PostgreSQL/Chromium suite. Polish browser routes check both catalog codes,
explicit offline non-text Mission acceptance and acknowledgement, dismissal
without a write, localized no-Mission rest, separate cell effects and existing
daily-cap/recovery checks. UI coverage remains **139 passing tests**; workflow
build/static sync passes. No new migration/DTO and no live provider calls.
29.5 release acceptance and production rollout remain pending.
