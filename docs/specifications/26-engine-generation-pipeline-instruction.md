# 26 — Engine Generation Pipeline

**Artifact type:** Copilot-ready implementation specification  
**Destination:** `docs/specifications/26-engine-generation-pipeline-instruction.md`  
**Depends on:** intent note `docs/specifications/intent/01-universal-game-engine.md`; specs 22, 23, 24, 24.1, and 25  
**Next:** spec 27 — Engine API Contract  
**Status:** Proposed implementation specification. The architectural contracts below are binding; package names and constructor shapes must be reconciled against current `main` before editing existing APIs.

## Purpose

Implement the provider-neutral generation pipeline for packs that declare the `GENERATION` feature. The pipeline uses an LLM through a narrow `TextGenerator` port, assembles prompts from the selected pack and a minimal validated context, obtains schema-constrained output, validates it locally, attempts at most one repair for invalid output, and falls back to authored content when generation cannot safely complete.

The generation lifecycle is staged:

1. `THEME` — produce a compact, typed theme artifact from permitted start inputs and pack-defined preferences.
2. `SCENARIO` — choose/fill a scenario skeleton and produce a bounded beat plan using pack-defined templates and route/choice slots.
3. `CAST_AND_QUESTS` — fill the permitted actor/hero and quest/missions slots using pack-declared archetypes and objective templates.
4. `SCENE` — generate only the current scene/beat when it is needed; subsequent scene content is generated lazily.
5. `INPUT_RESOLUTION` / `SHORT_REPLY` — connect spec 25's free-text interpretation contract to the same structured generation boundary without bypassing its guard, scrubber, or validation rules.

The pipeline is an engine capability, not CapyBee application logic. It does not create new game rules, effects, counters, flags, sets, objectives, events, places, movement rules, or arbitrary actions. The model supplies bounded narrative data inside pack-authored constraints; the engine and pack remain authoritative.

## Repository baseline and verification status

The user reports that specs 22, 23, 24, 24.1, and 25 are implemented and committed. The local specification artifacts available during drafting establish these integration contracts:

- Pack manifest uses `features`, locale-specific strings/scenes, `files`, optional `contentPolicy`, and a `GENERATION` feature. Generation prompts live under `prompts/<locale>/...` and required files are computed by `PackLayout.requiredFiles(manifest)`.
- Public engine shape is based on `Engine`, `Run`, `Command`, `View`, `StepResult`, `RunRequest`, and `RunSnapshot`; scene content has a typed, versioned schema. Specs 24 and 24.1 own deterministic state/rules and places/map behavior respectively.
- Spec 25 owns `Command.SubmitText`, input normalization and local PII scrubbing, `InputGuard`, `InputInterpreter`, the closed `InputResolution` results, safety policy, and the ban on raw free text in logs, events, snapshots, and stores.
- The engine core must remain universal, headless, deterministic where it controls behavior, and free of provider/network/framework dependencies. Existing specs require offline/fixture tests and authored fallback behavior.

**Source inspection limitation:** this session could not fetch the GitHub repository or resolve `github.com`, so it could not freshly inspect current Java sources, the latest `main` commit, or the committed spec 25 implementation. Before changing code, inspect the real checkout and use existing types and signatures. This file does not claim that its illustrative API has been compile-verified.

**Template dependency check is mandatory.** The roadmap identifies a template system (scenario skeletons, archetypes, beat types, slots, JSON schemas), but the locally available spec 24 artifact covers rules/state rather than defining that catalogue. Inspect the implemented `engine/core/.../template` package, pack fixture data, and template validators. Reuse the implementation if present. Do not create a second template format or silently change the ownership/schema of `templates/*.json`. If the template contract is absent or materially different, record the missing dependency in the implementation summary and implement only compatible generation contracts until the template owner is reconciled.

## 1. Scope

### In scope

- A generic `TextGenerator` port and a provider-specific adapter outside `engine/core`.
- Pipeline orchestration for theme, scenario, actor/quest, and lazy scene generation.
- Structured prompts and bounded, stage-specific output contracts.
- Safe prompt assembly from global policy, pack prompts, templates, authored data, and explicitly permitted user input.
- Local parsing and semantic validation even when the provider supports JSON-schema-constrained output.
- One repair attempt for schema/semantic validation failures, then authored fallback.
- Validation of pack, locale, template, route, scene, actor/quest, text-length, and asset references.
- Integration seam for spec 25's `InputGuard` and `InputInterpreter`.
- Per-run and per-player generation budgets, quota accounting, token/cost telemetry, provider timeout/error handling, and privacy-safe cache behavior.
- A provider adapter initially targeting Anthropic Claude Haiku 4.5, with an explicit configurable/pinned model ID.
- Test doubles, deterministic fixtures, golden stage outputs, test-only provider contract checks, and CLI/service-level verification that does not require a real API key.

### Out of scope

- CapyBee-specific check-in semantics, honeycomb rewards, daily reward caps, mini-games, real-life echoes, or any product-specific input source. Those belong to the CapyBee pack and application specs.
- Building a public HTTP API, session lifecycle, authentication, database tables, or the offline write queue. Spec 27 wires the pipeline into the Engine API and host lifecycle.
- Rendering a hex board or scene UI; spec 28 defines client rendering.
- Defining a second template/catalog schema if one is already implemented.
- Any ability for the model to execute tools, access the network, read arbitrary local files, edit the pack, or write directly to the run state.
- Model training, fine-tuning, embeddings/vector databases, multi-agent orchestration, or autonomous tool use.

## 2. Non-negotiable invariants

