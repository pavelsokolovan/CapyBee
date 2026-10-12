# 24 — Engine Rules and State

## Purpose

Extend the existing scene runtime with pack-defined state and deterministic rules. This spec owns counters, flags, closed-value sets, effects, conditions, objectives, outcomes, event emission, snapshot migration, and the `RunStore` port plus an in-memory adapter.

A pack without `rules.json` must continue to work exactly as a stateless authored-scene pack does today. Everything remains universal, headless, deterministic, synchronous, locale-agnostic, and playable through the console. AI does not define or execute rules; later generation specs may propose bounded values that this runtime validates.

## Ground truth reviewed

Repository source was inspected through the public `main` branch. A local `git clone` could not complete because the execution environment could not resolve `github.com`; the findings below are therefore based on the reachable source files, not a local checkout.

| Existing source | Observed contract / consequence for this spec |
|---|---|
| `engine/README.md`, `engine/pom.xml` | `engine/` exists as a standalone Maven SDK with `core` and `cli`; Java release is 25; it has no Spring Boot parent. Keep the SDK boundary and current module layout. |
| `engine/core/.../api/Engine.java` | `Engine.sectionValidators()` currently returns only `SceneSectionValidator`; add the rules validator without dropping the scene validator. |
| `engine/core/.../api/RunState.java` | Current state has only `sceneId`, `visited`, `ended`, and `endReason`; add optional state maps and outcome. |
| `engine/core/.../api/RunSnapshot.java` and `api/json/SnapshotCodec` | Snapshot version 1 already stores a `RunState`; extend `state` additively and keep old snapshots readable. |
| `engine/core/.../content/Scene.java` | Current scene model contains only `id`, `start`, `blocks`, `options`, `next`, and `end`; add the scene-rule fields below. |
| `engine/core/.../content/json/SceneSectionValidator.java` | Scene validation is already registered through `SectionValidator` and uses `PackValidationContext` / `ReferenceIndex`; extend this validation rather than creating a parallel scene loader. |
| `engine/core/.../api/View.java` and `api/Option.java` | `View` currently has blocks, options, input, status, ended, and end reason. `Option` already has `enabled` and `hint`; the runtime must populate them meaningfully. |
| `engine/cli/.../ConsoleRenderer.java` | Current renderer does not show disabled-option state or hints and does not render objectives/outcomes. Add those outputs while preserving current block text and console chrome. |
| `engine/cli/.../ConsoleApp.java` | The inspected `run` path does not implement the spec-23 `--script` contract, and its current `readLine()` reads an arbitrary byte chunk rather than one line. The scripted golden-test requirement depends on correcting this existing mismatch in `engine/cli`. |
| `docs/specifications/22-engine-core-and-pack-format-instruction.md` and `23-engine-api-and-authored-scene-runtime-instruction.md` | Their current series map puts places in spec 24, and spec 23 describes a future `places.json` addition to the dungeon fixture. Under the confirmed split, places move to spec 24.1; those older documents need a follow-up compatibility edit. |

## Constraints

- Preserve the universal, headless, provider-agnostic engine boundary from spec 22. No theme, genre, language, screen, or product-specific terms in engine code, ids, or tests.
- Changes are limited to `engine/`, `.github/`, and `docs/`. Do not change `app/server`, its schema, endpoints, or deployment.
- Core has no Spring, JPA, Hibernate, console I/O, network calls, or vendor/model names. Jackson 3 stays inside `json` packages and `adapter`.
- Time and ids remain injected through the existing ports. Spec 24 introduces no randomness.
- Domain values are immutable and defensive-copied. Content validation collects all issues; malformed pack content must not throw an unchecked exception out of `tryLoad`.
- Closed-world rules: only declared counter/flag/set/objective/outcome/event ids may be referenced; all numeric state remains bounded; runtime never accepts an effect or condition type outside the lists below.
- Preserve `fixture-minimal` and the existing public API where practical. Add compatibility constructors when records gain components, and update all callers/tests in this spec.
- **Decision: split places out.** Spec 24 is rules and state only. Do not add `places.json`, map state, `GO_TO`, or a move command. Places will be covered by spec 24.1; this does not renumber the planned specs 25–31. Specs 22 and 23 still need follow-up edits to align their descriptions with this split.

## 1. State model

All state is per `Run`; packs remain immutable static data. State definitions are declared in optional `rules.json` and loaded once with the pack.

| Kind | Definition fields | Runtime value | Defaults |
|---|---|---|---|
| Counter | `id`, `labelKey`, `min`, `max`, `initial`, `display`, optional `maxDelta` | 32-bit integer constrained to `[min, max]` | `initial` is required; it must be in range. |
| Flag | `id`, optional `initial` | Boolean | `false` |
| Set | `id`, closed `values` list, optional `initial` list | Set of ids selected only from `values` | Empty set |
| Objective | `id`, `labelKey`, `completeWhen`, optional `onComplete` effects | `ACTIVE` or `DONE` | `ACTIVE`; once `DONE`, never becomes active again. |
| Outcome | `id` | Optional terminal result label/id | `null` until set. It is not an `EndReason`. |

State-definition and set-member IDs use the existing lowercase hyphenated id convention: `[a-z][a-z0-9]*(?:-[a-z0-9]+)*`, with a maximum length of 48 characters. Definition ids must be unique within their kind. The same spelling may be used in different kinds because references are type-specific.

Limits for v1: at most 32 counters, 32 flags, 16 sets, 32 values per set, 8 objectives, 16 outcomes, 32 effects in one effect list, 16 children in a composite condition, and 64 condition nodes in one tree. Counter bounds and values are signed 32-bit integers. Use `long` for intermediate addition before clamping to avoid overflow. A set value list and its initial members must contain no duplicates.

A counter with `display: true` produces a `StatusItem` in definition order. Flags and sets are not displayed automatically. Objectives are exposed separately in definition order. Packs without `rules.json` have empty state maps and an empty objective list.

## 2. `rules.json` format and validation

`rules.json` is optional. If present, it must be indexed in `pack.json`'s `files` list and use this schema:

```json
{
  "schemaVersion": 1,
  "counters": [
    {
      "id": "health",
      "labelKey": "status.health",
      "min": 0,
      "max": 10,
      "initial": 10,
      "display": true,
      "maxDelta": 3
    }
  ],
  "flags": [
    { "id": "has-key", "initial": false }
  ],
  "sets": [
    { "id": "inventory", "values": ["key", "token"], "initial": [] }
  ],
  "objectives": [
    {
      "id": "escape",
      "labelKey": "objective.escape",
      "completeWhen": { "type": "VISITED_SCENE", "scene": "exit" },
      "onComplete": []
    }
  ],
  "outcomes": [
    { "id": "escaped" }
  ]
}
```

`counters`, `flags`, `sets`, `objectives`, and `outcomes` are optional arrays and default to empty. `schemaVersion` is required and must be `1`. `rules.json` does not declare events; event definitions remain in `pack.json` as specified in spec 22. The `limits` key is reserved for a later spec: in spec 24 it may be absent or `{}` only; any non-empty value is `RULES_FIELD_INVALID`, because the engine must not imply that an unenforced limit is active.

