# Spec 27 — Engine API Contract (Run Session Lifecycle, Host Endpoints, Offline-Queue Integration)

**Type:** Implementation specification
**Parent:** Spec 26.1 — Generation Contracts and Runtime Integration
**Status:** Draft rev. 3 (adds scene prefetch and a local-PC console) — grounded in repo commit `43a6491` ("engine 26.1 implemented"). Builds/tests were **not** re-run during this investigation.
**Primary outcome:** A signed-in family can start, play, resume and finish an engine run through a small, versioned HTTP API. Every command is idempotent, survives a sleeping Fly machine and a flaky phone connection, never holds a database connection during an LLM call, and can never exceed the generation budget. The next scene is generated ahead of time (**prefetch**) so most taps feel instant. The same service drives the web app (over HTTP) and a **text console (CLI) that runs on a local PC only**, in-process with no server.

---

## 1. Context

Specs 22–26.1 delivered a standalone, Spring-free engine SDK: pack format and loader, in-process `Engine`/`Run`, rules and state, places/map, input safety, provider-neutral `TextGenerator`, the generation pipeline with repair and validated fallback, lazy scene resolution, and generated artifacts embedded in snapshots. It is console-playable (`--generated`). It has **no host**: nothing persists runs, nothing serialises concurrent access, nothing meters cost, and the web backend does not depend on it.

Spec 27 defines that host boundary. Specs 28 (client rendering contract) and 29 (CapyBee Polish pack) build on it and are out of scope here.

### 1.1 What the investigation found (evidence)

| # | Finding | Evidence | Consequence for this spec |
|---|---|---|---|
| F1 | Public API is small and stable: `Engine.start(RunRequest)`, `Engine.resume(RunSnapshot)`, `Run.view()/apply(Command)/snapshot()`. Commands: `Choose`, `Advance`, `SubmitText`, `Move`, `Abandon`. | `app/engine/core/.../api/` | The HTTP contract wraps exactly these; no new engine verbs. |
| F2 | `RunSnapshot.step` increases by one for every accepted state change (Choose/Advance/Move/Abandon) and is rolled back when scene resolution fails. Rejected commands leave it unchanged. | `RunImpl.apply`, `choose`, `move` | `step` is the natural **revision** for optimistic concurrency and idempotency. A contract test must assert "accepted=false ⇒ step unchanged" for every command type including `SubmitText`. |
| F3 | `RunImpl` has no synchronisation and `Run` is a mutable object built from a snapshot. | `RunImpl` (no `synchronized`/locks) | Host must serialise per run and rebuild `Run` from the stored snapshot per command. |
| F4 | `Engine.start` calls `GenerationPipeline.initialize()` **synchronously** (SCENARIO → CAST/QUEST → SCENE). `Run.apply` may call the provider again for a lazily resolved scene. | `DefaultEngine.start`, `GenerationPipeline.resolveScene` | One HTTP request can take tens of seconds. Adapter default response timeout is 25 s with one repair attempt (`AnthropicConfig`), so a single stage can take ~50 s and `start` several minutes worst case. Start and slow commands need a **wait-then-poll** model (§8.3). |
| F5 | Any `TextGenerationException` or runtime failure inside a stage degrades to the pack's validated **fallback** artifact; the fallback is persisted in the snapshot like any other artifact. | `GenerationPipeline.execute` | A host-side budget refusal can simply throw `TextGenerationException(RATE_LIMITED)` and the child still gets valid content. **No engine change is needed for metering** (§10). |
| F6 | Spec 26 asked for a `BudgetLedger`/quota port and a `GENERATION_ARTIFACT_MISSING` resume code. **Neither exists in code.** Resume of an incomplete generated snapshot returns `SNAPSHOT_ARTIFACT_VERSION_MISMATCH`. Spec 26.1 chose "embed artifacts in the snapshot". | `grep` for ledger/quota: no hits; `DefaultEngine.resume` | Spec 27 defines the host-side ledger (§10) and the recovery policy for unresumable runs (§6.4). Artifacts stay embedded in snapshots. |
| F7 | Free-text start inputs are stripped before they enter the snapshot (`persistentInputs`). | `DefaultEngine.persistentInputs` | A `PREPARING` run that must be restarted after a machine suspend needs the free text; store it only for the preparing window and null it afterwards (§9, §13). |
| F8 | Backend is Spring Boot **4.1.0**, Java **25**, Jackson 3 (`tools.jackson`). Engine core uses the same Jackson 3 (`3.1.2`). Hikari pool is **5**; Fly VM is 1 shared CPU / 1 GB with `auto_stop_machines = "suspend"`. CSRF is disabled; auth is Google OAuth2 + Spring Session JDBC. | `app/server/pom.xml`, `application.yml`, `fly.toml`, `SecurityConfig` | No Jackson clash (good). Pool and RAM are tight: never hold a DB connection across a provider call; cap concurrent generation jobs. A suspended machine can kill an in-flight job. |
| F9 | `Dockerfile` builds only `app/server` (copies `app/server/pom.xml` and `src`); the engine is not in the image build. `app/pom.xml` is an aggregator with modules `engine` and `server`. CI already has a standalone engine job. | `Dockerfile`, `app/pom.xml`, `.github/workflows/build-test-deploy.yml` | Dockerfile and CI must change when the server depends on the SDK (§14). |
| F10 | The real offline queue (`syncEngine.ts`) differs from the spec-15 text in ways that matter: it **discards response bodies**; it treats **409 as success**; it retries every non-OK response (including permanent 400/404/422) up to 8 times with up to 60 s backoff **inside the flush loop**, so one bad item delays everything behind it; it only handles `POST`/`DELETE` bodies of the form `{id: clientId, ...payload}`. | `app/ui/src/offline/syncEngine.ts` | Game commands need response delivery, a distinct "stale revision" outcome, and terminal-vs-retryable classification (§12). |
| F11 | The server has **no** `@ControllerAdvice`/`@ExceptionHandler`; existing validation failures are `IllegalArgumentException` (default Spring handling). | `grep` in `app/server/src/main` | The game API gets its own scoped error advice with stable codes (§8.6). Do not copy the existing pattern. |
| F12 | The mini-game daily hive-cell cap is enforced **client-side only**, per device-local calendar day (`sameCalendarDay`). Missions and mini-games use client-supplied UUIDs for idempotency. | specs 20/21, `GameService` | Engine-driven rewards (spec 29) need a **server-side** cap and a server-defined day. Spec 27 exposes the event outbox and day-zone config; spec 29 owns the reward rules (§11). |
| F13 | Docs drift: `app/engine/README.md` names the artifact `com.capybee:game-engine:0.0.1-SNAPSHOT`; the POMs say `engine-core` / `0.1.0-SNAPSHOT`. `docs/CapyBee_concept.md` says Java 21. Spec 22/README number 27–31 differently from the roadmap you gave. Stray files in repo root: `generated-smoke.txt`, `snapshot.json`, `tmp-runcheck.jsh`. | repo root, READMEs | Reconciliation note (§2) and a small docs-cleanup task (§16, step 0). |
| F14 | Scene generation input is only: scenario/cast/quest artifacts, the scene id, its trusted choice ids and actor ids. It does **not** include player state or which choice led there. | `GenerationPipeline.contextJson` | A scene generated ahead of time is identical whichever path reaches it, so **prefetch has no quality cost** (§10.1). Side effect for spec 29: generated text never reacts to earlier choices; adaptation must come from trusted engine state. |
| F15 | `restoreArtifacts` accepts scene artifacts for any slot, not only visited ones; resume only requires cached artifacts for *visited* scenes. | `GenerationPipeline.restoreSceneArtifact`, `DefaultEngine.resume` | Prefetched artifacts can be merged into a snapshot and restored safely. |
| F16 | Per-run limits (`totalCallsPerRun`, `callsPerStage`) live in the pipeline **instance**. A restored pipeline starts its counters at 0. Because the host rebuilds `Run` for every command and every prefetch job, these limits no longer bound a run's lifetime. | `GenerationPipeline` (`totalCalls`, `stageCalls`), `restoreArtifacts` | **Correction to rev. 1 §10.** The host must enforce a lifetime per-run call ceiling itself (§10). |
| F17 | The CLI plays in-process through `Engine` directly (`ConsoleApp`) and has no session layer: no run ids, no command idempotency, no metering, no prefetch. | `ConsoleApp` | The local console should drive `RunSessionService` so that what you test locally is exactly the lifecycle the web app will use (§14.1). |