1. **Engine decides; model proposes.** Generated output is untrusted content until it passes local schema and semantic validation. A valid JSON response is not necessarily a valid game artifact.
2. **Closed-world game behavior.** The model may fill text/slots and select only choices, templates, archetypes, objectives, and asset references explicitly supplied as allowed. It may not invent mechanics, effects, arbitrary commands, new rule IDs, new places, or state transitions.
3. **Pack-first.** Pack ID and exact version pin prompt resources, templates, schemas, locale, and fallback content. A prompt or template from another pack/version must never be reused accidentally.
4. **Lazy scenes.** At run initialization, generate only the stages/artifacts required to prepare play. Generate a scene only when it becomes current or is otherwise required to enter it. Do not pre-generate all scene prose.
5. **Fallback is a supported path.** Every required stage must have an authored, validated fallback or a documented safe way to continue in authored-only mode. Provider failure must not corrupt the run or make authored choices unusable.
6. **Repair once.** After a generated artifact fails parsing/schema/semantic validation, make at most one constrained repair call for that artifact. If it still fails, use fallback. No repair loops.
7. **Spec 25 remains authoritative.** Free text must be normalized, locally scrubbed, guarded, and kept out of logs/persistent prompts. The generation pipeline must not bypass safety checks or send original unsanitized user text to a provider.
8. **No raw prompt persistence.** Do not persist full system prompts, assembled prompts, raw user text, scrubbed user text, model request bodies, or raw response bodies for debugging or caching. Persist only approved validated artifacts and content-free metadata where host retention policy allows.
9. **Bound every resource.** Limit prompt input, output tokens, generated item counts, scene count, wall-clock timeout, repair calls, and total provider calls/cost.
10. **No provider in core.** `engine/core` defines types and ports only. HTTP, vendor SDK, credentials, provider model names, request headers, and network error translation belong in a separate adapter boundary.
11. **No provider required for core tests.** The unit/integration suite runs with fake generator and fake clock/ID/quota dependencies; no network, account, or API key is required by `mvn verify`.
12. **No model-created identifiers as authority.** IDs used for routing, rule references, objective references, and assets come from the pack/template or are assigned by trusted code. Model-proposed display text is not an ID.
13. **No hidden choices leakage.** A scene-generation or interpretation prompt receives only the choices/slots allowed for the active stage; never pass hidden options or internal state the player is not permitted to observe.
14. **No silent model migration.** The provider model ID is explicit configuration, visible in safe operational metadata, and not selected by pack/user input. Upgrade via configuration plus evaluation tests.
15. **Text-first output.** All generated content remains renderable without images or assets. Asset IDs are optional references from the fixed catalog, never generated image URLs or executable markup.

## 3. Terms

- **Pipeline:** the orchestration of stage requests, validation, bounded repair, fallback, quota accounting, and artifact storage.
- **Stage:** one closed operation in `THEME`, `SCENARIO`, `CAST_AND_QUESTS`, `SCENE`, `INPUT_RESOLUTION`, `SHORT_REPLY`, or internal `REPAIR`.
- **Artifact:** a typed, schema-versioned, validated output from a stage. It is not a prompt or raw provider response.
- **Template slot:** a pack-defined location the pipeline/model may fill. The template defines the permitted structure and semantics.
- **Beat plan:** the bounded scenario structure/graph used to decide which beat is current and which authored route a choice follows. It must be constrained by the template system.
- **Authored fallback:** pack content that is validated at load time and can be used without model access.
- **Provider adapter:** a concrete `TextGenerator` implementation that translates an engine request into a provider call and maps provider results/errors back to provider-neutral DTOs.
- **Quota scope:** an opaque host-supplied key for usage accounting. It is not a child name, profile identifier, email, or prompt field and must never be sent to the model.
- **Validated artifact cache:** storage of validated generated artifacts, not of prompts or raw text.

## 4. Feature and pack contract

### 4.1 `GENERATION` remains optional

- Packs without `GENERATION` must continue to load and play exactly as before.
- Do not make generation prompts, templates, or provider configuration globally required for all packs.
- A pack that declares `GENERATION` must contain the generation-specific files required by the implemented manifest/template contract. Missing files must result in stable validation issues during `PackLoader.tryLoad`, not runtime null dereferences.
- A pack with invalid generation resources must fail generation-feature validation, but unrelated packs without `GENERATION` must remain valid.
- Do not edit `fixture-minimal` unless a specific backwards-compatibility test requires an additive change. Upgrade/create a dedicated generation fixture instead.

### 4.2 Pack prompts are data, not global policy

Use the existing pack prompt paths and filenames if implemented. The expected conceptual split is:

- pack-authored task instructions and tone for the selected locale;
- stage-specific template/schema definitions;
- static authored fallback artifacts;
- resolved, localized display strings;
- declared allowed actor archetypes, quest/objective patterns, beat types, and asset catalog references.

Do not create a new directory/filename scheme merely to match this conceptual list. If the current repository uses different names, preserve it and document the mapping in code/tests.

Pack-supplied prompt text must never override the engine's global safety policy, model configuration, schema, resource limits, privacy rules, or closed action vocabulary. Treat unknown prompt front matter, unsupported template fields, and unbounded prompt files as validation errors/warnings according to existing pack conventions. Never load a path supplied by the model or player; pack file access must be through the validated `Pack` file index.

### 4.3 Fallback completeness

For every generation stage enabled by a pack, validate that the pack has enough authored fallback content to continue safely. The exact location must follow the implemented template contract; do not add duplicate fields if fallback is already represented there.

Minimum completeness:

- `THEME`: a neutral/default theme artifact or a valid authored theme option.
- `SCENARIO`: at least one valid scenario skeleton/plan.
- `CAST_AND_QUESTS`: an authored or template-derived cast/quest fallback, which may be an empty cast/quest list when the template allows it.
- `SCENE`: a valid fallback scene/beat for every reachable generated beat type, or an explicit authored scene route that can be used instead.
- `INPUT_RESOLUTION` / `SHORT_REPLY`: a localized generic no-match/unavailable reply; these are never allowed to block ordinary choices.

Generation fallback content must pass the same local validator as model output. A missing or invalid fallback is a pack validation error for a pack that depends on that stage.

## 5. Pipeline stages and stage contracts

Stage DTOs must be immutable and versioned. The types below describe the data boundaries and are illustrative names; reuse existing canonical types wherever available. Do not use untyped `Map<String,Object>` as the public domain contract.

### 5.1 `THEME`

**Input:** pack ID/version, locale, selected theme/template IDs, declared non-sensitive start inputs, approved preference enums, and at most the already-scrubbed/guard-approved free-text context allowed by spec 25.

**Output:** compact theme fields such as a short title, premise, tone tags, and bounded setting hints. Values are descriptive only.

**Must not output:** rules, effects, state values, objective completion logic, scene IDs, user identity, asset URLs, generated system prompts, or new policy categories.

**Default limits:** title ≤ 80 Unicode code points; premise ≤ 500; ≤ 8 tone tags, all from the pack-declared vocabulary. Pack limits may be stricter, never looser than engine hard limits.

### 5.2 `SCENARIO`

**Input:** the validated theme artifact, locale, selected scenario skeleton/template, allowed beat types, and any constraints explicitly exposed by the template contract.

**Output:** a bounded scenario plan with a summary and a graph/list of beat slots that refer to existing template/beat definitions. Each choice route must correspond to a predeclared choice slot or a route from the selected scenario skeleton. Scene/beat IDs are assigned by trusted code or taken from templates.