| Field | Validation |
|---|---|
| Counter `min`, `max`, `initial` | Integers in the signed 32-bit range; `min < max`; `initial` lies inside the range. |
| Counter `maxDelta` | Optional positive integer no greater than `max - min`. If present, every authored `COUNTER_ADD` for this counter must have `abs(delta) <= maxDelta`; perform the absolute-value comparison in a wider integer type to avoid overflow for the minimum signed 32-bit value. |
| Counter `display` | Required boolean. `labelKey` must exist in every pack locale. |
| Flag `initial` | Optional boolean; defaults to `false`. |
| Set `values` | Required, 1–32 unique valid ids. `initial`, when present, must be a duplicate-free list of members of `values`; defaults to `[]`. |
| Objective | `labelKey` exists in every locale; `completeWhen` is a valid condition tree; `onComplete` defaults to `[]`. At most 8 objectives. |
| Outcome | Valid unique id. At most 16 outcomes. |
| Unknown fields | Use the existing `PACK_UNKNOWN_FIELD` warning convention unless a field is explicitly reserved above. Invalid known fields use `RULES_FIELD_INVALID`. |

The rules validator is a pure `SectionValidator` in `com.capybee.engine.rules.json`. Register it in `Engine.sectionValidators()` alongside the existing `SceneSectionValidator`; do not replace the scene validator. It runs only after pack built-in validation has passed, as the existing loader already guarantees.

Cross-file definitions/references use the shared `ReferenceIndex`. Define `counter`, `flag`, `set`, `setValue:<set-id>`, `objective`, and `outcome` ids. Use the existing `stringKey:<locale>` kind for labels. Scene ids and event ids continue to use the existing scene/manifest definitions. Reuse `PACK_DUPLICATE_ID` and `PACK_DANGLING_REF` for ordinary duplicate and missing references; use the more specific codes below for a malformed definition or invalid payload.

### New issue codes

| Code | Severity | When |
|---|---|---|
| `RULES_FIELD_INVALID` | ERROR | Invalid `rules.json` schema, known field, declaration, or reserved `limits` content. |
| `EFFECT_INVALID` | ERROR | Unknown effect type, malformed shape, invalid literal, or out-of-policy authored effect. |
| `EFFECT_CLAMPED` | WARNING | A future generated effect asks to move a counter outside its range and the runtime clamps it. Never use this warning to excuse invalid authored content. |
| `CONDITION_INVALID` | ERROR | Unsupported or malformed condition, or a condition that violates depth/node/list bounds. |
| `OBJECTIVE_INVALID` | ERROR | Objective definition is malformed or its completion condition is invalid. |
| `OBJECTIVE_EVALUATION_LIMIT` | ERROR | Objective fixed-point evaluation exceeds its hard cap. |
| `EVENT_UNDECLARED` | ERROR | An effect names an event not declared in `pack.json`. |
| `EVENT_PAYLOAD_INVALID` | ERROR | Effect payload does not exactly match the event's declared field names and types. |
| `EVENT_NEVER_EMITTED` | WARNING | An event is declared but no authored effect emits it. |
| `SNAPSHOT_STATE_PRUNED` | WARNING | Same-major migration removes obsolete state ids, set members, visited ids, or outcome. |
| `SNAPSHOT_STATE_CLAMPED` | WARNING | Same-major migration clamps a preserved counter because its new same-major bounds exclude the saved value. |

Existing `SNAPSHOT_INVALID`, `SNAPSHOT_VERSION_UNSUPPORTED`, and `SNAPSHOT_PACK_MISMATCH` remain the errors for malformed, unsupported, or incompatible snapshots. Ordinary duplicate and dangling references continue to use `PACK_DUPLICATE_ID` and `PACK_DANGLING_REF`.

**Cross-section validation implementation:** the rules and scene validators must share pure parsers/models for `rules.json` and scene content so the engine can check authored effects against counter bounds/`maxDelta` and set values, not just check that referenced ids exist. Do not depend on validator registration order to pass parsed objects, and do not emit the same parse error twice. `RulesSectionValidator` owns errors in `rules.json`; `SceneSectionValidator` owns scene shape, graph, and locale-parity errors; a shared rule-reference pass validates the rule-bearing fields from both files. The current `SceneSectionValidator` checks graph references directly; extend it to define each scene id in `ReferenceIndex` as `scene:<locale>`. A `VISITED_SCENE` condition references that kind once for every declared locale, so dangling scene references use the existing shared-index error path. The loader's final `ReferenceIndex.check()` remains the single place that emits ordinary duplicate/dangling-reference issues.

## 3. Effects

Effects are typed, closed, and validated at pack-load time. Each effect is a JSON object with a `type` and exactly the fields shown here. Unknown effect types are `EFFECT_INVALID`.

| Type | Exact JSON shape | Semantics |
|---|---|---|
| `COUNTER_ADD` | `{"type":"COUNTER_ADD","counter":"health","delta":-2}` | Add `delta`; clamp the result to the counter's `[min, max]` immediately. If `maxDelta` exists, validate the authored delta against it. |
| `COUNTER_SET` | `{"type":"COUNTER_SET","counter":"health","value":5}` | Set the counter. Authored `value` must be within its declared bounds; runtime uses the same clamp helper for any future generated effect. |
| `FLAG_SET` | `{"type":"FLAG_SET","flag":"has-key","value":true}` | Set a flag to a boolean. |
| `SET_ADD` | `{"type":"SET_ADD","set":"inventory","value":"key"}` | Add one allowed set member. Adding an existing member is a no-op. |
| `SET_REMOVE` | `{"type":"SET_REMOVE","set":"inventory","value":"key"}` | Remove one allowed member. Removing a missing member is a no-op. |
| `EMIT_EVENT` | `{"type":"EMIT_EVENT","event":"run.completed","payload":{"outcome":"escaped","health":{"counter":"health"}}}` | Emit a declared event with a complete, type-checked payload. |
| `END_RUN` | `{"type":"END_RUN","outcome":"escaped"}` or `{"type":"END_RUN"}` | Request completion at the end of the current atomic step. Optional `outcome` must reference a declared outcome. |

`COUNTER_ADD` may use a negative or positive non-zero `delta`; zero is invalid because it has no state effect. For `COUNTER_SET`, the authored value must be in range. The runtime clamp is still mandatory as defence in depth and for generated effects added in later specs. When a generated value is clamped, report warning `EFFECT_CLAMPED`; do not silently clamp authored out-of-range `COUNTER_SET` values at load time.

Apply effects in authored list order. Each effect observes the state produced by earlier effects in that same list. An effect reference to an unknown counter, flag, set, set member, event, or outcome is rejected during validation. Once validated, effect application must be total: no missing-id lookup may throw during normal play.

`END_RUN` records an end request; it does not interrupt the current list, target scene entry, or objective fixed-point evaluation. The run becomes ended during the atomic step's final end check. If several `END_RUN` effects request outcomes in one step, the last explicit outcome wins. If no explicit outcome is requested, an ending scene's `outcome` supplies it. An omitted `END_RUN.outcome` does not erase an outcome already present in the state.

## 4. Conditions

All conditions use one JSON object with a `type` and the exact fields below. Only the listed types are supported. A condition tree has depth at most 4 (root depth is 1), at most 64 total nodes, and composite lists contain 1–16 children. Invalid types, wrong field types, or invalid limits produce `CONDITION_INVALID`.