---

## 2. Roadmap reconciliation

Earlier documents number the follow-up work differently (spec 22 maps 27 to "generation pipeline"; `app/engine/README.md` maps 27 to the session lifecycle and 28–31 to console/web host/client rendering/pack). The current plan is:

| Spec | Scope |
|---|---|
| **27** | Engine API contract: run session lifecycle, endpoints, host persistence, metering, offline-queue integration (this document) |
| **28** | Client rendering contract: view models for hex board and scene screens; AI returns asset IDs only; React renders from a fixed SVG catalog |
| **29** | CapyBee pack (Polish): theme inputs from check-in mood, preferences, free text; real-life echo feeds Missions; honeycomb-cell rewards with a daily cap like the mini-games |

Do not renumber existing spec files. Add a short "Roadmap reconciliation" note to `docs/specifications/README.md` and `app/engine/README.md` stating: *the web host and server-side SDK wiring move from "spec 29" to spec 27; the expanded console host is no longer a separate numbered spec.*

---

## 3. Goals

1. A versioned HTTP API to **list packs, start, read, command, abandon and delete** an engine run.
2. A framework-neutral **session service** in the engine repo (no Spring/JPA) so the lifecycle is testable with in-memory fakes, like the rest of the SDK.
3. **At-least-once delivery, exactly-once effect**: every command carries a client UUID; replays are harmless.
4. **Optimistic concurrency** via `expectedRevision` (= `RunSnapshot.step`) and a short DB **lease** so two requests can never apply to the same run at once.
5. **No DB connection or transaction is held while the provider is being called.**
6. **Wait-then-poll** for operations that may call the provider (start and slow commands), safe across Fly suspend/resume.
7. **Persistent, atomic generation budgets** per profile/day and global/day, enforced by wrapping `TextGenerator`.
8. A defined **offline-queue integration** (spec 15) that fixes the queue behaviours in F10 for game commands.
9. A **domain-event outbox** and a **versioned view envelope** as the stable seams for specs 29 and 28.
10. Privacy: no prompts, responses, snapshots or player text in logs; free text never stored after use.
11. **Scene prefetch:** after each state change, generate the scenes the player can reach next, in the background, without blocking commands or exceeding budgets (§10.1).
12. **Local console:** the same `RunSessionService` can be driven in-process by the text console, with in-memory or file storage, so the game can be played and tested on a local PC with no server, database or network (§14.1).

## 4. Non-goals

- Rendering, hex-board geometry, asset catalog, view-model redesign (spec 28).
- Pack content, Polish copy, mood→theme mapping, mission/echo/reward rules (spec 29).
- Changing engine rules, generation stages, artifact schemas, fallback semantics or continuity guarantees (26/26.1). If a change turns out to be needed, document the incompatibility and propose the smallest change (26.1 §14).
- Multi-child profiles, multiplayer, public/shared runs, push notifications, WebSockets/SSE.
- Offline **play** (see D3): the next scene is server-computed, so it cannot be rendered optimistically.
- Prefetch deeper than one scene ahead, and prefetch for authored (non-generated) runs.
- Console access to a deployed (Fly) server. Not needed now; the HTTP API does not prevent it, but it would need a non-Google authentication scheme, which is deliberately not designed here.

---

## 5. Design decisions

| ID | Decision | Rationale |
|---|---|---|
| D1 | **Two layers.** New engine module `engine-session` (pure Java 25, depends only on `engine-core`) holds `RunSessionService`, ports and the metering decorator. `app/server` holds Spring controllers, security, JDBC adapters and Flyway migration. | Keeps the engine universal and independently testable (project principle). Spring is only an adapter. |
| D2 | **Revision = `RunSnapshot.step`.** Clients send `expectedRevision`; the server rejects mismatches with `409 REVISION_CONFLICT`. | F2: already monotonic and persisted. |
| D3 | **Commands are not blind offline writes.** A command is persisted to the local queue *before* sending (so a closed tab or dropped connection cannot lose it), but the UI shows a calm "CapyBee is thinking" state and waits for the server view. At most **one pending command per run**. | The next view depends on rules and generation; optimistic rendering is impossible. One-at-a-time removes ordering problems and head-of-line blocking. |
| D4 | **Idempotency key = `commandId` (UUID, client-generated), scoped to a run.** Same id + same payload ⇒ replay; same id + different payload ⇒ `422 COMMAND_ID_REUSED`. | Matches the existing "optional client UUID" convention (spec 15), but mandatory here. |
| D5 | **Rejected commands are normal results**, `200` with `accepted:false` and issues. They are deterministic and terminal for the queue. | Avoids queue retry storms; keeps HTTP errors for transport/auth/concurrency problems. |
| D6 | **Replays return the current view**, not a stored historic view. The command log stores only outcome metadata and a keyed hash, never views or text. | Smaller, privacy-safe, always correct for the client. |
| D7 | **Lease, not row lock, around engine work.** A short transaction claims `lease_token/lease_until`; engine work runs with no transaction; a second short transaction saves with compare-and-set on `revision` and the lease token. | F4/F8: pool of 5 and slow provider calls. |
| D8 | **One active run per child profile in the MVP.** `POST /runs` with a different `id` while one is active returns `409 RUN_ALREADY_ACTIVE` with the existing run id. | Matches single-profile MVP; simplifies the home screen and the budget. |
| D9 | **Metering by decorating `TextGenerator`** with a request-scoped `MeteredTextGenerator`; the host builds an `Engine` per request (cheap: `Engine.builder(registry)…build()`). | F5: failure ⇒ fallback, so no pipeline change. Per-request engine avoids ThreadLocal context. |
| D10 | **Artifacts stay embedded in the snapshot** (26.1 choice). Snapshot size is capped (§13). | Preserves the 26.1 continuity guarantee; no second store. |
| D11 | **Day boundary is server-defined**: `capybee.game.day-zone` (default `Europe/Warsaw`). | Budgets and spec-29 reward caps must not depend on a device clock (F12). |
| D12 | **Feature-flagged, dark by default** (`capybee.game.enabled=false`). Endpoints return `404` when disabled. |
| D13 | **Prefetch = depth 1, path-independent, side table, best-effort.** After a state change the host generates the uncached scenes directly reachable from the current scene and stores them in `game_run_prefetch`; commands merge them into the snapshot. Prefetch never takes the run lease and never stores fallback content. | F14/F15: safe and valid on any path. Not taking the lease means a tap is never blocked by background work. Not storing fallbacks stops a transient error from permanently downgrading the story. |
| D14 | **One service, two clients.** `RunSessionService` (in `engine-session`) holds all lifecycle rules. The web app reaches it over HTTP; the local console calls it in-process with in-memory/file adapters. DTOs and the JSON codec live in `engine-session` (`wire`). | What you try in the console is the same behavior the web app gets; no second code path to keep in sync. |

---

## 6. Run lifecycle

### 6.1 States

```
            POST /runs                       worker finishes engine.start
 (none) ─────────────────► PREPARING ───────────────────────────────► ACTIVE ──┐
                              │  worker fails / TTL                     │      │ accepted Abandon,
                              ▼                                         │      │ or engine ends run
                           FAILED ◄─────── unrecoverable resume ◄───────┘      ▼
                                                                              ENDED (COMPLETED | ABANDONED)
```

| Status | Meaning | Allowed operations |
|---|---|---|
| `PREPARING` | `engine.start` (including initial generation) is running or waiting to be (re)claimed. No snapshot yet. | `GET`, `DELETE` |
| `ACTIVE` | Snapshot exists and `ended=false`. | `GET`, `POST commands`, `DELETE` |
| `ENDED` | Engine reported `ended=true`; `endReason` is `COMPLETED` or `ABANDONED`. | `GET`, `DELETE` |
| `FAILED` | Start failed, or an active run's snapshot cannot be resumed safely. Carries `failureCode`. | `GET`, `DELETE` |

### 6.2 Revision and ordering

- `revision` is `snapshot.step` (0 for a fresh run and for `PREPARING`).
- A command is applied only if `expectedRevision == run.revision` and the run is `ACTIVE`.
- Accepted commands bump `revision` by exactly the amount the engine reports (normally +1).