**Must not output:** arbitrary executable routes, dangling next IDs, mechanics, state mutations, movement instructions that bypass spec 24.1, or new option/action IDs outside the allowed skeleton.

The validator must reject unreachable required plan nodes, dangling edges, duplicate IDs, unsupported beat types, routes outside the skeleton, and graphs that violate the template's explicit termination/reuse guarantees. Cycles are allowed only when the template contract allows them. If the existing template system defines other graph semantics, that implementation is authoritative.

### 5.3 `CAST_AND_QUESTS`

**Input:** validated scenario plan, pack-declared actor/hero archetypes, quest/objective templates, allowed names/language rules, and locale.

**Output:** descriptive actor data and quest/missions content. Use neutral engine terms (`Actor`, `Quest`/`Objective`) in core. A product pack can label these as heroes, missions, or any other words in its own strings.

**ID and reference rules:** model output may supply display values only. Trusted code assigns generated content IDs from the injected ID source or resolves them from already declared IDs; never accept a model-selected ID as authority for rules/state/effect/event/asset/route references. Quest completion is tied to existing objective definitions and engine conditions, not a generated narrative claim.

**Bounds:** cap actor and quest counts by the selected template, with a hard engine maximum of 12 actors and 12 quests per run. Display names ≤ 60 code points; descriptions ≤ 600; role/personality summaries ≤ 300. If implemented schema limits are stricter, use the stricter limits.

### 5.4 `SCENE` (lazy scene-by-scene)

**Input:** the current planned beat ID/type, its allowed template slots, validated theme/scenario/cast/quest artifacts needed for that beat, current locale, and the permitted narrative context. Send only context required for that scene; do not send the whole snapshot, unrelated scenes, hidden choices, or the entire conversation history.

**Output:** content compatible with the existing scene/block schema. The model may produce narration/speech text and fill declared presentation slots. The scenario/template determines the available option IDs, route targets, end behavior, rule effects, and state transitions. Where the template permits generated choice labels, the model fills only the label slots for those predeclared choices; code retains their IDs and route targets.

**Lazy invariant:** generating the current scene must not recursively generate destination scenes. After a choice, the next required beat is generated when needed. A host may prefetch only under an explicit future policy; prefetch must respect budget and must not affect gameplay results.

**Bounds:** output must fit existing scene schema (including block count, text length, label length, speaker rules, reference rules and allowed block kinds). Use the strictest limit between the existing Scene validator and this specification. Any generated asset ref must resolve against the pack's fixed catalog; otherwise drop the optional ref or use fallback per the existing asset policy. Never accept HTML/script/URL markup as executable presentation.

### 5.5 `INPUT_RESOLUTION` and `SHORT_REPLY`

Implement through spec 25's existing `InputInterpreter`/`InputResolution` contract. The pipeline may reuse the same generator and adapter, but must not define a parallel free-text command or a second set of input result types.

- Input is passed to the pipeline only after local normalization, PII scrubbing, and the input guard's approved decision.
- `SelectOption` is valid only if the ID belongs to a currently visible and enabled option supplied to the model.
- `ShortReply` is transient, short, locally scrubbed, and safety-checked before display.
- No model result directly mutates rules/state, emits events, changes places, advances the run, or creates an option.
- When generation/guard service is unavailable, return the safe code and message defined by spec 25; ordinary choices remain usable.

## 6. Stable artifact envelope and serialization

Every generated artifact must be stored/returned as a typed value with an explicit schema version and enough metadata to detect cross-pack reuse:

```json
{
  "artifactSchemaVersion": 1,
  "stage": "SCENE",
  "pack": { "id": "example-pack", "version": "1.2.0" },
  "locale": "en",
  "templateId": "declared-beat-template",
  "artifactId": "assigned-by-engine",
  "payload": {}
}
```

The envelope is illustrative and must not replace an existing canonical artifact format. If artifact wrappers already exist, reuse them. The `payload` is stage-specific typed JSON validated against the exact schema for that stage and pack version.

Rules:

- `artifactSchemaVersion` changes only for a breaking serialized-artifact schema change. If artifacts are embedded in snapshots, coordinate migrations with spec 24's snapshot policy and spec 27's storage contract.
- Pin exact `pack.id`, `pack.version`, `locale`, stage, template ID, and schema version. Reject an artifact for another pack/version/locale/template unless an explicit migration exists.
- Validate on both creation and load/resume. Cached JSON is not trusted just because it was written previously.
- Never include raw input text, scrubbed input text, assembled prompts, provider request/response bodies, API credentials, or user identity fields in the envelope.
- Generated prose may still contain sensitive user context. Treat artifacts as private run data under host retention controls; do not expose one run's artifact in another run or cross-user cache.
- Ensure `toString()`/debug representations do not print prompt/input payloads or arbitrary generated text when they can be logged implicitly.

## 7. Pipeline API and ports

Inspect current `engine/core` packages first. The following is a target contract, not a requirement to duplicate existing types. Prefer closed stage-specific DTOs over a generic untyped document API.

### 7.1 Provider-neutral text generation port

A minimal shape could be:

```java
public interface TextGenerator {
    TextGenerationResult generate(StructuredGenerationRequest request);
}

public record StructuredGenerationRequest(
    String requestId,
    String taskId,
    String systemPolicyVersion,
    String taskInstructions,
    String untrustedDataJson,
    String outputSchemaJson,
    int maxOutputTokens,
    CostClass costClass
) {}

public record TextGenerationResult(
    String outputJson,
    TokenUsage usage,
    String modelId,
    GenerationFinishReason finishReason
) {}

public record TokenUsage(
    long inputTokens,
    long outputTokens,
    long cacheReadTokens,
    long cacheWriteTokens
) {}
```

Do not copy/paste this shape if `TextGenerator` or similar already exists. Preserve Java version, package naming, nullability, result/error patterns, and API constructors used by `main`.

Required semantics:

- `taskInstructions` contains fixed pipeline policy plus constrained pack task instructions. Pack text is not allowed to replace global policy.
- `untrustedDataJson` is a serialized, typed data envelope, not concatenated into the trusted instruction string. It may contain player-derived content only after spec 25's scrubber and guard.
- `outputSchemaJson` is selected by trusted code for the expected stage. The model cannot choose/change it.
- No tools/function calls are exposed in v1. No URL fetching, shell, database, file, or tool access is available to the model.
- Every call specifies a bounded output token ceiling and stage cost class (`LIGHT`/`STANDARD`, matching the project vocabulary if already implemented).
- The response carries provider-reported token usage when available. If missing or invalid, mark usage unknown and use the conservative budget policy; never invent usage figures.
- Exceptions surfaced to callers are sanitized provider-neutral failure codes. Do not include provider body, prompt text, or generated content in exception message or `Issue.message`.