| Type | Exact JSON shape | True when |
|---|---|---|
| `COUNTER_AT_LEAST` | `{"type":"COUNTER_AT_LEAST","counter":"health","value":3}` | Counter is at least `value`. |
| `COUNTER_AT_MOST` | `{"type":"COUNTER_AT_MOST","counter":"health","value":3}` | Counter is at most `value`. |
| `COUNTER_EQUALS` | `{"type":"COUNTER_EQUALS","counter":"health","value":3}` | Counter equals `value`. |
| `FLAG_IS` | `{"type":"FLAG_IS","flag":"has-key","value":true}` | Flag equals `value`. |
| `SET_CONTAINS` | `{"type":"SET_CONTAINS","set":"inventory","value":"key"}` | Set contains the allowed `value`. |
| `SET_SIZE_AT_LEAST` | `{"type":"SET_SIZE_AT_LEAST","set":"inventory","size":1}` | Set size is at least `size` (non-negative integer). |
| `VISITED_SCENE` | `{"type":"VISITED_SCENE","scene":"exit"}` | Scene id appears in the run's unique `visited` list. |
| `STEP_AT_LEAST` | `{"type":"STEP_AT_LEAST","step":3}` | The number of accepted commands is at least `step`; a new run starts at step 0. |
| `ALL` | `{"type":"ALL","conditions":[{"type":"FLAG_IS","flag":"has-key","value":true},{"type":"COUNTER_AT_LEAST","counter":"health","value":1}]}` | Every child is true. |
| `ANY` | `{"type":"ANY","conditions":[{"type":"FLAG_IS","flag":"has-key","value":true},{"type":"COUNTER_AT_LEAST","counter":"health","value":1}]}` | At least one child is true. |
| `NOT` | `{"type":"NOT","condition":{"type":"FLAG_IS","flag":"has-key","value":true}}` | Its one child is false. |

`ALL`, `ANY`, and `NOT` are evaluated deterministically in listed order; evaluation has no side effects. Comparison values must be inside the referenced counter's range. `SET_SIZE_AT_LEAST.size` must be between 0 and the set's number of allowed values. `VISITED_SCENE.scene` must reference a scene declared by the pack. Conditions do not support randomness, arbitrary expressions, inline scripts, string interpolation, or AI-authored operators.

## 5. Objectives

An objective is declared in `rules.json` with `id`, `labelKey`, `completeWhen`, and optional `onComplete` effects. It begins `ACTIVE`. Once `completeWhen` first evaluates to true, the engine changes it to `DONE` and applies its `onComplete` list exactly once. A `DONE` objective never reverts, including after a snapshot is resumed.

After each scene entry, evaluate objectives in `rules.json` definition order. Repeat passes until a complete pass marks no new objective as `DONE`, so one objective's completion effects can satisfy another objective. There are at most 8 objectives, so allow at most 9 passes: up to 8 passes may each complete one or more objectives, and one extra pass confirms the fixed point. If no no-change pass occurs within that cap, report `OBJECTIVE_EVALUATION_LIMIT` as an error issue and end the evaluation safely; this indicates an engine defect or invalid future extension, not something pack authors should work around.

Expose objectives as a `View` list in rules-file order. Each item contains its id, resolved locale label, and `ACTIVE` or `DONE` state. A pack with no objectives returns an empty list. A `completeWhen` condition may already be true at game start; startup runs the same objective evaluation and applies completion effects before returning `StartResult`.

## 6. Additions to authored scenes (schema v1, additive)

Keep the existing scene format and its exactly-one-of rule: a scene has `options`, or `next` (optionally with `branches`), or `end: true`. Cycles remain legal. The fields below are optional and do not change existing packs.

| Field | Location | Meaning |
|---|---|---|
| `effects` | Option | Effects executed when this option is chosen. Defaults to `[]`. |
| `requires` | Option | Condition that must be true for the option to be available. |
| `unmetHint` | Option | Optional string key shown when `requires` is false; must exist in every locale. |
| `hideWhenUnmet` | Option | Optional boolean, default `false`. When true, omit the option from `View` while unmet; otherwise expose it as disabled. |
| `onEnter` | Scene | Effects applied every time this scene is entered, including initial start and revisits. Defaults to `[]`. |
| `branches` | Linear scene | Ordered `{ "if": Condition, "next": "scene-id" }` entries. First matching entry wins; plain `next` remains the default if no branch matches. |
| `outcome` | End scene only | Optional declared outcome id used if the run ends here and no `END_RUN` in this step supplied an explicit outcome. |

Example option and scene fragments (each is valid JSON):

```json
{
  "id": "climb",
  "label": "Climb the stairs",
  "next": "exit",
  "requires": { "type": "SET_CONTAINS", "set": "inventory", "value": "key" },
  "unmetHint": "hint.need-key",
  "hideWhenUnmet": false,
  "effects": []
}
```

```json
{
  "id": "corridor",
  "blocks": [{ "kind": "NARRATION", "text": "A corridor branches in two directions." }],
  "next": "cell",
  "branches": [
    { "if": { "type": "FLAG_IS", "flag": "has-key", "value": true }, "next": "stairs" },
    { "if": { "type": "COUNTER_AT_MOST", "counter": "health", "value": 2 }, "next": "shelter" }
  ]
}
```

The first object is a complete option definition and belongs inside a scene's `options` array. The second is a valid scene fragment; in a real pack, all default and branch target ids must be declared in that pack. `branches` is valid only with `next`; it is not allowed on an option-based or ending scene.

For an unmet `requires`, the default is to show the option disabled with its resolved `unmetHint`. If `unmetHint` is absent, the option is still disabled but has a null hint. When `hideWhenUnmet` is true, omit that option from `View`; option indexes in the console are recalculated over the visible list. Choosing a disabled option directly by id is still rejected with `OPTION_DISABLED` and changes nothing.

The scene validator must accept these fields rather than warn `PACK_UNKNOWN_FIELD`, validate their shapes, validate nested effects and conditions, and register their references. Add compatibility constructors for the current six-component `Scene` and three-component `SceneOption` records so existing source callers still compile. The new parser must retain the existing scenes-v1 file wrapper and all existing content limits.

## 7. Atomic run-step algorithm

Use this order exactly so a host, transcript test, and resumed run observe identical results.

### Starting a run

1. Validate pack, locale, start inputs, and supported features as in spec 23. Initialize each counter and flag from `rules.json`; initialize each set from its initial members; mark all objectives `ACTIVE`; set outcome to null; set `step = 0`.
2. Set the start scene as current and add it to `visited`.
3. Apply the start scene's `onEnter` effects in list order.
4. Evaluate objectives to a fixed point using the rules in section 5.
5. If the start scene is an end scene or an `END_RUN` was requested, end with `COMPLETED` and resolve the outcome. Return startup-emitted events through `StartResult.events` in emission order.
6. Set `createdAt` and `updatedAt` from the injected `Clock`. Do not call the wall clock directly.

### Applying an accepted command

1. If ended, reject with `RUN_ENDED`. Validate command/scene pairing, option id, option visibility and enabled state. Any rejection returns the current view and issues without changing state, step, timestamps, events, or snapshot.
2. For an accepted `Choose` or `Advance`, increment `step` by one before evaluating step-dependent conditions. Resolve the target scene: chosen option's `next`, or for a linear scene the first matching branch's `next`, falling back to plain `next`.
3. For `Choose`, apply the selected option's effects in list order.
4. Move to the target scene and append its id to `visited` only if not already present.
5. Apply target `onEnter` effects in list order.
6. Evaluate objectives to a fixed point; collect all emitted events in actual emission order.
7. If the target is an end scene or any `END_RUN` requested completion, set `ended = true`, `endReason = COMPLETED`, and resolve `outcome` (explicit `END_RUN` outcome for this step wins; otherwise use the target end-scene outcome if present). Do not run another scene transition.
8. Set `updatedAt` once from the injected clock, rebuild `View`, and return `StepResult(accepted=true, view, events, issues)`.