### 6.3 Lease

`lease_token` (UUID) and `lease_until` (timestamptz) mark "a worker owns this run right now". Defaults: **command lease 90 s**, **start lease 5 min**, derived from the F4 worst cases and configurable. An expired lease can be re-claimed by the next request. A lease is released when the save transaction commits or the worker fails.

### 6.4 Unrecoverable resume

If `Engine.resume` fails with `SNAPSHOT_ARTIFACT_VERSION_MISMATCH`, `SNAPSHOT_PACK_MISMATCH`, `SNAPSHOT_INVALID` or `SNAPSHOT_VERSION_UNSUPPORTED`:

1. Set status `FAILED` with `failureCode = RUN_UNRECOVERABLE` (internal reason code kept in the server log only).
2. **Never silently regenerate or substitute narrative content** (26.1 §8.4). The snapshot is kept until the family deletes the run or retention expires.
3. The client offers "start a new adventure"; `POST /runs` succeeds because a `FAILED` run does not count as active.

---

## 7. Engine SDK additions — module `engine-session`

Package `com.capybee.engine.session`. No Spring, JDBC, or HTTP types. Java 25, Jackson 3 only if serialization is needed (reuse `SnapshotCodec`).

### 7.1 Ports

```java
public interface RunRepository {
    /** Insert PREPARING row. Idempotent on runId for the same owner. */
    CreateOutcome createPreparing(NewRun run);                         // CREATED | EXISTS_SAME_OWNER | EXISTS_OTHER_OWNER | OWNER_HAS_ACTIVE(existingRunId)
    Optional<StoredRun> find(String runId, OwnerRef owner);            // owner mismatch ⇒ empty (maps to 404)
    Optional<StoredRun> findCurrent(OwnerRef owner);                   // the owner's PREPARING/ACTIVE run, if any
    Optional<Lease> tryClaim(String runId, OwnerRef owner, Duration ttl);   // CAS on lease_until < now
    boolean saveActive(String runId, Lease lease, long expectedRevision, RunSnapshot snapshot);   // CAS: revision + lease token
    boolean markFailed(String runId, Lease lease, String failureCode);
    void releaseLease(String runId, Lease lease);
    void delete(String runId, OwnerRef owner);
    int purgeExpired(Instant now);                                     // lazy retention sweep (§13)
}

public interface CommandLog {
    Optional<LoggedCommand> find(String runId, String commandId);
    /** Insert IN_PROGRESS before engine work; unique (runId, commandId). */
    InsertOutcome begin(String runId, String commandId, String payloadMac, long revisionBefore);   // INSERTED | EXISTS
    void complete(String runId, String commandId, boolean accepted, long revisionAfter, List<Event> events);
    void fail(String runId, String commandId, String code);
}

public interface GenerationLedger {
    /** Atomically reserve one provider call for the scope(s); false if any cap would be exceeded. */
    boolean tryReserveCall(LedgerScope profile, LedgerScope global, LocalDate day);
    void recordUsage(LedgerScope profile, LedgerScope global, LocalDate day, TextGenerationResult.TokenUsage usage);
}

public interface EventOutbox {
    void append(String runId, long revision, List<Event> events);      // same transaction as saveActive
    List<OutboxEvent> readAfter(String runId, long afterSeq, int limit);
}
```

```java
public interface PrefetchStore {
    List<GenerationArtifact> load(String runId);                                   // all prefetched scene artifacts for the run
    boolean insertIfAbsent(String runId, String sceneId, long basedOnRevision, GenerationArtifact artifact);
    void deleteForScenes(String runId, Collection<String> sceneIds);               // after they are merged into a saved snapshot
}
```

`saveActive`, `CommandLog.complete` and `EventOutbox.append` must be callable inside **one short transaction** (the Spring adapter wraps them in a `TransactionTemplate`; the in-memory fake just applies them together).

### 7.2 Service

```java
public final class RunSessionService {
    public RunSessionService(PackRegistry packs, RunRepository runs, CommandLog commands,
                             GenerationLedger ledger, EventOutbox outbox, EngineFactory engines,
                             SessionClock clock, SessionLimits limits, Executor workers) { … }

    public StartAccepted      start(OwnerRef owner, StartRequest request);                 // always returns quickly
    public RunSummary         get(OwnerRef owner, String runId);                          // may drive/restart a stale PREPARING job
    public CommandOutcome     command(OwnerRef owner, String runId, CommandRequest request, Duration syncWait);
    public CommandStatus      commandStatus(OwnerRef owner, String runId, String commandId);
    public void               delete(OwnerRef owner, String runId);
    public List<PackInfo>     packs(Locale hint);
    public void               ensurePrefetch(OwnerRef owner, String runId);              // idempotent, best-effort, never throws to caller
    public Diagnostics        diagnostics(OwnerRef owner, String runId);                 // in-process only (§8.7)
}
```

`EngineFactory` builds an `Engine` per request: shared `PackRegistry`, `Clock`, `RandomGenerator`, `IdSource`, the engine's input guard/interpreter, and a `MeteredTextGenerator` bound to `(profileScope, globalScope, day)`.

`workers` is a bounded executor (virtual threads, plus a semaphore of `maxConcurrentGeneration`, default 2). If saturated: `GENERATION_BUSY`, retryable.

### 7.3 `MeteredTextGenerator`

```java
final class MeteredTextGenerator implements TextGenerator {
    public TextGenerationResult generate(StructuredGenerationRequest r) {
        if (!ledger.tryReserveCall(profile, global, day))
            throw new TextGenerationException(TextGenerationException.Code.RATE_LIMITED);   // pipeline ⇒ fallback (F5)
        TextGenerationResult result = delegate.generate(r);
        ledger.recordUsage(profile, global, day, result.usage());
        return result;
    }
}
```

The decorator is the only place that knows about budgets. `RunSessionService` never inspects prompts or outputs. It carries a `Priority` (`DEMAND` or `PREFETCH`); prefetch is refused earlier than demand (§10.1).

### 7.4 Engine-core additions (the only engine change in spec 27)

Two default methods on `Run`, implemented in `RunImpl`; authored runs keep the defaults (empty).

```java
public interface Run {
    // existing: id(), view(), apply(Command), snapshot(), generationSummary()

    /** Scene ids reachable in ONE step from the current scene (option targets, next, branch targets)
     *  that have a generation slot and are not yet cached. Ignores option `requires`/hide conditions
     *  (they can change with state). Empty for authored and ended runs. */
    default List<String> prefetchCandidates() { return List.of(); }

    /** Resolve one candidate ahead of time (provider call, repair, or validated fallback).
     *  Does NOT change step, state, view or updatedAt. Empty if sceneId is not a candidate. */
    default Optional<GenerationArtifact> prefetch(String sceneId) { return Optional.empty(); }
}
```

Implementation sketch: candidates come from `scene(sceneId)` (`options[].next`, `next`, `branches[].next`) filtered by `pipeline.hasSceneSlot && !pipeline.hasCachedScene`; `prefetch` calls the existing `pipeline.resolveGeneratedScene` and returns the matching artifact. Engine tests: candidates for `fixture-generated`; `prefetch` leaves `step`, `sceneId`, counters and flags unchanged; authored runs return empty; a prefetched scene is reused (no second provider call) when the player then enters it.

### 7.5 Packages of `engine-session`

| Package | Contents |
|---|---|
| `com.capybee.engine.session` | `RunSessionService`, ports (§7.1), `MeteredTextGenerator`, limits/config records |
| `…session.wire` | `RunSummaryDto`, `CommandRequestDto`, `CommandOutcomeDto`, `PackInfoDto`, `ErrorDto`, `SessionJson` (Jackson 3 codec). The HTTP controllers return these same records. |
| `…session.adapter` | In-memory implementations of every port and `LocalSessionStore` (one versioned JSON document) for console use |

---

## 8. HTTP API (package `com.capybee.server.game`)

Base path `/api/game`. JSON only. Auth: existing Google session (`401` if absent). Every run is owned by the authenticated user (and their single `FamilyProfile`); a run id belonging to someone else is `404`, never `403`. All bodies are rejected above 16 KB.

### 8.1 Endpoints