### 7.2 Pipeline facade

Expose a small façade from the existing generation package, or adapt its existing equivalent:

```java
public interface GenerationPipeline {
    GenerationResult<ThemeArtifact> generateTheme(ThemeGenerationInput input);
    GenerationResult<ScenarioArtifact> generateScenario(ScenarioGenerationInput input);
    GenerationResult<CastQuestArtifact> generateCastAndQuests(CastQuestGenerationInput input);
    GenerationResult<GeneratedSceneArtifact> generateScene(SceneGenerationInput input);
}
```

Do not make these exact class names public if the repository has an existing `GenerationService` API. Required behavior matters more than the illustrative method names:

- every request has explicit pack/version/locale and allowed template IDs;
- every result contains validated artifact or safe fallback metadata;
- failures are categorized using closed codes;
- callers can distinguish `GENERATED`, `FALLBACK_USED`, `UNAVAILABLE`, `QUOTA_EXCEEDED`, and `INVALID_PACK` without reading model prose;
- the pipeline itself does not make HTTP requests except through injected ports and does not read environment variables or secrets;
- the pipeline does not mutate `Run`/`RunState`; the owning runtime/host applies the artifact using normal runtime APIs.

### 7.3 Deterministic dependencies

Inject all IDs, clock/time, provider, quota ledger, artifact store/cache, and pricing catalog. Do not call `Instant.now()`, `UUID.randomUUID()`, `new Random()`, or the network from domain code. Use existing `Clock`, `IdSource`, and test conventions. Generated text is naturally not reproducible across separate remote calls; reproducibility is achieved by storing a validated artifact and reusing it for a run, not by pretending the model is deterministic.

## 8. Prompt assembly and injection defense

Assemble each model request from these separate, inspectable parts:

1. Fixed engine safety/purpose policy and policy version.
2. Stage-specific instructions from trusted code.
3. Pack-provided stage prompt as constrained content instructions, never as policy authority.
4. Typed input JSON with explicit untrusted-data fields.
5. The trusted JSON output schema and enum constraints.

Requirements:

- Never insert player text, generated text, a scene block, a pack string, or a retrieved description into the trusted system-policy section.
- Delimit values as JSON data fields. Explicitly instruct the model to treat those values as data and ignore embedded requests to change roles, rules, output schema, budgets, tools, or policy.
- Do not pass conversation history by default. If a stage needs previous generated context, pass only the validated, minimal artifact fields named in its input DTO.
- Do not pass internal state maps, counters, flags, sets, hidden options, OAuth/authentication details, profile identifiers, database rows, system prompts from other packs, or data from another run.
- Do not ask the model to design a new schema, propose arbitrary code, or return markdown fences. The desired output is one JSON object matching a selected schema.
- Keep prompt length within configured input budgets before calling the provider. Large pack prompt files are validation errors/warnings and never silently truncated mid-policy.
- Do not send a full prompt/response dump to logs or tracing. A developer-only local mode may display synthetic fixture prompts when no personal input is present, but must be opt-in, disabled by default, and must not run in production.
- A pack cannot request model tools or instruct the engine to access a URL/file. There are no tools in v1.

## 9. Structured output, validation, one repair, fallback

### 9.1 Three validation layers

1. **Provider/schema constraint:** when the configured provider supports structured output, pass the schema through its supported schema-constrained response feature. This is an optimization, not the security boundary.
2. **Syntactic and DTO validation:** parse output as one JSON value, reject extra prose/fences, require supported schema version and fields, reject unknown fields where the schema uses a closed object, enforce enum/count/length/null limits, and map to immutable DTOs.
3. **Semantic pack/runtime validation:** check all IDs and references against the pinned pack/template/asset catalog, enforce allowed choice slots/routes, validate scene/block contracts, ensure generated values do not introduce mechanics, and confirm all target transitions are part of the approved scenario skeleton.

Any layer may fail. Do not render, cache, persist, or apply an artifact until all required layers pass.

### 9.2 Validation issue codes

Follow the existing issue/validation convention. Add stable generation-prefixed codes only if equivalents do not already exist. Suggested closed codes include:

- `GENERATION_FEATURE_DISABLED`
- `GENERATION_CONFIG_INVALID`
- `GENERATION_PROVIDER_UNAVAILABLE`
- `GENERATION_PROVIDER_TIMEOUT`
- `GENERATION_RATE_LIMITED`
- `GENERATION_OUTPUT_INVALID_JSON`
- `GENERATION_OUTPUT_SCHEMA_INVALID`
- `GENERATION_OUTPUT_REFERENCE_INVALID`
- `GENERATION_OUTPUT_UNSAFE`
- `GENERATION_REPAIR_EXHAUSTED`
- `GENERATION_FALLBACK_USED`
- `GENERATION_ARTIFACT_MISSING`
- `GENERATION_ARTIFACT_VERSION_MISMATCH`
- `GENERATION_QUOTA_EXCEEDED`
- `GENERATION_BUDGET_EXCEEDED`
- `GENERATION_STAGE_UNSUPPORTED`

Reuse a current code if the semantics are the same; do not introduce aliases that mean the same thing. Diagnostics may include stage, code, pack ID/version, locale, duration bucket, and bounded token/cost metadata, but no generated/prompt/user text.

### 9.3 Repair contract

- Exactly zero or one repair attempt per invalid generation artifact.
- The repair request uses the same stage schema, pack/version, locale, and strict output bounds.
- Send only the minimum data needed: the failed artifact (as untrusted data), validation code(s), and bounded JSON-pointer paths. Do not include user text, full exception messages, or internal stack traces in the repair explanation.
- A repair cannot change stage, schema, model, template selection, quota policy, or allowed output vocabulary.
- Repair consumes a normal provider call, token, time, and monetary budget. If the budget will not allow the repair, use fallback without trying it.
- A semantically invalid repaired artifact is rejected; do not attempt a second repair.

### 9.4 Fallback behavior