`Abandon` is an accepted terminal command: increment `step`, set `ended = true`, `endReason = ABANDONED`, update `updatedAt`, and return an empty-block/empty-option ended view. Do not apply effects, evaluate objectives, or emit content events for abandon. Preserve the current state and outcome otherwise.

On every accepted step the engine applies each counter clamp immediately, maintains set membership only from declared values, keeps `visited` unique in first-visit order, and preserves objective terminal state. An end scene ends after its `onEnter` and objective effects, so an objective can become `DONE` in the same final view. A loop is legal; there is no implicit random selection or automatic step limit in spec 24.

## 8. Public API and `View` changes

Only types in `com.capybee.engine.api` are public host contract; rule definitions and interpreter types remain internal. Keep current component names unless this section explicitly extends them.

- Extend `RunState` with `Map<String,Integer> counters`, `Map<String,Boolean> flags`, `Map<String,List<String>> sets`, `Map<String,ObjectiveState> objectives`, and nullable `String outcome`. All new JSON fields are optional on read. Keep a compatibility constructor with the previous four arguments, delegating to empty maps/null outcome for source compatibility; the snapshot codec must separately fill missing fields from current pack defaults. Copy maps and nested lists defensively; sort each set's member list when serializing snapshots.
- Add `ObjectiveState { ACTIVE, DONE }` and `ObjectiveItem(String id, String label, ObjectiveState state)`.
- Add `List<ObjectiveItem> objectives` and nullable `String outcome` to `View`. Both are empty/null for stateless packs. Keep a compatibility constructor with the previous six arguments, delegating to empty objectives and null outcome, then migrate in-repo callers to the canonical constructor.
- Keep the current `Option(id, label, enabled, hint)` shape. Populate `enabled` and `hint` from `requires`; do not trust the host to enforce conditions.
- Keep `StatusItem(id, label, value)`. A displayed counter has `id = counter id`, `label = resolved labelKey`, and `value = "<current> / <max>"` in definition order. Do not use locale-specific number formatting.
- Extend `StartResult` with `List<Event> events` for effects that fire while entering the start scene or completing initial objectives. Keep the old two-argument constructor delegating to an empty event list. Normal command events remain in `StepResult.events`.
- Keep `Event` payload's current `Map<String,String>` API for compatibility. Manifest payload types are checked at load time; the runtime serializes integers as base-10 digits and booleans as lowercase `true`/`false`. Strings and ref ids remain unchanged.

The canonical declarations are provided one Java file per public type in section 14 so the signatures can be compiled independently. Implement defensive copying, null handling consistent with adjacent API records, and the compatibility constructors described above. `EndReason` stays `COMPLETED | ABANDONED`; do not encode victory/defeat in that enum.

## 9. Events and payload validation

Event definitions stay in `pack.json` and are already indexed as kind `event`. Each event has an id and up to 8 unique `{ "name", "type" }` payload fields, with type `STRING`, `INT`, `BOOL`, or `REF_ID`.

`EMIT_EVENT.payload` must provide exactly the declared field names: no omitted required fields and no extra fields. Each value is a string, integer, boolean, or a counter reference object such as `{ "counter": "health" }`. Counter references are valid only for `INT` fields. Direct values must match the declared type; `REF_ID` is a valid lowercase id string. Null, decimal numbers, arrays, nested arbitrary objects, or expression strings are invalid. The loaded pack defines the closed list of allowed event ids and value types.

Events are returned in emission order. Events emitted by startup scene entry or startup objective completion are exposed by `StartResult.events`; events from any accepted command are exposed by `StepResult.events`. No event sink, database, or implicit `run.completed` event is added. A pack must explicitly emit any event it expects a host to handle.

| Code | Severity | When |
|---|---|---|
| `EVENT_UNDECLARED` | ERROR | An authored `EMIT_EVENT` names an event absent from the manifest. Do not also report a generic dangling-reference error for the same bad event id. |
| `EVENT_PAYLOAD_INVALID` | ERROR | Payload names, value types, literal values, or counter references do not match the manifest definition. |
| `EVENT_NEVER_EMITTED` | WARNING | An event is declared but no scene/objective effect emits it anywhere in the pack. |

The scan for `EVENT_NEVER_EMITTED` must inspect scene `onEnter`, option `effects`, and objective `onComplete` lists. It must not execute content or perform I/O.

## 10. Snapshot extension, resume validation, and pack-version migration

Keep `snapshotVersion = 1`: these are additive optional fields inside the existing `state` object, which spec 23 permits. Do not change top-level snapshot field names. A new snapshot's `state` includes the current scene, unique visited list, ended/endReason, counters, flags, sets, objectives, and outcome.

When reading a snapshot written before spec 24, missing state fields are filled from the current `rules.json`: counters from `initial`, flags from `initial`/false, sets from `initial`/empty, objectives all `ACTIVE`, and outcome null. A snapshot that has no `rules.json` state reads as empty maps. Missing nested keys are handled the same way. Unknown top-level fields remain ignored. Snapshot parsing and validation must return issues, not throw for malformed snapshot content.

Pin each run to `(pack id, exact version)` in `RunSnapshot.pack`.

| Resume case | Required behavior |
|---|---|
| Same pack id and exact version | Restore only after checking scene existence, state ids, counter bounds, set membership, objective states, and outcome validity. Invalid values produce `SNAPSHOT_INVALID`. |
| Same pack id, different version, same semantic MAJOR | Allow migration. Preserve state ids still declared; drop removed ids and removed set members; add newly declared state using defaults; default new objectives to `ACTIVE`; clear a no-longer-declared outcome. Report warning `SNAPSHOT_STATE_PRUNED` if anything is dropped. Clamp a preserved counter only if a changed same-major bound excludes its stored value and report `SNAPSHOT_STATE_CLAMPED`. |
| Different pack id or different semantic MAJOR | Reject with `SNAPSHOT_PACK_MISMATCH`; do not start a partially migrated run. |
| Stored current `sceneId` does not exist in the selected pack | Reject with `SNAPSHOT_INVALID`, even for same-major migration. The host must keep/load the old pack or handle this incompatible scene change itself. |
| Unsupported snapshot schema version or malformed required fields | `SNAPSHOT_VERSION_UNSUPPORTED` or `SNAPSHOT_INVALID`, respectively. |

The registry currently holds one version per pack id; loading an older pack version is the host's responsibility. Migration does not remap scene ids. A same-major migration also prunes unknown ids from the unique `visited` list, with `SNAPSHOT_STATE_PRUNED`; the current scene is never pruned.

Pack author versioning rule: PATCH for text/metadata corrections and fixes that preserve scene ids and state meanings; MINOR for backward-compatible additions, plus additions/removals of state ids that can be handled by the explicit prune/default migration; MAJOR for renaming/removing a scene id that might be the current saved scene, reinterpreting an existing state id (including changing its kind), or changing a rule's meaning in a way that cannot be represented by state-id pruning/defaults. State range narrowing within a same-major version uses the explicit clamp warning above. Do not silently migrate across a major boundary.