| Method | Path | Purpose | Success |
|---|---|---|---|
| `GET` | `/api/game/packs` | Available packs, locales, start-input definitions | `200` |
| `POST` | `/api/game/runs` | Start (or idempotently re-request) a run | `202` (PREPARING) / `200` (already ACTIVE, same id) |
| `GET` | `/api/game/runs/current` | The owner's PREPARING/ACTIVE run, or `404` | `200` |
| `GET` | `/api/game/runs/{runId}` | Run summary + current view | `200` |
| `POST` | `/api/game/runs/{runId}/commands` | Apply one command | `200` / `202` |
| `GET` | `/api/game/runs/{runId}/commands/{commandId}` | Poll a command that returned `202` | `200` / `202` |
| `GET` | `/api/game/runs/{runId}/events?after={seq}&limit=50` | Domain events (outbox) | `200` |
| `DELETE` | `/api/game/runs/{runId}` | Delete run, snapshot, command log, events, prefetch rows | `204` |

Abandon is a command (`type: "ABANDON"`), not a separate endpoint, so it is idempotent through the same machinery.

### 8.2 Start

`POST /api/game/runs`

```json
{
  "id": "6f1c…uuid",
  "packId": "capybee-pl",
  "locale": "pl",
  "generated": true,
  "startInputs": { "mood": "heavy", "freeText": "…" },
  "seed": null
}
```

Rules:

- `id` is required, client-generated (same convention as spec 15). It becomes `runId`.
- `packId`, `locale`, `startInputs` are validated by the engine (`validateStartRequest`); host-level errors map to §8.6.
- `generated` defaults to `false`. Generated runs additionally require provider configuration; otherwise `409 GENERATION_UNAVAILABLE` (the client may retry with `generated:false` if the pack allows authored play).
- `seed` is accepted only when `capybee.game.allow-seed=true` (tests/dev); ignored otherwise.
- Response `202`:

```json
{ "runId": "…", "status": "PREPARING", "revision": 0, "retryAfterMs": 1500 }
```

- If the same `id` already exists for this owner: `200`/`202` with the current summary (idempotent). If another run is active: `409 RUN_ALREADY_ACTIVE` with `{ "existingRunId": "…" }`.

### 8.3 Wait-then-poll

Operations that may call the provider (start, and commands that resolve a generated scene) use one pattern:

1. Server begins the work on a worker and **waits up to `syncWaitMs`** (default **8000**, max 20000) for completion.
2. If it finishes: `200` with the full result.
3. If not: `202` with `{ status: "PROCESSING", retryAfterMs }`. The worker keeps going; the client polls `GET run` (start) or `GET command` (commands) using `Retry-After`/`retryAfterMs` (start at 1.5 s, back off to 5 s).
4. **Suspend safety:** `PREPARING` stores the start parameters and `lease_until`. If a poll finds `PREPARING` with an expired lease (machine was suspended or crashed mid-job), the poll **re-claims the lease and restarts `engine.start`** with the same `runId`. Nothing was committed before success, so this is safe. A command stuck `IN_PROGRESS` after lease expiry is treated the same way: it is re-run from the stored (unchanged) snapshot with the same `commandId`.
5. A `PREPARING` run older than `start-ttl` (default 10 min) becomes `FAILED(START_TIMEOUT)`.

### 8.4 Run summary and view envelope

`GET /api/game/runs/{runId}` and the `200` bodies above use:

```json
{
  "runId": "…",
  "status": "ACTIVE",
  "revision": 4,
  "packId": "capybee-pl",
  "packVersion": "1.0.0",
  "locale": "pl",
  "endReason": null,
  "failureCode": null,
  "viewSchema": 1,
  "view": { "blocks": [], "options": [], "input": {}, "status": [], "ended": false, "endReason": null,
            "objectives": [], "outcome": null, "places": null },
  "updatedAt": "2026-10-16T20:11:03Z"
}
```

- `view` is `ViewDto` v1: a direct JSON mapping of the current `com.capybee.engine.api.View` record (field names unchanged).
- `viewSchema` is the **only** hook spec 28 needs: it will introduce `viewSchema: 2` (asset-ID-based hex-board/scene view models) behind the same endpoint, selected by a `?viewSchema=` query parameter with `1` as the default until the client migrates. Spec 27 must not add rendering fields to v1.
- `view` is `null` for `PREPARING` and `FAILED`.
- Generation internals (artifact ids, outcomes, token usage, provider ids) are **not** exposed over HTTP at all (diagnostics are in-process only, §8.7). `GET run` also calls `ensurePrefetch` (§10.1).

### 8.5 Commands

`POST /api/game/runs/{runId}/commands`

```json
{ "id": "e2b7…uuid", "expectedRevision": 3, "command": { "type": "CHOOSE", "optionId": "explore" } }
```

| `type` | Fields | Engine command |
|---|---|---|
| `CHOOSE` | `optionId` | `Command.Choose` |
| `ADVANCE` | – | `Command.Advance` |
| `SUBMIT_TEXT` | `text` (max per pack `maxChars`, hard cap 1000 code points) | `Command.SubmitText` |
| `MOVE` | `placeId` | `Command.Move` |
| `ABANDON` | – | `Command.Abandon` |

`200` result:

```json
{
  "commandId": "e2b7…",
  "outcome": "APPLIED",
  "accepted": true,
  "appliedRevision": 4,
  "revision": 4,
  "status": "ACTIVE",
  "view": { … },
  "viewSchema": 1,
  "events": [ { "id": "echo.offered", "payload": { "kind": "greet" } } ],
  "issues": [ { "severity": "WARNING", "code": "INPUT_REDACTED", "message": "…" } ]
}
```

- `outcome`: `APPLIED` | `REJECTED` (engine returned `accepted=false`; `issues` explain; revision unchanged) | `REPLAYED` (same `commandId` seen before; `view` is the **current** view, `events` are the stored events for that command, `appliedRevision` is the revision it originally produced).
- `issues[].message` is the engine's short, child-safe message; clients map `code` to localized copy (spec 17 tone rules).
- `202` result: `{ "commandId", "status": "PROCESSING", "revision": 3, "retryAfterMs": 1500 }`.

### 8.6 Errors

Scoped `@RestControllerAdvice` for `com.capybee.server.game` only; body `{ "error": { "code": "…", "message": "…", "details": {…} } }`. Messages are generic; no player text, no stack traces, no provider output.

| HTTP | `code` | When | Queue class |
|---|---|---|---|
| 400 | `REQUEST_INVALID` | Malformed JSON, missing fields, unknown command type, bad UUID | terminal |
| 401 | `UNAUTHENTICATED` | No/expired session | pause (existing behavior) |
| 404 | `RUN_NOT_FOUND` | Unknown id or not the owner's | terminal |
| 404 | `GAME_DISABLED` | Feature flag off | terminal |
| 409 | `REVISION_CONFLICT` | `expectedRevision` ≠ current. `details`: `currentRevision` | **stale** (§12) |
| 409 | `RUN_NOT_ACTIVE` | Command on `PREPARING/ENDED/FAILED` run. `details.status` | **stale** |
| 409 | `RUN_ALREADY_ACTIVE` | Second active run. `details.existingRunId` | terminal |
| 409 | `RUN_BUSY` | Lease held by another request; `Retry-After` set | retry |
| 409 | `GENERATION_UNAVAILABLE` | Generated play requested without provider | terminal |
| 413 | `PAYLOAD_TOO_LARGE` | Body > 16 KB | terminal |
| 422 | `COMMAND_ID_REUSED` | Same `id`, different payload | terminal |
| 422 | `START_REJECTED` | Engine start returned error issues. `details.issues` (codes only) | terminal |
| 429 | `RATE_LIMITED` | Per-user request rate (default 60 commands/min) | retry (honor `Retry-After`) |
| 503 | `GENERATION_BUSY` | Worker semaphore saturated | retry |
| 503 | `SERVICE_UNAVAILABLE` | DB/transient host failure | retry |
| 500 | `INTERNAL_ERROR` | Anything else | retry (bounded) |

### 8.7 Diagnostics (in-process only)

`RunSessionService.diagnostics(owner, runId)` tells a developer *how* recent turns were produced. It is **not** exposed over HTTP; the web server keeps `capybee.game.diagnostics-enabled=false`, and the local console enables it for `--debug`. It never contains prompts, responses, story text, player text or secrets:

```json
{
  "runId": "…", "revision": 7,
  "generationCalls": 9, "runCallCeiling": 24,
  "prefetch": { "inFlight": ["meeting"], "ready": ["finale"], "prefetchCalls": 4, "hits": 3, "misses": 1, "wastedCalls": 1 },
  "recentCommands": [
    { "revisionAfter": 7, "latencyMillis": 180, "sceneSource": "PREFETCHED" },
    { "revisionAfter": 6, "latencyMillis": 9400, "sceneSource": "JOINED_PREFETCH" }
  ],
  "stages": [ { "stage": "SCENE", "outcome": "GENERATED", "providerCalls": 1, "elapsedMillis": 4120, "failureCode": null, "inputTokens": 800, "outputTokens": 100 } ],
  "budget": { "profileCallsLeftToday": 31, "globalCallsLeftToday": 1288 }
}
```

`sceneSource` is one of `NONE` (no scene resolution), `PREFETCHED`, `JOINED_PREFETCH`, `INLINE_GENERATED`, `INLINE_REPAIRED`, `FALLBACK`. Per-run `prefetchCalls` counts granted reservations even when the provider returns no usage or throws. `wastedCalls = max(0, prefetchCalls - hits)` is an upper bound because a repaired scene can consume two provider calls for one scene. `generationCalls` comes from the run reservation ledger, and stage details are in memory from the last command only.

---

## 9. Algorithms

### 9.1 Start

1. Validate request shape. Reject if feature disabled.
2. `RunRepository.createPreparing` (one short tx). Stores: `runId`, owner, `packId`, `locale`, `generated`, `seed`, and the **raw `startInputs` JSON including free text** in `pending_start` with `lease_until = now + startLease`.
3. Submit worker job (semaphore-guarded). Return after `syncWaitMs` as in §8.3.
4. Worker: `tryClaim` → `EngineFactory.forOwner(owner)` → `engine.start(new RunRequest(…, generated))`. **No transaction open.**
5. On `StartResult.ok()`: one tx — `saveActive(run, lease, 0, snapshot)`, append any startup events to the outbox, **null `pending_start`**, set status `ACTIVE`.
6. On error issues: `markFailed(START_REJECTED)` (details: issue codes) and null `pending_start`.
7. On unexpected exception: leave `PREPARING` with lease released so the next poll may retry once; after `start-ttl`, `FAILED(START_TIMEOUT)`. Log fixed codes only.

### 9.2 Command

1. Authenticate, load `StoredRun` by `(runId, owner)`; `404` if missing.
2. Compute `payloadMac = HMAC-SHA256(serverSecret, canonicalJson(command))`. Look up `CommandLog`:
   - found `DONE` with equal MAC ⇒ return `REPLAYED` (current view, stored events, no engine work);
   - found with different MAC ⇒ `422 COMMAND_ID_REUSED`;
   - found `IN_PROGRESS` ⇒ continue to wait/poll below.
3. If not `ACTIVE` ⇒ `409 RUN_NOT_ACTIVE`. If `expectedRevision != run.revision` ⇒ `409 REVISION_CONFLICT`.
4. `CommandLog.begin(…)`, then `RunRepository.tryClaim(commandLease)`; failure ⇒ `409 RUN_BUSY`.
5. Worker (no transaction): load prefetched artifacts for the run and merge those whose slot is not already in the snapshot (`RunSnapshot` is immutable; build a new `RunState` with the extra artifacts) → `engine.resume(merged)`. If resume fails **only because of the merge** (`SNAPSHOT_ARTIFACT_VERSION_MISMATCH` caused by a prefetched artifact), drop the prefetch rows and resume the plain snapshot: prefetch is an optimisation and never a failure cause. Any other resume failure ⇒ §6.4. If a needed scene is being prefetched right now (in-flight registry, §10.1), wait for it up to the remaining `syncWait` instead of calling the provider twice. Then `run.apply(command)`; `run.snapshot()`.
6. One tx: if `accepted` and revision changed ⇒ `saveActive(CAS on revision + lease)`, `EventOutbox.append`, `CommandLog.complete`. If `accepted=false` ⇒ `CommandLog.complete(accepted=false)` only. CAS failure ⇒ log fixed code, return `409 REVISION_CONFLICT` (should be unreachable while the lease is held).
7. Status becomes `ENDED` when the resulting view has `ended=true`. In the same transaction, delete `game_run_prefetch` rows whose scenes are now inside the saved snapshot and record `scene_source` on the command.
8. Return as §8.5, or `202` if `syncWait` elapsed.
9. After the response is committed and while the run is `ACTIVE`, call `ensurePrefetch` (§10.1). Never on the request's critical path.

### 9.3 Resume on every command

Rebuilding `Run` from the snapshot each time is intentional (F3). It also exercises the 26.1 artifact-validation path on every request, so corruption surfaces immediately instead of at the end of a session.

---

## 10. Generation metering and the ledger

- Scopes: `profile:<profileId>` and `global` per day, plus a **lifetime per-run counter** (`game_runs.generation_calls`). Day = `LocalDate` in `capybee.game.day-zone`.
- Caps (config, **initial values to be tuned once real cost data exists**): `capybee.game.limits.profile-calls-per-day` (default 40), `global-calls-per-day` (default 1500), plus a `capybee.game.generation-enabled` kill switch that makes every start generation-free (authored fallback) without a deploy.
- Atomic reserve (PostgreSQL):

```sql
INSERT INTO game_generation_usage (scope, day, calls, input_tokens, output_tokens)
VALUES (:scope, :day, 1, 0, 0)
ON CONFLICT (scope, day) DO UPDATE SET calls = game_generation_usage.calls + 1
WHERE game_generation_usage.calls < :cap
RETURNING calls;
```

  Zero rows ⇒ cap reached ⇒ `TextGenerationException(RATE_LIMITED)`. Reserve the profile scope first, then global; if global fails, release the profile reservation (decrement) in the same transaction.
- `recordUsage` adds token counts after the call. Token totals are for cost monitoring; **caps are on calls** in v1.
- Behavior at the cap is graceful by construction (F5): the stage uses its validated fallback. Because fallbacks are persisted artifacts, a scene that fell back at the cap **stays** fallback content for that run; this is intentional (continuity over regeneration).
- **Per-run ceiling (corrects rev. 1).** The pipeline's own `totalCallsPerRun` / `callsPerStage` reset every time the host rebuilds a `Run` (F16), so they only bound a single command or prefetch job. The host enforces the lifetime ceiling with an atomic `UPDATE game_runs SET generation_calls = generation_calls + 1 WHERE id = :id AND generation_calls < :ceiling RETURNING generation_calls`, default `capybee.game.limits.run-calls = 24` (initial value; covers the initial stages, visited scenes, repairs and prefetch). Reservation order: run → profile → global; on a later failure roll back the earlier increments in the same transaction. `repairAttempts` per stage is still enforced by the pipeline per call sequence.
- The in-memory ledger used by the CLI/tests is **not** a production guarantee; the SQL ledger is the only production implementation.

---

## 10.1 Scene prefetch

**Idea:** while the child reads scene N, generate the scenes they can reach next, so choosing an option usually finds the next scene already waiting.

**Why it is safe (F14, F15):** scene generation does not see player state or the chosen path, and the engine accepts scene artifacts for slots that are not yet visited. A prefetched scene is therefore valid on every path and can be embedded in the snapshot later.

**Scope:** depth 1, i.e. `Run.prefetchCandidates()` of the *current* scene (options, `next`, branches). If several options lead to the same scene it is generated once.

**Triggers:** (a) right after `start` becomes `ACTIVE`; (b) after every accepted command that leaves the run `ACTIVE`; (c) `GET /runs/{id}` calls `ensurePrefetch`, which repairs the case where the machine was suspended before the job finished.

**Job (never holds a lease or a transaction while calling the provider):**