- Prefer the pack's authored fallback artifact for the same stage/template/locale.
- If a stage fallback is not applicable, use a validated authored-only route/scene that keeps the run playable and displays a generic localized message where appropriate.
- If no safe fallback exists, return a failure result that lets the host stop generation gracefully. Do not fabricate a partially valid scenario or leave a half-mutated run.
- Fallbacks must not require a provider and must themselves pass the same local validators.
- A fallback result must be marked `FALLBACK_USED` in content-free metadata and should not appear to the user as a provider error dump.
- If generation fails after earlier stages succeeded, keep those already validated artifacts; do not rewrite or regenerate them automatically. Only the failing stage uses its fallback unless the host explicitly starts a new run.

## 10. Safety integration with spec 25

Spec 25 takes precedence over every prompt or provider requirement in this section.

- Wire the existing `InputGuard` and `InputInterpreter` ports using the same stage orchestration where appropriate; do not add a parallel implementation that avoids their validation path.
- Incoming free text must first pass the spec 25 normalizer and `PiiScrubber`, then required guard/moderation. Only the safe, scrubbed form may be sent to a provider.
- A missing/unavailable required guard is not permission to call the model. Return `INPUT_GUARD_UNAVAILABLE` and keep authored choices usable.
- Before a generated `ShortReply` or any generated text is rendered, run output PII scrubbing and the required output safety check. Unsafe output is not displayed, cached, or persisted; use the safe fallback.
- Generated actor names, descriptions, quest text, scene blocks, and choices are output content and must pass the pack's schema/semantic limits and the configured content policy.
- Do not store a raw or scrubbed prompt as part of `GeneratedArtifact`, `RunState`, `RunSnapshot`, `RunStore`, the quota ledger, metrics, or error reports. Persist only validated artifacts whose retention is allowed by the host.
- Keep player-specific personal data out of provider requests except the minimum scrubbed free-text content explicitly allowed by the input policy. Never include a child's name, profile ID, exact age/date of birth, school, address, authentication information, or contact detail as generation context unless a separately reviewed host policy explicitly requires a field; this engine spec creates no such exception.
- Prompt injection strings are untrusted data even when the pack's own text asks the model to follow them. The pipeline's immutable policy and local validators remain authoritative.

## 11. Anthropic adapter and initial model configuration

### 11.1 Module boundary

Keep `TextGenerator` and its DTOs provider-neutral in `engine/core`. Put the concrete Anthropic network adapter in a separate adapter module/package such as `engine/adapter-anthropic` or the repository's existing adapter module. Do not add Anthropic classes or model IDs to core domain/API packages.

If adding a Maven module is necessary, update `engine/pom.xml` and CI intentionally. The core module must remain buildable and testable without the provider adapter. The CLI and future host can depend on an adapter only when explicitly configured.

Prefer the repository's existing HTTP/JSON stack if it is suitable. Avoid adding a large SDK when the same supported API can be implemented safely through the repository's existing adapter conventions. If a provider SDK is chosen, keep it entirely outside `engine/core` and pin its dependency version.

### 11.2 Model target

The implementation plan names **Claude Haiku 4.5** as the initial model. Configure the exact model ID (prefer a pinned snapshot such as `claude-haiku-4-5-20251001` when the selected platform supports it) outside the pack and source constants. The adapter must not accept model selection from a pack, free text, or a generated artifact.

**Current availability warning (checked 2026-10-13):** Anthropic's [Haiku 4.5 page](https://platform.claude.com/docs/en/models/haiku-4-5/overview) lists the snapshot, the $1/M input and $5/M output baseline, and a retirement commitment of “not sooner than October 15, 2026”. Anthropic also announced [Haiku 5.5 on October 7, 2026](https://www.anthropic.com/claude-haiku-5-5) and the current documentation search result recommends considering it. Model status/documentation appears to be in transition. Therefore:

- preserve Haiku 4.5 as the intended initial target unless the project owner changes the plan;
- recheck model access and current API parameters immediately before merging/deploying;
- make the model ID, price schedule, and supported structured-output mode configurable/versioned;
- do not automatically change to Haiku 5.5 or another provider as a hidden fallback;
- run the same contract/evaluation suite before any model migration.