`SNAPSHOT_STATE_PRUNED` and `SNAPSHOT_STATE_CLAMPED` are warnings; migration may succeed with warnings. All other snapshot errors reject resume. On resume, do not replay `onEnter`, objective completion effects, or events: a snapshot represents a state after that work has already happened.

Snapshot example after the winning fixture script in section 14:

```json
{
  "snapshotVersion": 1,
  "runId": "run-1",
  "pack": { "id": "fixture-text-dungeon", "version": "1.1.0" },
  "locale": "en",
  "seed": 42,
  "startInputs": {},
  "createdAt": "2026-10-12T10:00:00Z",
  "updatedAt": "2026-10-12T10:00:05Z",
  "step": 5,
  "state": {
    "sceneId": "exit",
    "visited": ["cell", "corridor", "armory", "stairs", "exit"],
    "ended": true,
    "endReason": "COMPLETED",
    "counters": { "health": 8 },
    "flags": { "has-key": true },
    "sets": { "inventory": ["key"] },
    "objectives": { "escape": "DONE" },
    "outcome": "escaped"
  }
}
```

The example's state corresponds to five accepted commands: cell → corridor → armory → corridor → stairs → exit. `health` starts at 10, decreases by 2 on entry to `stairs`, the key is added in `armory`, `escape` becomes `DONE` on entry to `exit`, and `outcome` is `escaped`.

## 11. `RunStore` port and in-memory adapter

Add the port in `com.capybee.engine.port` and its reference implementation under `com.capybee.engine.adapter`. Do not add a database, filesystem store, concurrency/version API, HTTP contract, or dependency on the application.

```text
public interface RunStore {
    Optional<RunSnapshot> load(String runId);
    void save(RunSnapshot snapshot);
    void delete(String runId);
}
```

`InMemoryRunStore` stores snapshots by `runId`, returns `Optional.empty()` for a missing id, replaces an existing value on save, and treats delete of a missing id as a no-op. Reject null ids/snapshots with `Objects.requireNonNull`. Use immutable snapshots and a thread-safe map implementation, but do not claim atomic command execution or optimistic concurrency; spec 29 must address persistence, cross-request races, and idempotency for hosts with retries/offline queues. This adapter exists for tests and console demos only.

## 12. Console updates

Update `ConsoleRenderer` without moving console I/O into core:

- Render status items on one line as `[label value]`, separated by two spaces, preserving status-definition order.
- Render objectives on the next line as `Objectives: <label> (active), <label> (done)` in objective-definition order. Omit the line when there are no objectives.
- Render `Outcome: <id>` when outcome is non-null.
- Render each visible option as `N) label`; append ` (unavailable)` when disabled and ` — <hint>` when the resolved hint is non-null. Keep the current numbering, `[Enter]`, `[end]`, and `[abandoned]` chrome.
- Hide options marked `hideWhenUnmet` before rendering and numbering; the engine's `View` is already responsible for this filtering.
- Stateless fixture output remains unchanged except where an option previously should have displayed disabled state or hint (none in `fixture-minimal`).

Fix the current console line reader as a prerequisite for new golden tests: read UTF-8 input one line at a time, and implement the documented `--script` mode rather than treating an arbitrary `InputStream.read()` chunk as one input line. Script mode echoes each supplied line as `> <line>`, including empty lines; exhausting a script ends that scripted run cleanly without an `[eof]` marker. Interactive EOF may retain existing behavior. Keep `PlayMain` as the only class that directly touches `System.in/out/err`.

## 13. Fixture upgrade and golden transcripts

Upgrade only `fixture-text-dungeon` from `1.0.0` to `1.1.0`. Keep `fixture-minimal` byte-for-byte unchanged. Keep the dungeon as a plain-text game. Because places are split out to spec 24.1, this spec does not add `places.json`; leave the existing `PLACES` feature marker for that spec to reconcile. All files below are complete and must match the manifest `files` list exactly.

### `game-packs/fixture-text-dungeon/pack.json`

```json
{
  "schemaVersion": 1,
  "id": "fixture-text-dungeon",
  "version": "1.1.0",
  "name": "Fixture — Text Dungeon",
  "locales": ["en"],
  "defaultLocale": "en",
  "features": ["PLACES"],
  "startInputs": [],
  "events": [
    {
      "id": "run.completed",
      "payload": [
        { "name": "outcome", "type": "STRING" },
        { "name": "health", "type": "INT" }
      ]
    }
  ],
  "contentPolicy": { "freeTextAllowed": false },
  "files": ["strings/en.json", "scenes/en/dungeon.json", "rules.json"]
}
```

### `game-packs/fixture-text-dungeon/strings/en.json`

```json
{
  "status.health": "Health",
  "objective.escape": "Escape",
  "hint.need-key": "You need the key."
}
```

### `game-packs/fixture-text-dungeon/rules.json`

```json
{
  "schemaVersion": 1,
  "counters": [
    {
      "id": "health",
      "labelKey": "status.health",
      "min": 0,
      "max": 10,
      "initial": 10,
      "display": true,
      "maxDelta": 3
    }
  ],
  "flags": [
    { "id": "has-key", "initial": false }
  ],
  "sets": [
    { "id": "inventory", "values": ["key", "token"], "initial": [] }
  ],
  "objectives": [
    {
      "id": "escape",
      "labelKey": "objective.escape",
      "completeWhen": { "type": "VISITED_SCENE", "scene": "exit" },
      "onComplete": [
        {
          "type": "EMIT_EVENT",
          "event": "run.completed",
          "payload": { "outcome": "escaped", "health": { "counter": "health" } }
        }
      ]
    }
  ],
  "outcomes": [
    { "id": "escaped" }
  ]
}
```

### `game-packs/fixture-text-dungeon/scenes/en/dungeon.json`

```json
{
  "schemaVersion": 1,
  "scenes": [
    {
      "id": "cell",
      "start": true,
      "blocks": [
        { "kind": "NARRATION", "text": "You wake in a cold stone cell. The door hangs open." }
      ],
      "options": [
        { "id": "corridor", "label": "Step into the corridor", "next": "corridor" },
        { "id": "listen", "label": "Stay and listen", "next": "listen" }
      ]
    },
    {
      "id": "listen",
      "blocks": [
        { "kind": "NARRATION", "text": "Water drips somewhere far away. Nothing else moves." }
      ],
      "next": "cell"
    },
    {
      "id": "corridor",
      "blocks": [
        { "kind": "NARRATION", "text": "A long corridor. A faint draught comes from the left, and a stair rises on the right." }
      ],
      "options": [
        { "id": "left", "label": "Go left", "next": "armory" },
        { "id": "right", "label": "Go right", "next": "stairs" },
        { "id": "back", "label": "Go back to the cell", "next": "cell" }
      ]
    },
    {
      "id": "armory",
      "blocks": [
        { "kind": "NARRATION", "text": "Racks of rusty tools line the walls. A small iron key lies beneath a bench." }
      ],
      "onEnter": [
        { "type": "FLAG_SET", "flag": "has-key", "value": true },
        { "type": "SET_ADD", "set": "inventory", "value": "key" }
      ],
      "options": [
        { "id": "back", "label": "Return to the corridor", "next": "corridor" }
      ]
    },
    {
      "id": "stairs",
      "blocks": [
        { "kind": "NARRATION", "text": "Worn steps lead up toward a square of daylight." }
      ],
      "onEnter": [
        { "type": "COUNTER_ADD", "counter": "health", "delta": -2 }
      ],
      "options": [
        {
          "id": "climb",
          "label": "Climb the stairs",
          "next": "exit",
          "requires": { "type": "SET_CONTAINS", "set": "inventory", "value": "key" },
          "unmetHint": "hint.need-key"
        },
        { "id": "back", "label": "Return to the corridor", "next": "corridor" }
      ]
    },
    {
      "id": "exit",
      "blocks": [
        { "kind": "NARRATION", "text": "Daylight. You made it out." }
      ],
      "end": true,
      "outcome": "escaped"
    }
  ]
}
```