1. Skip if prefetch is disabled, the run is not `ACTIVE`, or the run has no candidates.
2. Read the snapshot (short read) and `resume` into a throwaway `Run` (with prefetched rows merged, so candidates already stored are not repeated).
3. Take candidates in the order the options appear, minus stored rows and in-flight keys, up to `prefetch.max-fanout` (default 3).
4. For each: reserve a call with `Priority.PREFETCH`, then `run.prefetch(sceneId)`.
5. If the resulting artifact has `outcome = FALLBACK`, **discard it** (a transient error must not permanently replace the story with fallback text; the demand path will try again when the player gets there). Otherwise `insertIfAbsent` into `game_run_prefetch`.
6. Throw the throwaway `Run` away. The job never writes `game_runs`.

**In-flight registry:** an in-memory map `(runId, sceneId) → future` wraps the metered generator. A command that needs a scene being prefetched joins that future; the join is counted as neither a second reservation nor a second provider call. Commands for unrelated scenes never wait. It is per-process; if the app is ever scaled to several machines, the worst case is one duplicate provider call, still bounded by the ledger. A narrow race between provider return and prefetch-row insertion can also produce one extra counted call.

**Budget protection:** prefetch is refused when `profileCallsLeftToday <= prefetch.profile-reserve` (default 8), when `globalCallsLeftToday <= prefetch.global-reserve` (default 100), or when the run has `<= prefetch.run-reserve` (default 4) calls left before `run-calls`. Reserves larger than a cap are allowed and effectively disable prefetch at that scope. Prefetch also runs under its own concurrency limit (`prefetch.max-concurrent`, default 1), independent of player generation slots. Peak provider concurrency is `max-concurrent-generation + prefetch.max-concurrent` (default 3).

**Cost model:** a run costs roughly (visited generated scenes + sibling scenes that were prefetched but never visited). Wasted calls are counted in `game_generation_usage.prefetch_calls` and reported by diagnostics, so the cost of Option B is visible from the first test session.

**Best-effort by design:** Fly may suspend the machine after the response is sent; the job can die. The run still works (the scene is generated inline as in rev. 1). If the measured `PREFETCHED` hit rate is low in production, the ops response is `min_machines_running = 1` during active hours, not a code change.

**Config:** `capybee.game.prefetch.enabled` (default `true` when generation is enabled), `max-fanout`, `max-concurrent`, `profile-reserve`, `global-reserve`, `run-reserve`.

---

## 11. Events and the seam to spec 29

- Engine `Event`s (`id` + string payload) from `Run.apply` and `startupEvents()` are written to `game_run_events` in the same transaction as the snapshot save. `seq` is monotonic per run.
- `GET …/events?after=&limit=` returns them for the owner; consumers are idempotent by `(runId, seq)`.
- Spec 29 will register host-side **event handlers** (e.g. `echo.offered` → create a Mission suggestion; `reward.cell` → grant a honeycomb cell). Contract for handlers, owned by spec 29 but constrained here:
  - They run **after** the save transaction, are idempotent by `(runId, seq)`, and must not call the engine.
  - Reward grants (honeycomb cells) are enforced **server-side** with the server day (`day-zone`), not the device clock (F12). Spec 29 decides whether the existing client-side mini-game cap migrates to the same server rule.
  - Real-life echo → Missions reuses the existing mission completion endpoint and the offline queue unchanged (mission completion is a normal spec-15 write).
- Spec 27 ships the outbox and the endpoint but **no handlers**.

---

## 12. Offline write queue integration (spec 15)

### 12.1 Rules

1. **Starts and commands are queued locally first**, then sent. This protects against tab close, app suspend and flaky networks while the Fly machine wakes (cold start can take 10–20 s+).
2. **One pending game command per run.** The UI disables further choices until a result arrives, so there is never an ordering problem between queued commands.
3. **Cold start / warm-up:** the existing `/api/auth-status` ping already wakes the machine; game screens additionally call `GET /api/game/runs/current` early so the machine is warm before the first tap.
4. **UI language while waiting** follows spec 15 part F and spec 17: a quiet "CapyBee is thinking" animation, no spinner wording, no error language, no parent-facing sync failures.
5. **Never offline-play.** If the device is offline, the child can keep reading the current scene (last known view is cached in memory/IndexedDB) but choices are queued and resolve on reconnect; if the run has moved on (another device), the stale outcome below applies and the queued command is dropped silently.

### 12.2 Required changes to `syncEngine.ts` / `queueStore.ts`

The current queue (F10) cannot carry game commands safely. Add, without changing behavior for the four existing action types:

```ts
export type QueueActionType =
  | 'checkIn' | 'missionCompletion' | 'friendship' | 'memory' /* existing */
  | 'gameStart' | 'gameCommand';

export type SendOutcome =
  | { kind: 'done'; status: number; body?: unknown }   // 2xx → remove, deliver body
  | { kind: 'stale' }                                  // 409 REVISION_CONFLICT | RUN_NOT_ACTIVE → remove, trigger refetch
  | { kind: 'terminal'; status: number }               // other 4xx except 408/429 → remove, no retry
  | { kind: 'retry'; afterMs?: number }                // network error, 408, 429, 5xx, 409 RUN_BUSY, 202 → keep
  | { kind: 'auth' };                                  // 401 → keep, stop flushing until signed in
```

- Add `onSynced(listener)` so a screen receives `{ action, body }` for `gameStart`/`gameCommand` (existing types ignore it). The queue must **stop discarding response bodies** for these types.
- Replace "409 = success" **for game types only** with the classification above (409 `RUN_BUSY` is `retry`; `REVISION_CONFLICT`/`RUN_NOT_ACTIVE` are `stale`).
- **Do not sleep inside the flush loop for terminal outcomes**; move backoff to a per-item `notBefore` timestamp so one failing item cannot delay the others (fixes F10 head-of-line blocking for all types; verify existing tests still pass).
- `202 PROCESSING` is `retry` with `afterMs = retryAfterMs`; polling `GET command`/`GET run` is a separate client hook, not a queue item.
- Command body is `{ id: clientId, expectedRevision, command }`; `clientId` **is** the `commandId`, matching the existing `{ id: clientId, …payload }` convention. Because the queue spreads `payload`, store `{ expectedRevision, command }` as the payload and set `path` to `/api/game/runs/{runId}/commands`.
- `MAX_ATTEMPTS` for game commands is raised to cover a full cold start (suggest 12 with the existing backoff cap), after which the UI falls back to refetching the run and showing the current scene.

### 12.3 Stale outcome behavior

On `stale`: drop the queued command, `GET /runs/{id}`, render the returned view. The child sees the current scene; nothing is announced as an error.

---

## 13. Persistence (Flyway `V12__game_runs.sql`)

```sql
CREATE TABLE game_runs (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL REFERENCES users(id),
    pack_id       VARCHAR(64)  NOT NULL,
    pack_version  VARCHAR(32)  NOT NULL,
    locale        VARCHAR(16)  NOT NULL,
    status        VARCHAR(16)  NOT NULL,           -- PREPARING | ACTIVE | ENDED | FAILED
    end_reason    VARCHAR(16),
    failure_code  VARCHAR(48),
    revision      BIGINT       NOT NULL DEFAULT 0,
    generation_calls INTEGER   NOT NULL DEFAULT 0,   -- lifetime provider calls for this run (F16)
    snapshot      JSONB,                            -- SnapshotCodec JSON; NULL while PREPARING
    pending_start JSONB,                            -- start params incl. free text; NULLed on ACTIVE/FAILED/TTL
    lease_token   UUID,
    lease_until   TIMESTAMPTZ,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    ended_at      TIMESTAMPTZ,
    CONSTRAINT chk_game_runs_snapshot_size CHECK (snapshot IS NULL OR pg_column_size(snapshot) <= 262144)
);
CREATE UNIQUE INDEX uq_game_runs_one_open_per_user
    ON game_runs (user_id) WHERE status IN ('PREPARING','ACTIVE');
CREATE INDEX idx_game_runs_user_updated ON game_runs (user_id, updated_at DESC);

CREATE TABLE game_run_commands (
    run_id          UUID        NOT NULL REFERENCES game_runs(id) ON DELETE CASCADE,
    command_id      UUID        NOT NULL,
    payload_mac     BYTEA       NOT NULL,
    state           VARCHAR(12) NOT NULL,          -- IN_PROGRESS | DONE | FAILED
    accepted        BOOLEAN,
    revision_before BIGINT      NOT NULL,
    revision_after  BIGINT,
    scene_source    VARCHAR(20),                   -- NONE | PREFETCHED | INLINE_GENERATED | INLINE_REPAIRED | FALLBACK
    latency_ms      INTEGER,
    failure_code    VARCHAR(48),
    created_at      TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, command_id)
);

CREATE TABLE game_run_events (
    run_id     UUID        NOT NULL REFERENCES game_runs(id) ON DELETE CASCADE,
    seq        BIGINT      NOT NULL,
    command_id UUID,
    revision   BIGINT      NOT NULL,
    event_id   VARCHAR(96) NOT NULL,
    payload    JSONB       NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, seq)
);

CREATE TABLE game_run_prefetch (
    run_id            UUID        NOT NULL REFERENCES game_runs(id) ON DELETE CASCADE,
    scene_id          VARCHAR(96) NOT NULL,
    based_on_revision BIGINT      NOT NULL,
    artifact          JSONB       NOT NULL,           -- validated GenerationArtifact (never FALLBACK)
    created_at        TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (run_id, scene_id)
);

CREATE TABLE game_generation_usage (
    scope         VARCHAR(80) NOT NULL,            -- 'global' | 'profile:<uuid>'
    day           DATE        NOT NULL,
    calls         INTEGER     NOT NULL DEFAULT 0,
    prefetch_calls INTEGER    NOT NULL DEFAULT 0,   -- subset of calls made speculatively
    input_tokens  BIGINT      NOT NULL DEFAULT 0,
    output_tokens BIGINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (scope, day)
);
```