See [Anthropic structured outputs](https://platform.claude.com/docs/en/build-with-claude/structured-outputs) for schema-constrained JSON output support. If the selected platform/model cannot use that feature, plain JSON prompting is permitted only because the pipeline also performs mandatory local parsing and validation.

### 11.3 Credentials and request behavior

- Read the API key only from the host's secret/configuration injection. An environment variable such as `ANTHROPIC_API_KEY` is an acceptable CLI/local convention; do not read secrets from `pack.json`, prompts, arguments that will appear in process listings, browser code, or generated content.
- Never print or log the key. Redact authorization headers and request bodies from HTTP diagnostics.
- Configure connect timeout, response timeout, connection limits, and max response bytes. Cancel/close a request when the deadline is reached where the HTTP client supports it.
- Do not use tools, web search, or provider-side prompt chaining in v1. Use one structured response per requested stage/repair.
- Map `401/403`, `429`, timeout, `5xx`, invalid JSON/content blocks, truncation, content refusal, and schema/response errors to closed provider-neutral codes. Never forward provider error bodies verbatim.
- Account for every outbound attempt against call budgets, including transport retry and repair attempts.
- Retry only clearly transient failures, at most once in v1, and only if the budget permits. Honor bounded `Retry-After` when present. Do not retry authentication/permission errors, invalid configuration, rejected content, or validation failures as transport errors.
- Treat an ambiguous timeout after a request was sent as a possibly billed call. Charge/reserve conservatively; do not let repeating the request bypass budget enforcement.
- Parse provider-reported usage from the response; validate nonnegative values. Unknown/malformed usage must not be represented as zero cost.

## 12. Budgeting, quotas, and cost tracking

Budgets are mandatory. Unlimited generation is not permitted, even in development builds exposed to real provider credentials.

### 12.1 Budget layers

Use these separate limits, with policy/config types reused if they already exist:

1. **Per-call:** input-token/prompt size and provider `max_output_tokens` ceiling per stage.
2. **Per-run:** maximum generation calls, scene count, total input/output tokens, wall-clock time, and cost.
3. **Per-player/day:** an opaque host-supplied quota scope tracks daily use across runs. The quota scope must never be included in a model prompt or content log.
4. **Global/provider:** concurrency and rate limits to protect service availability and spend.

Every outbound request reserves budget before dispatch; reconcile reservation against reported usage/cost afterward. If usage/cost is missing, use the conservative reserved maximum rather than refunding all usage. A repair or retry uses the same quota/budget path as a normal call.

### 12.2 Initial conservative defaults

If the application does not already have explicit documented settings, use the following configurable defaults for the first implementation. These are guardrail defaults, not immutable product policy:

| Limit | Initial default |
|---|---:|
| Provider calls per run, repair and retry included | 24 |
| Generated scenes per run | 12 |
| Input tokens per call | 8,000 |
| Output tokens per call | 1,500 |
| Total input tokens per run | 40,000 |
| Total output tokens per run | 12,000 |
| Total wall-clock budget for pipeline work in one run start/turn | 30 seconds, subject to host endpoint limits |
| Cost limit per run | USD 0.15 |
| Cost limit per player per UTC day | USD 0.50 |

Stage-specific output ceilings should be lower than the per-call ceiling when possible: theme 500 tokens, scenario 1,200, cast/quests 1,500, scene 1,200, free-text interpretation/reply 400, repair 1,200. The effective ceiling is the minimum of stage, configured provider, per-call, and remaining run budget.

Before implementing these defaults, inspect for an existing configured limit policy; preserve stricter existing values. Because provider prices may change, the dollar caps must be enforced using a versioned pricing catalog and provider-reported usage, not a hard-coded assumption that the model always costs the same.

### 12.3 Player scope and persistence

- Keep player identity in the host layer. The pipeline accepts an opaque `QuotaScope` solely for accounting and does not infer identity from free text.
- Provide a deterministic in-memory budget ledger for tests and standalone CLI use.
- Define a `BudgetLedger`/quota port so spec 27 can connect a persistent host ledger and atomically enforce per-player/day caps across requests/replicas. The in-memory ledger is not sufficient for production multi-instance daily quota enforcement.
- If no persistent player scope is available, enforce per-call and per-run caps; a production host must not claim that per-player/day caps are enforced until its persistent implementation is wired.
- Define the day boundary and timezone explicitly in the host configuration (UTC by default). Do not infer a child's local timezone from prompt content.

### 12.4 Cost calculations

- Maintain a versioned `ModelPrice`/pricing catalog for input tokens, output tokens, cache reads/writes if used, and any platform-specific fee that the provider documents.
- Convert the provider response usage into integer microdollars or another fixed-point currency unit; do not use floating-point money for quota comparisons.
- Record model ID, price-catalog version, stage, token counts, cache token counts (if exposed), computed cost, call result code, and duration bucket.
- Do not record prompt/output strings, hashes of those strings, provider bodies, user ID, email, or child name in cost records.
- If the model's price is not configured, fail closed before dispatch for production. In local tests, use an explicit fake pricing catalog. If usage is missing after a call, charge the reserved maximum or mark cost unknown and block further calls until safely reconciled.
- Keep price information sourced from the provider's current official pricing page and date/version it in config. Do not embed volatile rates inside prompt files.

## 13. Caching and artifact storage

### 13.1 What may be cached

Allowed:

- Parsed immutable pack resources and validated templates keyed by exact pack ID/version/locale/schema version.
- Validated generated artifacts for the same run and exact stage/template/locale, subject to host retention policy.
- Provider schema compilation/cache behavior when handled by provider infrastructure; do not place player content into a schema definition.

Disallowed by default:

- Cross-player or cross-run cache entries keyed by raw/scrubbed user text or by a content-derived hash of the prompt.
- Prompt/response bodies, original inputs, scrubbed inputs, full contexts, provider request bodies, API keys, or free-text hashes for debugging/correlation.
- Sharing generated text between runs when the generated text may depend on the player's free text or personal context.

### 13.2 Cache/artifact keys

Use stable, non-content identifiers: pack ID/version, locale, run ID, stage ID, template ID, artifact schema version, and an injected artifact ID. A per-run artifact lookup may key by `(runId, stage, templateSlotId)`. Do not add the user text or a hash of it to the cache key.

If a future optimization proposes a shared cache of artifacts derived only from non-sensitive public pack data, define the exact eligibility, collision, invalidation, privacy, and retention rules in a separate change; do not enable it by default.

### 13.3 Store contract and resume

- Introduce or extend an injected `GeneratedArtifactStore` port only if needed. In-memory implementation is required for tests and CLI.
- Store only fully validated artifacts and bounded content-free metadata. Writes should be atomic at the artifact level.
- Verify pack version, locale, schema version, stage/template IDs, artifact hash/integrity if already part of the repository's storage pattern, and semantic references when loading an artifact.
- Do not automatically regenerate a missing scene during `resume` before the host has had a chance to apply budget and privacy policy. Return `GENERATION_ARTIFACT_MISSING` so spec 27 can choose a bounded regenerate-or-fallback flow.
- If generated artifacts are embedded in `RunSnapshot`, keep them separate from raw/scrubbed prompts, make the snapshot field backward-compatible, and follow spec 24's migration rules. Do not bump the snapshot version unless a breaking schema change requires it.
- Persistent storage/retention specifics are finalized by spec 27. Spec 26 allows storage of validated generated artifacts, not prompts or raw text.

## 14. Provider-neutral error model and operational metadata

Use stable categories at the pipeline boundary, for example:

- `CONFIGURATION_ERROR`
- `UNAVAILABLE`
- `TIMEOUT`
- `RATE_LIMITED`
- `PROVIDER_REJECTED`
- `INVALID_RESPONSE`
- `SCHEMA_INVALID`
- `SEMANTIC_INVALID`
- `SAFETY_REJECTED`
- `QUOTA_EXCEEDED`
- `BUDGET_EXCEEDED`
- `FALLBACK_USED`

Map them to project issue/result conventions rather than adding a competing `Issue` type.

Safe telemetry may include request ID (random/non-identifying), pack ID/version, locale, stage, template ID, configured model ID, result code, input/output token counts, price-catalog version, cost estimate/actual, duration buckets, repair/fallback booleans, and provider HTTP status class. Avoid exact response latency if that becomes identifying in a tiny cohort; use the existing telemetry convention.

Never log input/output content, raw or scrubbed prompts, text hashes, prompt templates after interpolation, model request/response JSON, exception bodies, authorization headers, PII detections, or child/profile identifiers. Ensure implicit record `toString()` does not make content appear in logs. If a request/response DTO contains content, override `toString()` with a content-free representation or prevent it from crossing logging boundaries.

## 15. Configuration

Use the project's existing configuration conventions. Suggested settings, not mandatory duplicate property names:

```text
GENERATION_ENABLED=false by default in a host with no provider credentials
GENERATION_PROVIDER=anthropic
GENERATION_MODEL_ID=claude-haiku-4-5-20251001
ANTHROPIC_API_KEY=<secret injected by host>
GENERATION_CONNECT_TIMEOUT_MS=<bounded value>
GENERATION_RESPONSE_TIMEOUT_MS=<bounded value>
GENERATION_MAX_CALLS_PER_RUN=24
GENERATION_MAX_SCENES_PER_RUN=12
GENERATION_MAX_INPUT_TOKENS_PER_CALL=8000
GENERATION_MAX_OUTPUT_TOKENS_PER_CALL=1500
GENERATION_MAX_INPUT_TOKENS_PER_RUN=40000
GENERATION_MAX_OUTPUT_TOKENS_PER_RUN=12000
GENERATION_MAX_COST_USD_PER_RUN=0.15
GENERATION_MAX_COST_USD_PER_PLAYER_DAY=0.50
GENERATION_PRICE_CATALOG_VERSION=<explicit version>
```

Rules:

- Missing provider configuration must not break authored-only engine usage.
- If a pack requests generation but the host hasn't configured a provider, use the authored fallback and surface content-free `FALLBACK_USED` metadata.
- Production startup should fail clearly for malformed security-critical config (negative budgets, blank model ID, invalid price catalog) instead of silently turning limits off.
- Do not store secrets in `pack.json`, source control, test fixtures, logs, process arguments, generated artifacts, or snapshots.
- Pack-level configuration can narrow generation limits but may never increase host caps or provider caps.
- Host/provider config always takes precedence over model-suggested configuration; the model cannot modify its own budget, model ID, temperature, or tool policy.

## 16. Fixture and acceptance design

Create or extend a dedicated generic fixture (e.g. `fixture-generation-basic`) rather than modifying `fixture-minimal`. Do not make the fixture CapyBee-specific. It should include:

- `GENERATION` declared in its manifest using the existing schema;
- a small validated template set with one scenario skeleton, two or three beat types, a tiny closed set of actor archetypes/quest templates, and a few route slots;
- one allowed asset catalog reference (optional) plus a no-asset fallback;
- complete authored fallback theme/scenario/cast/scene content;
- one locale initially, with tests that confirm unsupported/mismatched locale paths fail cleanly;
- no real-person names, real child profiles, personal data, API key, or network dependency;
- golden JSON artifacts for theme, scenario, cast/quests, each generated scene type, invalid response, repaired response, fallback response, and input-resolution decisions.

Use a fake `TextGenerator` whose responses are deterministic by requested stage and scripted call order. The tests must be able to assert which stage was called and whether generation was lazy. No default test may contact Anthropic.

## 17. Required tests

### Pack/template validation

1. A pack without `GENERATION` loads and plays unchanged without prompt files, template files, or provider config.
2. A pack with `GENERATION` and missing required prompt/template/fallback resources reports stable validation errors.
3. All manifest file references, locale references, template IDs, slot IDs, beat types, archetypes, objective templates, and asset refs resolve.
4. Duplicate/dangling IDs, unknown fields, unbounded prompt files, invalid schemas, invalid enums, and cross-locale mismatches are caught at pack load or the appropriate generation validator.
5. Templates and pack prompts cannot override global rules/policy, increase host budgets, or enable model tools.

### Stage flow and laziness

6. The pipeline invokes stages in order `THEME` → `SCENARIO` → `CAST_AND_QUESTS`, followed by only the current `SCENE` when needed.
7. Entering a scene does not generate every reachable scene. Choosing a route causes only its destination scene to be requested, when required by the integration contract.
8. Previously validated artifacts are reused on later views/resumes; they are not regenerated on every render.
9. A selected model output cannot alter a route target or introduce a new option/action ID outside the template's declared set.
10. Model-generated actor/quest IDs are assigned/resolved by trusted code and cannot become rule/state IDs by coincidence.

### Structured output and validation

11. Valid schema-constrained output still receives local syntactic and semantic validation.
12. Plain prose, code fences, extra JSON objects, unknown schema versions, extra fields, wrong types, invalid enum, too-long text, duplicate IDs, and dangling references are rejected.
13. The first invalid result triggers at most one repair; a second invalid result triggers validated fallback.
14. Repair failure, unsupported schema capability, truncated response, refusal, timeout, rate limit, provider 4xx/5xx, and malformed provider usage all produce safe result codes and no content leak.
15. Every fallback itself passes the validators; invalid fallback causes a controlled failure and never mutates a run partially.

### Safety and privacy

16. Spec 25's guard receives only scrubbed text. If guard is missing/unavailable, no remote generation call is made for that input.
17. Generated unsafe/PII-bearing text is not shown, stored, cached, or logged; the safe fallback is used.
18. Prompt-injection text cannot select hidden/disabled options, change model/schema/budget, create tools, or generate mechanics.
19. Sentinels inserted into free text and generated response bodies never appear in logs, traces, `Issue.message`, events, snapshots, store keys, or exception messages.
20. No free-text prompt or content-derived hash is used in cross-run cache keys.

### Budgets and cost

21. All calls, repair attempts, and retry attempts count against the run and quota scope.
22. Budget is reserved before outbound dispatch; quota exhaustion blocks the call before network dispatch.
23. A repeated run cannot bypass the in-memory quota; host integration tests for spec 27 must cover persistent atomic quotas.
24. Unknown token usage is not recorded as zero cost and results in conservative budget treatment.
25. Model pricing calculations use fixed-point money and a versioned price catalog.
26. Every stage obeys token, output, time, scene-count, and call-count bounds.

### Architecture and compatibility

27. `engine/core` has no Anthropic classes, model IDs, API key handling, HTTP client, Spring, ORM, or provider SDK dependency.
28. The provider adapter can be omitted from the build/use path and all core tests still pass.
29. Existing `fixture-minimal`, authored-only packs, specs 22/23/24/24.1 tests, and spec 25 privacy/input tests stay green.
30. Existing public constructors/API contracts remain source-compatible where possible; any necessary additive API/snapshot changes are covered by compatibility tests.

## 18. Build and implementation boundaries

- Keep changes in the existing engine/docs/test boundaries. Add a separate provider adapter module only if the current repository does not already have a suitable adapter module. Do not change `app/server` endpoints or production database schema in this spec; that belongs to spec 27.
- Do not add a network test to the default `mvn -B verify` path. If a manual real-provider smoke test is useful, make it opt-in, clearly named, credential-gated, and never run in CI by default.
- Update the engine CI workflow only if needed for the new module. CI should test core, adapter compilation, and fake-generator contract tests, but must not require API secrets.
- Preserve JaCoCo/build coverage thresholds already in the repository. Do not reduce them to make the pipeline pass.
- Run existing build/tests first to capture baseline; run focused generation tests, then the complete `mvn -B verify` from `engine/` (or the actual repository's documented root); review `git diff` for unrelated changes.

## 19. Implementation sequence

1. **Audit actual `main`.** Find current `TextGenerator`/generation/template/input packages, `PackFeature.GENERATION`, `PackLayout`, prompt loader, scene schema, provider adapters, quotas, persistence and CLI patterns. Record existing signatures and tests in the PR summary. Do not assume this document's example class names exist.
2. **Resolve template integration.** Reuse the shipped template/slot/beat schemas. Write one small compatibility test proving the generation fixture can load/resolve a template and all its declared references.
3. **Define provider-neutral DTOs/port.** Add or adapt `TextGenerator`, structured request/response, token usage, stage result, cost class and error categories in the correct current module/package.
4. **Implement stage validators and fallback selection.** Implement typed validators before implementing the live network adapter. Add golden stage artifacts and invalid fixtures.
5. **Implement pipeline orchestration.** Add ordered stage flow, artifact IDs, lazy scene generation, bounded repair once, fallback, and generated artifact storage.
6. **Wire spec 25.** Reuse `PiiScrubber`, `InputGuard`, and `InputInterpreter`; prove the no-free-text path remains available and no request bypasses required guard checks.
7. **Implement quota/cost ports.** Add in-memory test ledger, reserve/reconcile logic, model pricing catalog, safe cost records, and strict limits.
8. **Implement provider adapter.** Add configurable/pinned Anthropic model call, structured output if supported, bounded timeouts/retries, credential redaction, content-free errors, and usage parsing.
9. **Add fixture-driven end-to-end tests.** Run the pipeline exclusively through a fake generator in ordinary CI. Add an optional manual smoke-test task only if practical.
10. **Validate compatibility and build.** Run spec 22/23/24/24.1/25 regressions, all engine tests, coverage, and boundary tests. Verify that provider dependencies are not transitive into `engine/core`.
11. **Document verified behavior.** Update the engine README/spec index and the implementation summary with modules/files changed, test commands/results, provider setup, known limitations, and any remaining template-source mismatch. Do not claim a live model call was tested unless it was actually run with authorized credentials.

## 20. Acceptance criteria

Spec 26 is complete when:

- A valid `GENERATION` pack can be parsed and its pipeline inputs/templates/fallbacks validated before a model call.
- A provider-neutral generation port exists and is the only provider call path.
- The initial Anthropic adapter is configurable with a pinned model ID, and no provider identifier or dependency leaks into engine core.
- Theme, scenario, cast/quests, and current scene artifacts are generated/selected in the required order; scene generation is lazy.
- Every provider output is locally validated; invalid output gets one repair at most and then validated authored fallback.
- Model output cannot define or mutate game rules, state transitions, route targets, hidden options, places, or IDs outside the allowed template/pack vocabulary.
- Spec 25 input safety and privacy guarantees remain intact across all generation paths.
- Per-call, per-run, per-player/day (through the host quota port), global, token, time, scene and cost limits are enforced; usage/cost is recorded without content.
- All default CI tests work without a live provider/key/network; existing engine and authored-only fixture behavior remains green.
- The implementation report records the real repository files/API signatures checked and candidly distinguishes fake-provider tests from any live-provider smoke test.

## 21. Decisions fixed by this specification

| Decision | Rule |
|---|---|
| Provider shape | Provider-neutral `TextGenerator` port; one concrete adapter outside `engine/core`. |
| Initial model | Claude Haiku 4.5 per plan, but explicit configurable/pinned model ID; verify availability before deployment. |
| Stage order | Theme → scenario → cast/quests → scene-on-demand; input interpretation uses spec 25's existing types. |
| Output | Structured JSON, then mandatory local validation. |
| Repair | At most once per invalid artifact; repair consumes ordinary budgets. |
| Fallback | Authored and prevalidated; no provider required. |
| Game authority | Model fills permitted slots; code/template owns IDs, routes, mechanics, and state changes. |
| Cache | Pack resources and same-run validated artifacts only by default; no raw prompts or cross-run free-text-derived cache. |
| Quotas | Enforce per-call/per-run immediately; persistent per-player/day accounting through a host-provided ledger and spec 27 integration. |
| Logs | Content-free metadata only; never prompt/output bodies or input hashes. |
| Offline | Authored-only flow remains usable without provider/key/network. |

## 22. Open implementation notes (do not silently guess)

1. **Template source of truth:** confirm the exact template schema and the authoritative locations of beat types, choice slots, actor archetypes, quests/objective patterns, scenario skeletons, and fallback artifacts on current `main`. This specification intentionally does not invent a replacement schema.
2. **Generated scene integration:** confirm how current `Run` resolves scene data and how generated artifacts will be exposed to it. Keep `Run.apply` atomic and use the public Engine API contract in spec 27 for orchestration; do not put network calls or hidden partial mutation into the deterministic core.
3. **Persistence:** choose in spec 27 whether validated generated artifacts are embedded in snapshot state or stored through `GeneratedArtifactStore`; both paths must preserve artifact identity and the spec 25 privacy rules.
4. **Production player/day quota:** use the persistent host ledger in spec 27; the in-memory implementation is for CLI/tests only and must not be represented as a production guarantee.
5. **Model lifecycle:** check Anthropic's current model status, API schema-constrained output support, and pricing at implementation/deployment time. Do not infer current availability from this document's dated note.
6. **Template gap:** if template capability is missing despite the roadmap, make the gap explicit rather than burying a new template contract in the pipeline PR.

## References

- CapyBee repository: https://github.com/pavelsokolovan/CapyBee (repository unavailable to inspect from this drafting session; inspect current `main` before implementation).
- Spec 25 input/safety contract: `docs/specifications/25-engine-input-and-safety-policy-instruction.md`.
- Anthropic Claude Haiku 4.5 model ID, limits, pricing, availability: https://platform.claude.com/docs/en/models/haiku-4-5/overview
- Anthropic Claude Haiku 5.5 announcement (October 7, 2026): https://www.anthropic.com/claude-haiku-5-5
- Anthropic structured outputs and JSON-schema response formats: https://platform.claude.com/docs/en/build-with-claude/structured-outputs
- Anthropic current pricing reference: https://platform.claude.com/docs/en/about-claude/pricing