### Scripts

`engine/cli/src/test/resources/scripts/dungeon-win.txt`:

```text
1
1
1
2
1
```

`engine/cli/src/test/resources/scripts/dungeon-locked-exit.txt`:

```text
1
2
```

### Golden: winning path

`engine/cli/src/test/resources/golden/dungeon-win.txt` (script echo and console rules from section 12 are part of the contract):

```text
Running fixture-text-dungeon in locale en
You wake in a cold stone cell. The door hangs open.

[Health 10 / 10]
Objectives: Escape (active)
  1) Step into the corridor
  2) Stay and listen
> 1
A long corridor. A faint draught comes from the left, and a stair rises on the right.

[Health 10 / 10]
Objectives: Escape (active)
  1) Go left
  2) Go right
  3) Go back to the cell
> 1
Racks of rusty tools line the walls. A small iron key lies beneath a bench.

[Health 10 / 10]
Objectives: Escape (active)
  1) Return to the corridor
> 1
A long corridor. A faint draught comes from the left, and a stair rises on the right.

[Health 10 / 10]
Objectives: Escape (active)
  1) Go left
  2) Go right
  3) Go back to the cell
> 2
Worn steps lead up toward a square of daylight.

[Health 8 / 10]
Objectives: Escape (active)
  1) Climb the stairs
  2) Return to the corridor
> 1
Daylight. You made it out.

[Health 8 / 10]
Objectives: Escape (done)
Outcome: escaped
  [end]
```

### Golden: locked exit remains visible but unavailable

`engine/cli/src/test/resources/golden/dungeon-locked-exit.txt`:

```text
Running fixture-text-dungeon in locale en
You wake in a cold stone cell. The door hangs open.

[Health 10 / 10]
Objectives: Escape (active)
  1) Step into the corridor
  2) Stay and listen
> 1
A long corridor. A faint draught comes from the left, and a stair rises on the right.

[Health 10 / 10]
Objectives: Escape (active)
  1) Go left
  2) Go right
  3) Go back to the cell
> 2
Worn steps lead up toward a square of daylight.

[Health 8 / 10]
Objectives: Escape (active)
  1) Climb the stairs (unavailable) — You need the key.
  2) Return to the corridor
```

Tests must assert that trying `Choose("climb")` while it is disabled returns `accepted=false`, `OPTION_DISABLED`, and a byte-for-byte unchanged snapshot. The winning path must assert one `run.completed` event with payload `{ "outcome": "escaped", "health": "8" }` in the final step result, and the snapshot above must round-trip exactly (apart from any test-controlled timestamps/run id).

## 14. Code skeleton and package placement

Follow the current package boundaries: domain records have no Jackson imports; JSON parsing is under `.json`; adapters implement ports. All code remains under `engine/core` or `engine/cli`.

| Type | Package | Responsibility |
|---|---|---|
| `CounterDef`, `FlagDef`, `SetDef`, `ObjectiveDef`, `OutcomeDef`, `Rules` | `com.capybee.engine.rules` | Immutable validated pack declarations. |
| `Effect`, `Condition`, internal rule-state/evaluator types | `com.capybee.engine.rules` | Closed typed rule model and deterministic evaluation. |
| `ObjectiveState`, `ObjectiveItem` | `com.capybee.engine.api` | Public, host-visible objective state. |
| `RulesSectionValidator`, rules parser/codec | `com.capybee.engine.rules.json` | Parse `rules.json`, validate definitions, and register references. |
| Extended `Scene`, `SceneOption` and a branch type | `com.capybee.engine.content` | Hold validated additive scene fields. |
| Extended `SceneSectionValidator` / `SceneSets` parser | `com.capybee.engine.content.json` | Parse and validate scene effects, requirements, entry effects and branches. |
| Extended `RunState`, `RunSnapshot` read/write, `View`, `StartResult`, `ObjectiveItem` | `com.capybee.engine.api` and `api.json` | Host-facing immutable state, views, events and backward-readable snapshots. |
| `RunStore` | `com.capybee.engine.port` | Run snapshot storage contract. |
| `InMemoryRunStore` | `com.capybee.engine.adapter` | Reference store for tests and console demos. |

Required signatures (implementation bodies are not part of the contract):

```java
// File: engine/core/src/main/java/com/capybee/engine/api/ObjectiveState.java
package com.capybee.engine.api;

public enum ObjectiveState { ACTIVE, DONE }
```