**Retention** (lazy, because a scale-to-zero machine cannot rely on `@Scheduled`): on `POST /runs` and `GET /runs/current`, call `purgeExpired(now)` which (a) deletes command-log rows older than 7 days, (b) deletes `ENDED`/`FAILED` runs older than 30 days, (c) nulls `pending_start` older than `start-ttl`, (d) deletes `game_run_prefetch` rows older than 24 hours. Parents can delete a run at any time via `DELETE`. `game_generation_usage` keeps 90 days.

Free text: persisted only in `pending_start` during `PREPARING`; never in `snapshot`, the command log (MAC only), events, or logs.

---

## 14. Security, privacy and operations

- **Authorization:** all queries are `WHERE id = ? AND user_id = ?`; mismatches are `404`.
- **CSRF** is currently disabled app-wide; the game API relies on the session cookie and `Content-Type: application/json` only. Do not widen CORS. (Revisit globally; out of scope here.)
- **Logging:** log only `runId`, `commandId`, status, fixed error codes, revision, durations and the engine's `GenerationExecutionSummary` (stage, outcome, calls, timing, usage, codes). Disable request-body logging for `/api/game/**`. No snapshots, views, prompts, responses, free text or API keys, ever (spec 25 privacy rules; add a log-capture test, which 25 reported as not confirmed).
- **Provider secrets:** `GENERATION_MODEL_ID` and `ANTHROPIC_API_KEY` from environment only (as in the CLI). No default model.
- **Rate limits:** per-user token bucket (default 60 commands/min, 10 starts/hour) in memory; acceptable for a single-machine deployment, documented as such.
- **Memory/threads:** `maxConcurrentGeneration = 2` (1 GB VM). Engine work uses virtual threads; DB access uses short-lived connections only.
- **Configuration (`application.yml`, `capybee.game.*`):** `enabled`, `generation-enabled`, `day-zone`, `sync-wait-ms`, `command-lease`, `start-lease`, `start-ttl`, `max-concurrent-generation`, `limits.*` (incl. `run-calls`), `prefetch.*` (§10.1), `diagnostics-enabled`, `allow-seed`, `hmac-secret` (env `GAME_COMMAND_HMAC_SECRET`).
- **Build and deploy changes (required):**
  - `app/server/pom.xml` depends on `com.capybee:engine-core`, `engine-session` and, if generated play is enabled, `engine-adapter-anthropic` (artifact ids as in the POMs, version `0.1.0-SNAPSHOT`).
  - Packs are loaded at startup from the classpath/`ClasspathPackSource`; the registry is immutable. Document which packs ship in the image.
  - `Dockerfile` server-build stage must copy `app/pom.xml` and `app/engine/` and build with `mvn -B -f app/pom.xml -DskipTests -pl server -am package` (today it copies only `app/server`).
  - CI: keep the standalone engine job; make the server job build the reactor so the dependency resolves. Mind the `engine-core` Jacoco 70% per-package rule for new packages.

---

## 14.1 Local console (CLI) on a PC

**Scope:** the console runs on your own computer only. It never connects to Fly or any server and needs no sign-in, database or network (except to the LLM provider when you choose real generation).

**How it works:** the console calls `RunSessionService` in-process with the public in-memory adapters (or `LocalSessionStore` with `--store-dir`). It therefore follows the same lifecycle rules as the web app: run ids, revisions, idempotent commands, metering, ledger caps and prefetch. Only the storage differs (memory/files instead of Postgres).

The existing authored/`--generated` console path (`ConsoleApp` using `Engine` directly, `--save/--load`) stays unchanged and provider-free by default (26.1 guarantee). Session mode is new and additive.

### Flags (new; existing flags unchanged)

| Flag | Meaning |
|---|---|
| `--session` | Play through `RunSessionService`. Combines with `--pack-dir`, `--locale`, `--input`, `--seed`, `--generated`, `--script`. |
| `--store-dir <dir>` | Keep runs, command log and prefetched scenes in files so a game survives closing the console. Without it everything is in memory. |
| `--run <id>` | Resume that run. Without it, start a new run, or resume the current one if it exists and `--new` is not given. |
| `--debug` | After each turn print one diagnostics line (§8.7): revision, latency, `sceneSource`, prefetch ready/in-flight, calls used/ceiling, budget left. |
| `--prefetch on\|off` | Turn prefetch on or off to compare how the game feels. Default on when `--generated`. |
| `--calls-per-day <n>`, `--run-calls <n>` | Lower the budget caps locally to see what happens at the cap (fallback scenes) without spending much. |

Real generation uses `GENERATION_MODEL_ID` and `ANTHROPIC_API_KEY` from the environment, exactly as `--generated` does today. Tests use the deterministic fake provider.

### In-session commands

A number or option id chooses; `/text <words>` submits free text; `/move <placeId>`; `/abandon`; `/status` (objectives, status items, places); `/events` (domain events); `/debug on|off`; `/quit` (leaves the run `ACTIVE`; resume later with `--run`).

When a command takes longer than the wait limit, the console behaves like the web client: it prints one quiet line ("CapyBee is thinking…") and polls `commandStatus` with the same `commandId`, printing a dot per poll. It shows the stable error `code` plus a short hint, never a stack trace. Rendering reuses `ConsoleRenderer` on the run's `View`.

`--script <file>` works in session mode and is the basis of golden tests with a deterministic fake provider, including prefetch hits and misses.

### Keeping the web client in sync

DTOs in `engine-session` (`wire`) are the contract the HTTP controllers return. TypeScript API types in `app/ui` mirror them. Golden JSON files in `app/engine/session/src/test/resources/wire/` are the single source of truth: Java tests round-trip them through `SessionJson`; UI tests read the same files (relative path) and type-check them. A DTO change that breaks either side fails CI.

### Service vs HTTP conformance

One scripted scenario set (start → choices → free text → move → abandon; stale revision; replay; rejected command; cap reached; prefetch hit) is run twice in `app/server` tests: directly against `RunSessionService`, and through the HTTP controllers (MockMvc) with the same fake provider and in-memory adapters. Results (views, revisions, events, error codes) must be identical, so behavior seen in the console is the behavior the web app will get.

---

## 15. Required tests

**`engine-session` (no Spring, no network, in-memory fakes + deterministic fake `TextGenerator`):**