```java
// File: engine/core/src/main/java/com/capybee/engine/api/ObjectiveItem.java
package com.capybee.engine.api;

public record ObjectiveItem(String id, String label, ObjectiveState state) {}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/api/RunState.java
package com.capybee.engine.api;

import java.util.List;
import java.util.Map;

public record RunState(
    String sceneId,
    List<String> visited,
    boolean ended,
    EndReason endReason,
    Map<String, Integer> counters,
    Map<String, Boolean> flags,
    Map<String, List<String>> sets,
    Map<String, ObjectiveState> objectives,
    String outcome
) {
    public RunState {
        visited = List.copyOf(visited);
        counters = Map.copyOf(counters);
        flags = Map.copyOf(flags);
        var sortedSets = new java.util.LinkedHashMap<String, List<String>>();
        sets.forEach((id, values) -> sortedSets.put(id, values.stream().sorted().toList()));
        sets = Map.copyOf(sortedSets);
        objectives = Map.copyOf(objectives);
    }
    public RunState(String sceneId, List<String> visited, boolean ended, EndReason endReason) {
        this(sceneId, visited, ended, endReason, Map.of(), Map.of(), Map.of(), Map.of(), null);
    }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/api/View.java
package com.capybee.engine.api;

import com.capybee.engine.content.Block;
import java.util.List;

public record View(
    List<Block> blocks,
    List<Option> options,
    InputSpec input,
    List<StatusItem> status,
    boolean ended,
    EndReason endReason,
    List<ObjectiveItem> objectives,
    String outcome
) {
    public View {
        blocks = blocks == null ? List.of() : List.copyOf(blocks);
        options = options == null ? List.of() : List.copyOf(options);
        status = status == null ? List.of() : List.copyOf(status);
        objectives = objectives == null ? List.of() : List.copyOf(objectives);
    }
    public View(List<Block> blocks, List<Option> options, InputSpec input, List<StatusItem> status,
                boolean ended, EndReason endReason) {
        this(blocks, options, input, status, ended, endReason, List.of(), null);
    }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/api/StartResult.java
package com.capybee.engine.api;

import java.util.List;

public record StartResult(Run run, List<Issue> issues, List<Event> events) {
    public StartResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
        events = events == null ? List.of() : List.copyOf(events);
    }
    public StartResult(Run run, List<Issue> issues) {
        this(run, issues, List.of());
    }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/port/RunStore.java
package com.capybee.engine.port;

import com.capybee.engine.api.RunSnapshot;
import java.util.Optional;

public interface RunStore {
    Optional<RunSnapshot> load(String runId);
    void save(RunSnapshot snapshot);
    void delete(String runId);
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/adapter/InMemoryRunStore.java
package com.capybee.engine.adapter;

import com.capybee.engine.api.RunSnapshot;
import com.capybee.engine.port.RunStore;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class InMemoryRunStore implements RunStore {
    private final ConcurrentMap<String, RunSnapshot> snapshots = new ConcurrentHashMap<>();
    @Override public Optional<RunSnapshot> load(String runId) {
        return Optional.ofNullable(snapshots.get(Objects.requireNonNull(runId, "runId")));
    }
    @Override public void save(RunSnapshot snapshot) {
        RunSnapshot value = Objects.requireNonNull(snapshot, "snapshot");
        snapshots.put(Objects.requireNonNull(value.runId(), "snapshot.runId"), value);
    }
    @Override public void delete(String runId) {
        snapshots.remove(Objects.requireNonNull(runId, "runId"));
    }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/CounterDef.java
package com.capybee.engine.rules;

public record CounterDef(
    String id, String labelKey, int min, int max, int initial,
    boolean display, Integer maxDelta
) {}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/Condition.java
package com.capybee.engine.rules;

import java.util.List;

public sealed interface Condition permits Condition.CounterAtLeast, Condition.CounterAtMost,
        Condition.CounterEquals, Condition.FlagIs, Condition.SetContains, Condition.SetSizeAtLeast,
        Condition.VisitedScene, Condition.StepAtLeast, Condition.All, Condition.Any, Condition.Not {
    record CounterAtLeast(String counter, int value) implements Condition {}
    record CounterAtMost(String counter, int value) implements Condition {}
    record CounterEquals(String counter, int value) implements Condition {}
    record FlagIs(String flag, boolean value) implements Condition {}
    record SetContains(String set, String value) implements Condition {}
    record SetSizeAtLeast(String set, int size) implements Condition {}
    record VisitedScene(String scene) implements Condition {}
    record StepAtLeast(long step) implements Condition {}
    record All(List<Condition> conditions) implements Condition {
        public All { conditions = List.copyOf(conditions); }
    }
    record Any(List<Condition> conditions) implements Condition {
        public Any { conditions = List.copyOf(conditions); }
    }
    record Not(Condition condition) implements Condition {}
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/Effect.java
package com.capybee.engine.rules;

import java.util.Map;

public sealed interface Effect permits Effect.CounterAdd, Effect.CounterSet, Effect.FlagSet,
        Effect.SetAdd, Effect.SetRemove, Effect.EmitEvent, Effect.EndRun {
    record CounterAdd(String counter, int delta) implements Effect {}
    record CounterSet(String counter, int value) implements Effect {}
    record FlagSet(String flag, boolean value) implements Effect {}
    record SetAdd(String set, String value) implements Effect {}
    record SetRemove(String set, String value) implements Effect {}
    record EmitEvent(String event, Map<String, PayloadValue> payload) implements Effect {
        public EmitEvent { payload = Map.copyOf(payload); }
    }
    record EndRun(String outcome) implements Effect {}

    sealed interface PayloadValue permits PayloadValue.StringLiteral, PayloadValue.IntegerLiteral,
            PayloadValue.BooleanLiteral, PayloadValue.CounterRef {
        record StringLiteral(String value) implements PayloadValue {}
        record IntegerLiteral(int value) implements PayloadValue {}
        record BooleanLiteral(boolean value) implements PayloadValue {}
        record CounterRef(String counter) implements PayloadValue {}
    }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/FlagDef.java
package com.capybee.engine.rules;

public record FlagDef(String id, boolean initial) {}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/SetDef.java
package com.capybee.engine.rules;

import java.util.List;

public record SetDef(String id, List<String> values, List<String> initial) {
    public SetDef {
        values = List.copyOf(values);
        initial = initial == null ? List.of() : List.copyOf(initial);
    }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/ObjectiveDef.java
package com.capybee.engine.rules;

import java.util.List;

public record ObjectiveDef(String id, String labelKey, Condition completeWhen, List<Effect> onComplete) {
    public ObjectiveDef { onComplete = onComplete == null ? List.of() : List.copyOf(onComplete); }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/OutcomeDef.java
package com.capybee.engine.rules;

public record OutcomeDef(String id) {}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/Rules.java
package com.capybee.engine.rules;

import java.util.List;

public record Rules(List<CounterDef> counters, List<FlagDef> flags, List<SetDef> sets,
                    List<ObjectiveDef> objectives, List<OutcomeDef> outcomes) {
    public Rules {
        counters = List.copyOf(counters);
        flags = List.copyOf(flags);
        sets = List.copyOf(sets);
        objectives = List.copyOf(objectives);
        outcomes = List.copyOf(outcomes);
    }
    public static Rules empty() { return new Rules(List.of(), List.of(), List.of(), List.of(), List.of()); }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/content/SceneBranch.java
package com.capybee.engine.content;

import com.capybee.engine.rules.Condition;

public record SceneBranch(Condition condition, String next) {}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/content/SceneOption.java
package com.capybee.engine.content;

import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.Effect;
import java.util.List;

public record SceneOption(String id, String label, String next, List<Effect> effects,
                          Condition requires, String unmetHint, boolean hideWhenUnmet) {
    public SceneOption { effects = effects == null ? List.of() : List.copyOf(effects); }
    public SceneOption(String id, String label, String next) {
        this(id, label, next, List.of(), null, null, false);
    }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/content/Scene.java
package com.capybee.engine.content;

import com.capybee.engine.rules.Effect;
import java.util.List;

public record Scene(String id, boolean start, List<Block> blocks, List<SceneOption> options,
                    String next, boolean end, List<Effect> onEnter, List<SceneBranch> branches,
                    String outcome) {
    public Scene {
        blocks = List.copyOf(blocks);
        options = List.copyOf(options);
        onEnter = onEnter == null ? List.of() : List.copyOf(onEnter);
        branches = branches == null ? List.of() : List.copyOf(branches);
    }
    public Scene(String id, boolean start, List<Block> blocks, List<SceneOption> options,
                 String next, boolean end) {
        this(id, start, blocks, options, next, end, List.of(), List.of(), null);
    }
}
```

```java
// File: engine/core/src/main/java/com/capybee/engine/rules/json/RulesSectionValidator.java
package com.capybee.engine.rules.json;

import com.capybee.engine.pack.Pack;
import com.capybee.engine.pack.PackValidationContext;
import com.capybee.engine.pack.SectionValidator;

public final class RulesSectionValidator implements SectionValidator {
    @Override
    public void validate(Pack pack, PackValidationContext context) {
        // Parse and validate rules.json only; no I/O or runtime state changes.
    }
}
```

The new rule types may be nested records or separate package-private records in implementation, but JSON `type` strings and runtime semantics above are fixed. Extend the existing record constructors/API with the compatibility overloads described in section 8. `Engine.sectionValidators()` must keep the current `SceneSectionValidator` and add `RulesSectionValidator`; the shared `ReferenceIndex.check()` remains the loader's final cross-file check.

## 15. Quality gates and implementation constraints

- `rules.json` is optional and must be in `pack.json.files` when present. Do not make it a required file for every pack.
- Do not make `places.json` required or add map behavior here, even if the pack declares `PLACES`. Spec 24.1 must resolve the existing feature metadata, and specs 22/23 need follow-up edits to point places behavior there.
- Preserve no-framework-on-classpath and engine-boundary tests. No Jackson imports in domain records, no console I/O in core, no direct wall-clock/random/id creation.
- Validation is closed-world and deterministic. Never evaluate arbitrary code or turn condition/effect names into reflective class names.
- Keep issue messages free of full player text, pack dumps, or start-input content. Report file, JSON pointer, code, and a short safe explanation through the existing validation context.
- Update `EngineBoundaryTest`'s allowed package/test rules only where required; do not weaken it to make tests pass.
- Maintain the existing JaCoCo line coverage gate of 70% for the engine bundle and add focused tests for every new code path.

## Testing

All engine tests are ordinary JUnit 5 tests and must not need Spring, a database, HTTP, Node, an LLM key, or the application server.

- **State declarations:** counter bounds/initial/maxDelta; flag defaults; set allowed values/initial members/duplicates; objective/outcome definitions; optional missing `rules.json`.
- **Effect behavior:** every effect type, order-of-application visibility, counter clamping, authored `maxDelta` errors, set add/remove idempotence, outcome selection, end request ordering, event emission order.
- **Conditions:** truth tables for every leaf and composite, nested conditions, referenced ids, depth/node/child limits, branch first-match/default behavior, `STEP_AT_LEAST` at step 0 and after accepted commands.
- **Objectives:** already-true-at-start, completion effects exactly once, objectives unlocking other objectives, fixed-point pass order and cap, no reactivation after resume.
- **Scene validation:** additive fields accepted; bad condition/effect shape; missing references; bad label keys; invalid `branches` placement; `requires`, `hideWhenUnmet`, `onEnter`, `outcome`, option-level effects.
- **Events:** exact manifest payload match, each payload type, counter reference only for INT, unknown event, never-emitted warning, startup events through `StartResult`, step events through `StepResult`.
- **Snapshots:** old snapshot with no new fields, current round trip, sorted set serialization, unknown top-level fields ignored, exact-version invalid state, same-major state pruning/defaulting/clamp warning, major mismatch, missing current scene, unsupported snapshot version. Resume must not replay scene entry effects or re-emit events.
- **RunStore:** load missing/present, save replacement, delete present/missing, null rejection.
- **Determinism:** identical pack version + locale + start inputs + seed + commands yield identical views/events/snapshots given fixed clock/id adapters. Run twice for both fixture scripts.
- **Console:** status, objectives, outcome, unavailable options and hints; script lines echoed exactly; `fixture-minimal` remains unchanged; run both golden scripts character for character.
- **Boundary / quality:** `EngineBoundaryTest`, `NoFrameworkOnClasspathTest`, fixture-load tests, pack-index lint, JaCoCo 70% gate.

### Verification commands

```sh
mvn -f engine/pom.xml -B clean verify
```

Also run the `engine/cli` golden transcript tests and verify that a handwritten stateless pack can still be launched from the console. Check the built CLI on Windows for UTF-8 output; this environment cannot establish Windows behavior.

## Confirmed decisions

Pavlo confirmed these decisions on 2026-10-12:

1. **Places scope:** Spec 24 contains no place graph or `GO_TO`. Places will be covered by spec 24.1, with no renumbering of planned specs 25–31. Specs 22 and 23 need follow-up edits to point to spec 24.1.
2. **Pack-version migration:** Allow same-major migration with the controlled cleanup described in section 10; reject major changes and missing current scenes.
3. **Run storage:** Keep the first version in-memory only. Persistence and request concurrency belong in spec 29.
4. **Random rules:** Do not add randomness in spec 24. If a later spec adds it, derive each draw from `(seed, step, index)` so snapshot migration does not need hidden random-generator state.

## Rollout checklist

1. Add `com.capybee.engine.rules` and its JSON parser/validator; register `RulesSectionValidator` without removing `SceneSectionValidator`.
2. Extend scene parsing and validation for effects, requirements, hints, `onEnter`, branches, and end-scene outcome.
3. Implement ordered effects, conditions, objective fixed-point evaluation, event validation/emission, and end-of-step logic.
4. Extend `RunState`, snapshot codec, resume migration, `View`, and `StartResult` as specified; keep snapshot schema version 1 and old snapshots readable.
5. Add `RunStore` and `InMemoryRunStore` in the defined packages.
6. Upgrade only `fixture-text-dungeon` to 1.1.0 and add its scripts and golden transcripts. Keep `fixture-minimal` unchanged.
7. Update CLI status/objective/outcome/disabled-option rendering and fix line-by-line script input required by the existing spec-23 promise.
8. Run the complete quality gate, including reference-data checks, behavior/golden tests, API signature compilation, boundary tests, and JaCoCo.
9. Review the required follow-up edits to specs 22 and 23 to align places references with confirmed spec 24.1. Do not change app/server or unrelated features.

## Compatibility with specs 22 and 23

This section records required follow-ups; spec 24 implementation must still use the currently checked-in source as its immediate code baseline.

- **Spec 22 series map / vocabulary:** it currently assigns optional places and `GO_TO` to spec 24. Update spec 24's scope to rules/state only and move places, `GO_TO`, and Places vocabulary ownership to spec 24.1. Keep the planned specs 25–31 numbering unchanged. `PackLayout.requiredFiles` does not change in spec 24.
- **Spec 23 ground truth:** its old statement that the repository has no `engine/` folder is stale; the folder and the authored scene API/runtime exist. Update the ground-truth notes when editing that spec next.
- **Spec 23 scene schema:** extend the existing scene/option records additively with `effects`, `requires`, `unmetHint`, `hideWhenUnmet`, `onEnter`, `branches`, and end-scene `outcome`. Existing packs without these fields remain valid.
- **Spec 23 API:** add maps/objectives/outcome to `RunState`; objectives/outcome to `View`; events to `StartResult` for startup effects. `Option.enabled` and `Option.hint` already exist in current source and must be populated, not renamed.
- **Snapshot:** extend `state` only; keep `snapshotVersion = 1`, apply defaults on read, and implement the migration/rejection codes in section 10.
- **Validators:** `Engine.sectionValidators()` must return both `SceneSectionValidator` and `RulesSectionValidator`. The shared reference-index final check remains after all validators.
- **Fixture and CLI contract:** spec 23 currently says `fixture-text-dungeon` will get `places.json` in spec 24. With the split, defer that file to spec 24.1. The inspected `ConsoleApp` also lacks the promised script handling and reads a byte chunk as one line; fix this mismatch as required for the golden tests.
- **Current renderer:** `Option` already stores `enabled` and `hint`, but `ConsoleRenderer` does not print them. Add disabled labels and hints plus objectives/outcome, without moving rendering into core.

## Notes

- The actual `engine/` source wins over the older “not implemented” assumption in the handoff. This spec is written against the current `Engine`, `RunState`, `RunSnapshot`, `Scene`, `SceneSectionValidator`, `View`, `Option`, `ConsoleApp`, and `ConsoleRenderer` contracts observed in the repository.
- The Maven build, Java 25 compilation, Windows console UTF-8, and a live scripted CLI run have not been executed in this environment. The handoff reports that Maven and JDK 25 are unavailable in its sandbox; the current working environment has Java 21 and no Maven. Document the actual verification performed when delivering the file.