1. Start → ACTIVE with revision 0; start of an invalid request → `FAILED/START_REJECTED`.
2. Command applies once; replay with same `commandId`/payload returns `REPLAYED` with the **current** view and stored events; different payload → `COMMAND_ID_REUSED`.
3. `expectedRevision` mismatch → `REVISION_CONFLICT`; command on non-ACTIVE → `RUN_NOT_ACTIVE`.
4. Invariant for **all** command types: `accepted=false` ⇒ snapshot step unchanged (include `SUBMIT_TEXT` paths) (F2).
5. Concurrency: two simultaneous commands on the same run ⇒ exactly one applies, the other gets `RUN_BUSY`/replay; no lost update.
6. Lease expiry mid-command ⇒ next request re-runs the command from the unchanged snapshot; result equals a single application.
7. `PREPARING` with expired lease is restarted by a poll and completes; `pending_start` is nulled afterwards.
8. Unrecoverable resume (corrupt/missing artifact, pack-major mismatch) ⇒ `FAILED/RUN_UNRECOVERABLE`, no regeneration, no provider call.
9. Metering: cap reached ⇒ stage falls back, run still playable, `GenerationExecutionSummary` shows fallback; reservations are atomic under concurrent callers; global failure releases the profile reservation.
10. A run that spans several lazily generated scenes survives repeated resume with identical artifacts (reuse 26.1 continuity fixtures).
11. Log-capture test: no player text, prompts, responses, snapshots or secrets in captured logs (including failure paths).
12. One active run per owner; second `start` ⇒ `RUN_ALREADY_ACTIVE`; a `FAILED` run does not block a new start.

**`app/server` (Spring slice + Testcontainers/Postgres if already used, else H2 only for non-JSONB logic):**

13. Auth: unauthenticated ⇒ 401; other user's run ⇒ 404.
14. Ledger SQL: concurrent reserves never exceed the cap; day rollover uses `day-zone`.
15. Flyway `V12` migrates cleanly on top of `V11`; unique partial index enforces one open run per user.
16. Error advice returns stable codes and never leaks messages from exceptions.
17. Wait-then-poll: a slow fake generator yields `202` then `200` on poll; the DB connection pool is not exhausted (pool size 2 in the test) while three slow commands run.

**`app/ui`:**

18. `syncEngine`: classification table (done/stale/terminal/retry/auth); a terminal 4xx does not delay later items; existing four action types unchanged; `onSynced` delivers bodies.
19. Replay safety: a command sent twice (simulated dropped response) results in one state change.
20. Stale outcome refetches and renders the current view without any error UI.

**Prefetch (engine-core and `engine-session`):**

21. `prefetchCandidates()` lists exactly the uncached generated scenes reachable in one step; empty for authored/ended runs; converging options yield one candidate.
22. `prefetch(sceneId)` changes no step/state/view; the artifact is reused with **zero** provider calls when the player enters that scene (fake provider call counter).
23. Merging prefetched artifacts into a snapshot resumes cleanly; a bad/mismatched prefetched artifact is dropped and the run still resumes from the plain snapshot.
24. A `FALLBACK` prefetch result is not stored; the demand path then generates normally.
25. A command never waits for or conflicts with a running prefetch job (no lease); a command needing an in-flight scene reuses that future (one provider call total).
26. Budget: prefetch is refused at the reserves; run-lifetime ceiling holds across many commands and prefetch jobs (regression test for F16); the demand path can still use the last calls.
27. After a simulated suspend (job lost), `GET run` → `ensurePrefetch` regenerates the missing prefetch; `scene_source` shows `PREFETCHED` vs `INLINE_GENERATED` correctly.

**Local console and conformance:**

28. Service vs HTTP conformance (above) green.
29. Console golden tests in `--session` mode (authored pack and generated pack with fake provider), including `/text`, `/move`, `/abandon`, `--debug`, and resuming with `--run` after a restart using `--store-dir`.
30. Console needs no network or server with the fake provider; with provider env missing, `--generated` reports the fixed "not configured" code and plays authored fallbacks (as today).
31. Slow command: the console prints the thinking line and polls with the same `commandId`; killing the console mid-command and restarting with `--store-dir` leaves the run consistent (the command is applied exactly once or not at all).
32. Cap rehearsal: `--calls-per-day 1` / `--run-calls` produce fallback scenes, the run still completes, `--debug` shows `FALLBACK`.
33. Existing `ConsoleAppTest`, `ConsoleGoldenTest` and `--generated` paths are unchanged and green; wire golden files round-trip in Java and type-check in UI tests.

**Optional, opt-in (never in default build):** extend the existing live-provider smoke path to run one start + three commands through the HTTP layer against a local server.

---

## 16. Implementation sequence

0. **Docs hygiene:** roadmap reconciliation notes (§2); fix `app/engine/README.md` artifact coordinates; remove or `.gitignore` `generated-smoke.txt`, `snapshot.json`, `tmp-runcheck.jsh`; record that 26.1 is implemented.
1. Create `engine-session` module with ports, in-memory fakes, `RunSessionService`, `MeteredTextGenerator`; tests 1–12.
2. Wire `engine-session` into the reactor and CI; keep `mvn -f app/engine/pom.xml clean verify` green.
2a. Engine-core additions `Run.prefetchCandidates()` / `Run.prefetch()` (§7.4) with tests 21–22; then host-side prefetch (job, in-flight registry, `PrefetchStore`, reserves, `ensurePrefetch`) with tests 23–27.
2b. `wire` DTOs + `SessionJson`, public in-memory adapters, `LocalSessionStore`; console `--session` mode (flags in §14.1); tests 28–33. The console can be used for manual testing as soon as 2b is done, before any web work.
3. Flyway `V12`, JDBC adapters (`RunRepository`, `CommandLog`, `GenerationLedger`, `EventOutbox`), `@RestControllerAdvice`, controllers behind `capybee.game.enabled`; tests 13–17.
4. Dockerfile/CI changes (§14); verify the image boots with the feature flag off and on.
5. Client: API types + `syncEngine`/`queueStore` changes (§12) and tests 18–20, **without** game screens (those are spec 28). Add a minimal dev-only debug page or Postman collection to drive the API by hand.
6. Manual end-to-end with a fake provider, then one opt-in live run; check Fly cold-start behavior (suspend mid-start, then poll).

## 17. Definition of done

- All endpoints in §8.1 work behind the feature flag; spec-15 queue carries `gameStart`/`gameCommand` with the new classification.
- Replaying any command any number of times changes state at most once.
- No code path holds a DB connection during `TextGenerator.generate`.
- Hitting the daily cap produces fallback content, not errors.
- A machine suspended mid-start recovers on the next poll without duplicate runs.
- Logs contain only the fixed fields in §14 (log-capture test green).
- Existing authored/CLI behavior, existing four queue action types and all current tests are unchanged and green.
- With a fake provider, a multi-scene generated run shows `PREFETCHED` for the second and later scenes in diagnostics; with the provider failing, the run still completes (fallback) and no fallback is ever stored by prefetch.
- The lifetime per-run call ceiling holds across any number of commands and prefetch jobs.
- The console plays a full run locally with no server, and the conformance test shows the HTTP API and the in-process service give identical results.
- Docs updated: README roadmap note, `03-api-contract.md` (new endpoints), `02-data-model.md` (new tables).

## 18. Open questions (decide before step 3)

1. **Latency — decided: prefetch (Option B, §10.1).** Still open: initial `max-fanout`, reserves and `run-calls`; and whether the CapyBee pack should *also* shorten the SCENE-stage timeout as a safety net for the first scene after start and for prefetch misses.
2. **Initial caps** (40 calls/profile/day, 1500 global) are placeholders; set them from the first week of real token usage.
3. **Parent visibility.** Should parents see run history (like mission history, SCR-07)? Not in v1; the API leaves room (`GET /runs?status=ENDED`).
4. **Prefetch vs machine suspend.** Prefetch is best-effort (§10.1). Decide after measurement whether to keep one machine awake during play hours.
5. **Day boundary vs device time.** Server day (`Europe/Warsaw`) can differ from a child's device day when travelling; accepted for v1.

## 19. Traceability

- US-004/005/006, US-009/010 (check-in → theme → mission loop): spec 29 uses this API; US-016/017 (language, tone): `locale` and issue-code localization; US-018/019 (privacy, minimal data): §13–14; US-020 (phone-first): wait-then-poll and queue behavior.
- Spec 15: §12. Spec 25: §14 logging/privacy. Spec 26/26.1: §6.4, §10, D10; handoff items F6 resolved here.
- Prefetch and console/CLI clients (rev. 2): §7.4, §10.1, §14.1; decisions D13–D14.
- Hands off to spec 28 via `viewSchema`; to spec 29 via the event outbox and server-side day/caps.
