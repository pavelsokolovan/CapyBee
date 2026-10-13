# 25 — Engine Input and Safety Policy

**Artifact type:** Copilot-ready implementation specification  
**Destination:** `docs/specifications/25-engine-input-and-safety-policy-instruction.md`  
**Depends on:** intent note `docs/specifications/intent/01-universal-game-engine.md`; engine pack/API/runtime specs 22, 23, 24 and 24.1  
**Next:** spec 26 — Generation Pipeline  
**Status:** Proposed implementation spec; decisions below are defaults unless the intent note or current source proves otherwise.

## Purpose

Define a safe, privacy-preserving contract for player input in the universal game engine before an LLM generation pipeline is introduced. Players can choose an authored option, optionally submit free text, have free text interpreted as an existing choice or receive a short in-context reply, and continue playing without the model inventing rules or changing state directly.

This specification covers input modes, text limits and normalization, personally identifiable information (PII) scrubbing, content moderation contracts, prompt-injection boundaries, interpretation results, privacy-safe diagnostics, retention rules, host obligations, and tests. It is intentionally ahead of the LLM provider implementation: spec 26 may connect the interpretation contract to `TextGenerator` and the provider adapter, but spec 25 must keep the core usable without a provider or network.

The engine is for general use. It must not contain CapyBee, honeycomb, friendship, migration, or other product/theme-specific policy in package names, classes, identifiers, or tests. A pack may make policy stricter, but it may not weaken the engine's baseline privacy and safety guarantees.

## Repository baseline and source confidence

The public repository README confirms a standalone Java engine under `engine/`, separate from `app/server` and `app/ui`; it describes the SDK as independent of Spring and says the app does not consume it until the later web-host integration spec. See the [repository README](https://github.com/pavelsokolovan/CapyBee/blob/main/README.md).

The earlier specification artifacts available for this task describe the public shape used here: `Engine` / `Run` / `Command` / `StepResult`, `Command.SubmitText(String text)` reserved for input support, `View.input`, `InputSpec` modes `NONE`, `CHOICE`, `TEXT`, `CHOICE_OR_TEXT`, pack `contentPolicy` (`freeTextAllowed`, optional `audience`, optional `forbiddenTags`), `RunRequest.startInputs`, and `RunSnapshot.startInputs`. Treat the actual current implementation of specs 22, 23, 24 and 24.1 as the source of truth for those existing features; this document must not reassign their ownership or duplicate their schema.

**Verification limitation:** during preparation, GitHub showed the public repository README, but the directory pages/raw source files could not be fetched and a local clone failed because `github.com` could not be resolved. Consequently, this document does not claim that exact current source signatures, package contents, or all test names were freshly inspected. Before implementation, compare this spec with the actual `main` branch and preserve existing constructors and runtime behavior. Where this document's API sketch differs from source, adapt it additively and record the discrepancy rather than replacing existing API contracts silently.

## 1. Scope

### In scope

- A shared policy for free text supplied through scene input and `FREE_TEXT` start inputs.
- Optional free text alongside authored choices.
- Unicode normalization, size limits, control-character handling, and deterministic PII scrubbing.
- A provider-neutral safety guard contract with safe defaults and fail-closed behavior when required moderation is unavailable.
- A provider-neutral interpretation contract whose structured result is either an enabled option ID, a short reply, a clarification/no-match result, or a blocked result.
- Validation of all interpretation output against the current `View` and pack policy before it can affect runtime state.
- Privacy-safe issues, logs, metrics, snapshots, events, and retention rules.
- Console behavior and automated acceptance tests.
- Requirements that later API/web host and generation-pipeline specs must obey.

### Out of scope

- Implementing an LLM client or naming a concrete model/provider. That belongs to spec 26.
- Allowing model output to author rules, effects, conditions, objectives, places, option IDs, or state transitions.
- New game mechanics, the template catalog, Places graph semantics, or rule/state semantics already owned by the implemented specs.
- Implementing CapyBee-specific check-in, mission, friendship, or memory behavior.
- Defining the complete web API/session lifecycle or changing the app's existing database schema; the later host/API spec must wire these requirements into HTTP, offline queues, and server logging.
- Claiming regex PII detection can identify every real name, address, school, or other sensitive detail. Scrubbing is defense in depth, not a guarantee.

## 2. Non-negotiable invariants

1. **Choices remain the authoritative game actions.** Free text may select one currently visible and enabled option, or produce a short reply that does not advance the game. It may never create a new action.
2. **The engine decides; an interpreter proposes.** The engine checks every proposed option ID and runs the ordinary `Choose` path. An invalid, hidden, or disabled option ID is rejected without state changes.
3. **No raw free text in persistent engine state.** Raw text must not appear in `RunState`, `RunSnapshot`, `RunStore`, `Event`, generated issue messages, metrics, traces, or application logs. Persistent non-text start-input values may remain as before. Do not store a redacted text string merely as a workaround for storing raw text.
4. **PII scrubbing occurs locally before external moderation or interpretation.** No external provider may receive the original unredacted user string from this contract.
5. **Safety is enforced on both directions.** Inspect incoming text before interpretation, and scrub/check any proposed reply before rendering it.
6. **Fail closed for free text when a required safety service is unavailable.** Do not silently pass unchecked text to a provider or treat unavailable moderation as approval. Authored choices must remain usable.
7. **Pack policy can tighten, never loosen, global policy.** `freeTextAllowed: false` disables free text. A pack's `audience` and `forbiddenTags` add constraints; no pack field may disable PII scrubbing, bypass moderation, retain raw text, or enable content prohibited by the host's global policy.
8. **Untrusted text is data, not instructions.** It must be passed to interpretation as a clearly delimited user-data field. It cannot override system/developer policy, request tools, change model roles, or expand the allowed action set.
9. **No content in operational diagnostics.** Diagnostics may report decision codes and bounded metadata, but never input or generated reply content, prompts, payloads, content hashes, or excerpts.
10. **No network or framework dependency in `engine/core`.** The core defines contracts and deterministic helpers only. Network/provider implementations live behind adapters and are added by the generation/host specs.
11. **Rejected input changes nothing.** The current scene, run-state maps, place position, visited lists, objective state, events, outcome, and logical step remain unchanged after a rejected command or an unavailable guard/interpreter.
12. **Universal and locale-agnostic.** Use generic policy categories and resolved pack text. Avoid hard-coded Polish/English safety messages in core.

## 3. Terminology

- **Raw input:** the exact text submitted by a player before normalization/scrubbing.
- **Normalized input:** text after Unicode normalization, trimming, and disallowed-control handling.
- **Safe text:** normalized text after local PII scrubbing and a successful safety decision. This is short-lived request data, not automatically safe for storage.
- **Input guard:** the policy boundary that assesses safety of the scrubbed input. It is a port; an external implementation may be connected later.
- **Input interpreter:** a provider-neutral port that maps safe text plus the currently available choices to a structured resolution. It does not execute commands.
- **Resolution:** one of `SELECT_OPTION`, `SHORT_REPLY`, `NO_MATCH`, or `BLOCKED`.
- **Content-free diagnostic:** metadata describing what kind of decision occurred, without the text that led to it.

## 4. Input policy and authored pack schema

### 4.1 Reuse the existing manifest contract

Do not replace or rename existing `pack.json` fields:

```json
"contentPolicy": {
  "freeTextAllowed": true,
  "audience": "general",
  "forbiddenTags": ["personal-data"]
}
```

The snippet is illustrative; preserve the schema already implemented. `freeTextAllowed` must default to `false` when the field is missing, unless current manifest compatibility rules explicitly state otherwise. `audience` and `forbiddenTags` remain optional pack hints/constraints. They are not a substitute for global baseline policy.

No pack-owned retention setting is introduced. A pack must not be able to opt users into content retention, logging, third-party disclosure, weaker moderation, or longer provider retention.

### 4.2 Scene input declaration

If the current `Scene` model does not already express an input mode, add an **optional, backward-compatible** scene field. Recommended JSON shape:

```json
{
  "id": "campfire",
  "blocks": [{ "type": "TEXT", "text": "What would you like to do?" }],
  "options": [
    { "id": "tell-story", "label": "Tell a story", "next": "story" },
    { "id": "look-around", "label": "Look around", "next": "clearing" }
  ],
  "input": {
    "mode": "CHOICE_OR_TEXT",
    "maxChars": 300
  }
}
```

Use existing block/option JSON shape if different; the contract above only illustrates the new input field. Add it through the existing scene parser/validator rather than introducing a parallel scene format.

Rules:

- Missing `input` preserves current behavior: scenes with options behave as `CHOICE`; scenes without options behave as `NONE`/the current advance behavior.
- `NONE` accepts no choice and no text; retain the current scene-advance contract.
- `CHOICE` requires at least one option and accepts `Choose` only.
- `TEXT` accepts text and has no selectable options.
- `CHOICE_OR_TEXT` requires at least one option and accepts either `Choose` or `SubmitText`.
- An authored `input.mode` conflicting with the scene's options is a validation error. Do not infer text support merely because `SubmitText` exists in the sealed `Command` API.
- Text modes have an explicit positive `maxChars`, bounded by the global ceiling below. If omitted, use a documented default of `300` for compatibility and usability; an explicit value outside the accepted range is a content validation error.
- Non-text modes expose `InputSpec.maxChars = 0` (or preserve the equivalent existing convention if the current API uses a different representation). Do not advertise a positive text limit when text is not accepted.

If the current source already has an equivalent field or validation rule, reuse it and do not add duplicate fields.

### 4.3 Hard limits

- Maximum raw input: **500 Unicode code points** per submission. A pack may set a lower `maxChars`, never a higher one.
- Default maximum when a text input does not set its own bound: **300 Unicode code points**.
- Maximum visible short reply: **280 Unicode code points**, no more than **3 lines**.
- Maximum number of options passed to an interpreter: use the actual current `View.options` count, but cap the interpretation context to **32 options**. If a scene has more, validation must flag it or the interpreter input must be safely bounded; do not silently omit valid options while still allowing the model to pick them.
- Count Unicode code points, not UTF-16 code units, so supplementary characters are not counted as two.
- Never silently truncate input to make it fit. Return a safe size-limit issue and allow the player to retry. The UI may show a remaining-character counter.
- Reject NUL and disallowed control characters. Normalize CRLF/CR line endings and Unicode text to NFC; trim leading/trailing whitespace. Preserve legitimate punctuation and language-specific characters.
- Keep free-text input to one submission. Do not treat arbitrary newlines as multiple commands. The engine may collapse them to spaces or reject them consistently; implement one deterministic policy and test it.

## 5. Request processing order

Every scene `SubmitText` and every `FREE_TEXT` start input must follow this order. Do not skip or reorder the local privacy steps:

1. **Check context.** Confirm the active pack allows free text, the current input mode accepts it, the run is not ended, and the input belongs to the expected prompt. Otherwise return the existing command error or `FREE_TEXT_DISABLED`.
2. **Normalize and bound.** Apply the Unicode/control/length rules from §4.3. Invalid input is rejected with no state changes.
3. **Scrub PII locally.** Replace detectable PII with typed placeholders (see §6). Do not put the raw string into exceptions, issues, log fields, or debug `toString()` output.
4. **Inspect the scrubbed text.** Call the configured `InputGuard` with only safe text plus minimal policy context. If there is no configured guard, it fails, times out, or returns an invalid result, treat it as unavailable and fail closed.
5. **Interpret only approved safe text.** On `ALLOW` or `ALLOW_REDACTED`, call the configured `InputInterpreter`, if available. Send only the scrubbed text, current visible/enabled options, locale tag, and relevant pack policy tags. Do not send full snapshots, hidden options, other runs, child-profile details, OAuth data, IDs that identify a child, or the raw input.
6. **Validate the structured resolution.** Validate output shape, option ID, and reply constraints locally. Never execute arbitrary model text as a command.
7. **Check output safety.** Run generated reply text through local scrubbing and the output safety check before display. An unsafe or invalid reply falls back to a generic localized clarification/no-match response and does not change run state.
8. **Apply only approved game actions.** For `SELECT_OPTION`, invoke the same code path as `Command.Choose` after confirming the option is visible and enabled. For `SHORT_REPLY`, show a transient text block and keep the scene/game state unchanged. For `NO_MATCH`, ask the player to select an option or rephrase, without advancing. For `BLOCKED`, show a neutral localized safe message without quoting or describing the blocked input.
9. **Drop request references.** Do not place raw or scrubbed input in long-lived engine objects, `RunState`, snapshot data, event payloads, logs, or stores. Java cannot guarantee immediate physical memory erasure of immutable `String`; the requirement is no intentional retention or reuse after the request completes.

A later spec may combine steps 4–6 with one `TextGenerator` call internally, but the safety and validation boundaries must remain observable and testable.

## 6. PII scrubbing

### 6.1 Baseline behavior

Implement a deterministic local `PiiScrubber` (or equivalent helper in the existing input package). It must run before any network-capable guard/interpreter adapter. It should recognize and redact, at minimum:

- email addresses;
- phone-like number sequences, including common international prefixes and separators;
- URLs and obvious social/contact handles;
- long numeric identifiers likely to be account, document, or membership numbers;
- explicitly formatted dates of birth when they appear in an obvious full-date form, where practical.

Use typed placeholders such as `[redacted:email]`, `[redacted:phone]`, `[redacted:url]`, `[redacted:identifier]`, and `[redacted:date]`. Placeholder spelling is internal data and must not be interpreted as a user instruction. Keep the scrubbing result deterministic and avoid depending on the machine locale.

Do not attempt to guarantee regex detection of names, school names, exact addresses, or implied identities. For content that looks like it contains such details but cannot be reliably scrubbed, the guard should be able to return `BLOCK` or `NEEDS_CLARIFICATION`. Product/UI copy should ask players not to enter full names, contact details, exact home addresses, school/class details, passwords, or other identifying information.

### 6.2 PII scrubber contract

Recommended immutable result:

```java
public record ScrubResult(
    String safeText,
    boolean changed,
    Set<PiiKind> detectedKinds
) {}

public enum PiiKind {
    EMAIL, PHONE, URL, CONTACT_HANDLE, IDENTIFIER, DATE
}
```

Adapt names to existing conventions. `ScrubResult` must not keep a `rawText` field. Any `toString()`/debug representation must not render `safeText`, since even scrubbed text can still contain personal context.

### 6.3 Scrubber constraints

- Redact before moderation/interpreter services and before any content-capable observability hook.
- Do not log the original or redacted text.
- Do not hash input text for correlation. A hash of predictable text can be vulnerable to guessing and is still content-derived.
- Do not mutate the player's input in place in the visible UI without explanation. It is acceptable to show a concise message such as “Personal contact details were removed”; never quote the detected details.
- Scrub outputs too. Generated replies may accidentally repeat private information from context.
- PII scrubbing must not turn a blocked input into an allowed input by itself. A successful scrub is not a moderation verdict.

## 7. Safety guard contract

### 7.1 Port

Add or adapt a provider-neutral port in the existing engine port package. Recommended minimal shape:

```java
public interface InputGuard {
    InputSafetyDecision inspect(SafeTextInput input, InputPolicy policy);
}

public enum InputSafetyAction {
    ALLOW,
    ALLOW_REDACTED,
    BLOCK,
    NEEDS_CLARIFICATION,
    UNAVAILABLE
}

public record InputSafetyDecision(
    InputSafetyAction action,
    SafetyCategory category,
    String reasonCode
) {}
```

The exact type names may follow existing package conventions, but do not include input text in the decision object. `reasonCode` must be a closed, non-user-controlled code. `category` is optional/closed and contains only broad categories, not detailed content or quotes.

Do not put provider SDKs, HTTP clients, Spring types, JSON library nodes, or provider/model names in `engine/core`. A remote moderation adapter, when added, belongs outside the pure core and may be wired in spec 26 or the host integration spec.

### 7.2 Closed categories

Use a small generic set adequate for policy and diagnostics; avoid provider-specific taxonomy leaking through the public API:

- `PERSONAL_DATA`
- `SELF_HARM_OR_CRISIS`
- `SEXUAL_OR_EXPLOITATIVE_CONTENT`
- `VIOLENCE_OR_DANGEROUS_INSTRUCTIONS`
- `HARASSMENT_OR_HATE`
- `ILLEGAL_OR_AGE_RESTRICTED_ACTIVITY`
- `PROMPT_INJECTION_OR_POLICY_BYPASS`
- `OTHER_UNSAFE_CONTENT`
- `UNKNOWN`

These are broad labels only. Do not store or repeat the user's unsafe text in order to explain a decision. The host may display an age-appropriate, localized generic message and, for a genuine crisis classification, follow a separately reviewed support UX; the engine itself must not generate detailed guidance about harmful acts.

### 7.3 Fail-closed behavior

- If a production host requires a moderation provider and it is unavailable, `SubmitText` returns `INPUT_GUARD_UNAVAILABLE`, rejected, and leaves state untouched.
- If no `InputInterpreter` is wired, return `INPUT_INTERPRETER_UNAVAILABLE`, rejected, and leave state untouched. Do not echo the submitted text as a fallback.
- Authored choice commands remain available when text services are unavailable.
- A local deterministic rule checker is useful defense in depth and tests; it is not a claim of comprehensive moderation. Do not label a no-op/allow-all stub as production-safe.
- Tests may use explicit fake implementations. Fake allow-all behavior must be named clearly and confined to tests/fixtures.

## 8. Input interpretation contract

### 8.1 Result types

Interpretation returns structured data, not free-form commands. Recommended shape:

```java
public sealed interface InputResolution {
    record SelectOption(String optionId) implements InputResolution {}
    record ShortReply(String text) implements InputResolution {}
    record NoMatch(String reasonCode) implements InputResolution {}
    record Blocked(String reasonCode) implements InputResolution {}
}
```

Adapt Java version/type style to the repository. The resolver must not be allowed to return a `Command`, `Effect`, `RunState`, arbitrary event, scene ID, place ID, or state update. `reasonCode` is a closed code, not model-authored prose.

### 8.2 Interpreter input context

Pass only:

- scrubbed `SafeTextInput`;
- current locale tag;
- current input mode and configured character limit;
- the current **visible** options with their IDs and labels, and the IDs of those that are enabled;
- pack `audience` / `forbiddenTags` and the global policy version;
- a non-identifying correlation ID only if strictly necessary.

Do not pass hidden options or their IDs, internal state maps/counters/flags, snapshots, profile fields, stored check-in notes, full conversation history, authentication/session data, or other runs. If some game state is needed for interpretation later, the owning spec must identify each minimal field and prove that it does not expose hidden options or private data.

The interpreter may return `SelectOption` only for an option in the supplied visible/enabled set. A model's confidence score is not authoritative; the adapter should return `NoMatch` when uncertain. Never guess the player's intent by selecting an option if the resolution is uncertain.

### 8.3 Resolution semantics

- `SelectOption`: validate its ID against the current `View`; if unknown, hidden, or disabled, reject with `INPUT_RESOLUTION_INVALID`. Otherwise route through the same option-application logic as `Choose`, so requirements, effects, objectives, events, step handling, and end semantics are not duplicated.
- `ShortReply`: enforce at most 280 Unicode code points and three lines; locally scrub PII and safety-check it. Render as a transient text block through `StepResult.view` (or the existing equivalent). It does not execute an option, apply effects, change place, emit game events, increment the logical game step, or alter objectives. The next ordinary `view()` may return to the current authored scene without retaining the exchange.
- `NoMatch`: rejected, no state changes. Return a safe issue code and a localized generic prompt to choose an option or rephrase. Do not include player text in the message.
- `Blocked`: rejected, no state changes. Return a neutral safety response. Never describe the unsafe category with a quote or expose the original string in logs.
- Malformed/unknown result type: treat as invalid, fail closed, and use the same no-state-change behavior.

### 8.4 Prompt injection and output constraints

The provider adapter added in spec 26 must:

- treat all player-provided text and retrieved content as untrusted data;
- never concatenate user text into a system/developer instruction string;
- delimit user input as a distinct data field and instruct the model to classify/map only within the supplied visible choices;
- return schema-constrained structured data;
- not let user text change model, tools, budgets, safety settings, system prompts, pack files, or allowed output types;
- avoid echoing names/contact details and keep replies age-appropriate and related to the current scene;
- run output through the same post-generation safety path before display.

This spec does not choose a model or implement prompt assembly; those belong to spec 26.

## 9. Public API and runtime behavior

### 9.1 `Command.SubmitText`

Reuse the existing reserved command `SubmitText(String text)` if it still exists. Do not add a second text command. Keep `Command` sealed and API objects immutable.

Expected handling:

- ended run → retain the existing `RUN_ENDED` behavior;
- free text disabled by pack or host policy → `FREE_TEXT_DISABLED`;
- mode `NONE`/`CHOICE` → retain `INPUT_NOT_EXPECTED` or a more specific compatible issue;
- too long/invalid characters → `INPUT_TOO_LONG` / `INPUT_INVALID_CHARACTERS`;
- PII scrubbed → continue with scrubbed text and an optional content-free `INPUT_REDACTED` warning;
- guard/interpreter unavailable → reject with the relevant content-free issue;
- safe `SelectOption` → process as `Choose`;
- `ShortReply` → return transient reply with no game-state transition;
- no match/blocked/invalid resolution → reject with no state transition.

Use the repository's existing issue shape and severity conventions. Messages must be localized through a host/pack-safe mechanism; issue messages may not interpolate player text or provider response snippets.

### 9.2 Start input privacy

Apply the same validation/scrubbing/guard policy to manifest start inputs of type `FREE_TEXT`. Text start inputs must obey a maximum of 500 code points and any stricter authored limit.

The current snapshot model serializes `RunSnapshot.startInputs`. To satisfy the no-raw-text invariant:

- Validate and consume free-text start inputs at run start, but do not retain their value in `RunImpl`, `RunState`, events, or the persistent snapshot.
- When constructing a snapshot, include only non-free-text start-input values. For text input keys, omit the value rather than writing raw text, redacted text, a content hash, or a reversible token.
- Snapshot resume must not fail merely because a free-text start-input value was intentionally omitted. On resume, the already-materialized run state/artifacts determine the current run; it must not re-request the original text.
- Keep the distinction between “not supplied” and “supplied but intentionally not persisted” internal if required for validation, without storing the content.
- Do not claim deterministic re-execution from a snapshot by reprocessing the original free-text input. Deterministic replay of text interpretation requires a recorded, content-free resolution (e.g. accepted option ID) or persisted generated artifact, not the original user string.

If current resume code validates every start-input key against required input declarations, adapt it so privacy omission is an explicit, tested case. Do not silently store user text to avoid touching resume logic.

### 9.3 Events

Do not add events that contain text, detected PII values, moderation provider payloads, or prompt content. If an audit-style event is necessary, use only a closed event ID and a minimal non-content payload with bounded codes. Do not emit raw user input as a pack event. Game events and safety diagnostics are separate concerns.

## 10. Privacy, retention, logging and observability

### 10.1 Retention defaults

- Raw free text is request-scoped and must not be intentionally retained after the command/start-input processing completes.
- Redacted text remains sensitive context. It must not be written to persistent state, a `RunSnapshot`, `RunStore`, browser `localStorage`, `IndexedDB` offline write queue, service-worker cache, analytics, error reports, or ordinary application logs.
- For offline hosts, do not queue a free-text input for later processing. If the input needs a remote guard/interpreter and the host is offline, tell the player the text feature is temporarily unavailable and leave ordinary choices usable.
- Generated, validated game artifacts may be persisted under the separate rules of spec 26/27; do not persist original or redacted prompts along with those artifacts by default.
- Do not claim a third-party provider does not retain data unless its actual policy/contract has been verified and is documented by the host. If that verification has not happened, production hosts must not send text to that provider.
- This engine specification does not authorize persistent storage of check-in notes or other non-game product text. Those are governed by the relevant application data policy and should not be copied into the engine input context.

### 10.2 Allowed diagnostic fields

Operational logs may contain only content-free metadata, for example:

- event code (`INPUT_ACCEPTED`, `INPUT_REDACTED`, `INPUT_BLOCKED`, `INPUT_NO_MATCH`, `INPUT_SERVICE_UNAVAILABLE`);
- policy version, pack ID/version, locale, input mode;
- bounded length bucket (`0–49`, `50–149`, `150–299`, `300–500`) rather than exact length if possible;
- broad closed safety category and stable reason code;
- duration bucket and success/failure outcome;
- a random request correlation ID that cannot identify a profile on its own, if correlation is needed.

Do not log input or output strings, fragments, prompts, tokens, model request/response bodies, content hashes, detected PII values, profile IDs, OAuth subjects, email addresses, exact birth dates, exact addresses, or user-provided labels. Do not place text into exception messages. Disable request-body logging for endpoints that can carry free text.

### 10.3 Testable no-content logging

Inject a test logger/appender and submit synthetic strings containing distinctive markers and fake PII. Assert those exact markers do not appear in captured logs, stack traces, issue messages, events, snapshots, serialized snapshots, or generated exception messages. Test successful, scrubbed, blocked, timeout, malformed-response, and unexpected-exception paths.

### 10.4 App and web host obligations

This spec defines host requirements now; it does not connect the engine to the app yet. The later API/host integration spec must implement and test these requirements for the actual HTTP/UI flow:

- Do not log request bodies on free-text routes.
- Do not put text inputs into IndexedDB offline writes or `localStorage`.
- Do not include free-text values in analytics, crash reports, replay tools, session recording, or client-side error breadcrumbs.
- Clear the active input from in-memory UI state after completion, navigation, cancellation, or policy failure when possible; never place it into a URL or browser history.
- Show a short privacy explanation before the first free-text submission and do not ask for names, contact information, exact addresses, school/class, passwords, or other unnecessary identifiers.
- If a remote provider is used, document what category of data is sent, to whom, under which retention terms, and what happens when the provider is unavailable.
- Enforce per-child/request budgets in the host (spec 26/27), not by adding child identity or quota logic to the universal engine core.

## 11. Validation and issue codes

Add issue codes consistent with the existing uppercase underscore convention. Reuse existing issues where semantics already match.

| Code | Severity | Meaning |
|---|---|---|
| `INPUT_MODE_INVALID` | ERROR | Authored input mode is unknown or incompatible with scene options. |
| `INPUT_LIMIT_INVALID` | ERROR | Authored `maxChars` is zero, negative, or greater than the global cap for a text mode. |
| `FREE_TEXT_DISABLED` | ERROR | Pack or host policy disallows free text. |
| `INPUT_TOO_LONG` | ERROR | Submitted text exceeds the effective code-point limit. |
| `INPUT_INVALID_CHARACTERS` | ERROR | Input contains prohibited controls or invalid content encoding. |
| `INPUT_GUARD_UNAVAILABLE` | ERROR | Required guard is absent, unavailable, times out, or returns invalid data. |
| `INPUT_BLOCKED` | WARNING or ERROR | Guard blocks input; use existing severity conventions, always rejected/no state change. |
| `INPUT_NEEDS_CLARIFICATION` | WARNING | Guard says the input is ambiguous or requires clarification. |
| `INPUT_INTERPRETER_UNAVAILABLE` | ERROR | Interpreter is not configured or is unavailable. |
| `INPUT_NO_MATCH` | WARNING | No sufficiently certain option mapping was produced. |
| `INPUT_RESOLUTION_INVALID` | ERROR | Interpreter returned malformed output or an unknown/hidden/disabled option. |
| `INPUT_OUTPUT_UNSAFE` | ERROR | Proposed short reply failed output scrubbing/safety checks. |
| `INPUT_REDACTED` | WARNING | PII-like content was scrubbed; must never contain the redacted value. |

Issue message rules:

- No message may contain raw or scrubbed input, model text, PII values, or provider error body.
- Provider exceptions must be converted to stable internal error codes; do not include a raw exception message if it may contain request content.
- Unknown fields in authored content follow the existing `PACK_UNKNOWN_FIELD` convention; known but invalid input fields are errors.
- Validation collects problems using the existing context and does not let malformed pack content throw unchecked exceptions out of `tryLoad`.

## 12. Suggested package/API placement

Adapt to the actual `engine/core` tree rather than forcing the layout if the existing input package already exists. Keep domain types free of JSON-library classes and framework dependencies.

```text
engine/core/src/main/java/com/capybee/engine/
  input/
    InputPolicy.java
    SafeTextInput.java
    PiiKind.java
    ScrubResult.java
    PiiScrubber.java
    InputSafetyAction.java
    SafetyCategory.java
    InputSafetyDecision.java
    InputResolution.java
  port/
    InputGuard.java
    InputInterpreter.java
```

Possible contracts (illustrative; preserve current conventions and Java 25 style):

```java
public record InputPolicy(
    boolean freeTextAllowed,
    int maxInputCodePoints,
    int maxReplyCodePoints,
    int maxReplyLines,
    String audience,
    Set<String> forbiddenTags,
    String policyVersion
) {}

public record SafeTextInput(String text) {
    // Constructor/factory rejects null and values above the policy limit.
    // toString() must not print text.
}

public interface InputGuard {
    InputSafetyDecision inspect(SafeTextInput input, InputPolicy policy);
}

public interface InputInterpreter {
    InputResolution interpret(SafeTextInput input, InputContext context);
}
```

`SafeTextInput` should be created only by the normalizer/scrubber path; do not expose a public factory that lets arbitrary code assert unprocessed text is safe. Keep `InputContext` immutable and limited to the allowed fields in §8.2. Add constructors/builder methods to `Engine` only as necessary; avoid making previously required builder arguments optional.

If there is already an `InputGuard` or an input package in current `main`, extend it instead of defining duplicate abstractions. The old planning handoff placed `InputGuard` in a later spec; this updated plan assigns it to spec 25. Implement the port and safe defaults here, and let spec 26 supply/wire the generation-backed adapter. Do not implement `TextGenerator` itself in this spec.

## 13. Default/reference implementations

- `PiiScrubber`: deterministic, local, dependency-free implementation with unit-tested patterns and typed placeholders.
- `InputGuard`: provide a fail-closed default that returns `UNAVAILABLE` for free text unless a policy-compliant implementation is injected. Do not ship a production `ALLOW_ALL` default.
- `InputInterpreter`: absent/unconfigured by default; return `INPUT_INTERPRETER_UNAVAILABLE` rather than echoing text or auto-selecting an option.
- Tests may inject `FakeAllowGuard` and `FakeInputInterpreter` implementations explicitly. Put them under test sources or fixture test support, not production default configuration.
- The console remains fully playable through authored options with no provider. If a console run requests free-text input while no safety/interpretation adapter is configured, show the safe localized unavailable message and let the player choose an authored option. Do not print the submitted text to logs or echo it into a persistent transcript.

This default is intentionally conservative: generation/provider wiring comes next, and a missing service must not accidentally turn into an allow-all safety path.

## 14. Fixtures and tests

Do not change rules/state/Places semantics or upgrade fixture pack versions for unrelated reasons. If a fixture must exercise input modes, add a small dedicated input fixture or add an explicitly justified, additive input test to the existing generic fixture; preserve `fixture-minimal` unchanged.

### Unit tests

1. Unicode NFC normalization and code-point counting, including supplementary characters.
2. Leading/trailing whitespace, line endings, NUL/control characters, empty input, and newline policy.
3. Input mode validation for `NONE`, `CHOICE`, `TEXT`, and `CHOICE_OR_TEXT`; compatibility when the new scene field is omitted.
4. `freeTextAllowed: false` rejects `SubmitText` even if a scene says `CHOICE_OR_TEXT`.
5. Per-pack limits can tighten but never exceed the 500-code-point cap.
6. PII scrubber covers e-mail, phone, URL, contact handle, long identifier, and obvious full date; result and `toString()` do not reveal raw text.
7. Guard sees scrubbed text only. Add a sentinel fake guard that records its input and assert original PII is absent.
8. Guard absent/failure/timeout/invalid response fails closed, and ordinary `Choose` remains usable.
9. Interpreter absent/failure/invalid response does not alter any run state.
10. `SelectOption` succeeds only for visible/enabled current option IDs and follows the exact `Choose` code path.
11. Hidden, disabled, stale, or invented option IDs are rejected with no state changes, no event, no step increment, and no place/state mutation.
12. `ShortReply` length, line count, PII scrub, and safety checks. It does not advance or apply effects/objectives/events.
13. `NoMatch`/`Blocked` response is generic and contains no user text.
14. Unsafe reply is not shown; fallback is localized and safe.
15. Prompt-injection-like strings remain ordinary data and cannot change the output schema or action vocabulary.
16. Snapshot serialization excludes free-text start-input values while preserving non-text start inputs. Resume succeeds without requiring the omitted text value.
17. Legacy snapshot with a free-text value is sanitized/omitted at the correct boundary before it is re-saved; no log or exception leaks the legacy value.
18. Events and metrics never contain raw/scrubbed text, text hash, prompt, or provider payload.
19. Structured logging tests prove synthetic sentinel markers never appear under success or error paths.
20. No-framework/no-network boundary tests remain green; `engine/core` has no provider SDK or web client dependency.

### Integration/golden tests

- A text-enabled scene with safe input mapped to a valid option executes the same scene transition, effects, objectives and events as direct `Choose`.
- A text-enabled scene with a short-reply result shows the reply once and leaves the authored scene/state unchanged.
- A blocked or unavailable text request produces a generic safe response; the current scene remains playable by choosing an authored option.
- A scene with only authored choices works identically with no `InputGuard`, no interpreter, no network, and no provider key.
- Console script fixtures use synthetic input only. Preserve the existing `--script` transcript contract, but ensure production logging is separate from script-echo behavior and no raw inputs are written to diagnostic logs.

## 15. Build and implementation boundaries

- Main production changes are in `engine/core`; console user-facing behavior changes belong in `engine/cli`.
- Do not change `app/server`, app DB migrations, OAuth flow, deployment, or UI in this spec. Record the host obligations for the later web/API integration spec as described above.
- Do not add Spring, JPA/Hibernate, HTTP clients, cloud SDKs, model SDKs, or logging dependencies that carry bodies into core.
- Reuse existing `SectionValidator`, `PackValidationContext`, `ReferenceIndex`, issue convention, immutable domain objects, and JSON package boundaries.
- Keep all checks synchronous in v1; no background job, async executor, or callback lifecycle is added here.
- Respect existing time/ID injection. Do not introduce wall-clock access, `UUID.randomUUID()`, static mutable state, or non-deterministic logic into core.
- Preserve source and binary compatibility where feasible using overloaded constructors/builders. If the sealed command or record shapes must change, update all callers/tests and document why.
- A rejected command must return a `StepResult` with the current view and issues, and must not partially mutate state before safety decisions are complete.

## 16. Acceptance criteria

Implementation is complete when all of the following hold:

- [ ] Backward-compatible input mode support is validated from authored scene content.
- [ ] `SubmitText` is rejected in contexts that do not allow text and is accepted for processing only when policy permits it.
- [ ] Input limits use Unicode code points, enforce the global cap, and never silently truncate.
- [ ] PII is scrubbed locally before any external-capable port; no raw text is sent to `InputGuard` or `InputInterpreter`.
- [ ] Missing/unavailable safety or interpretation services fail closed, but choice-only play works offline/provider-free.
- [ ] Interpreter results are closed-world and cannot introduce arbitrary commands, effects, state changes, scenes, places, or events.
- [ ] Output replies are bounded, scrubbed, safety checked, and transient; they do not advance the world.
- [ ] Raw and redacted free text are absent from snapshots/stores/events/logs/metrics/exception messages and are not queued offline.
- [ ] Free-text start input values are omitted from persistent snapshots and snapshot resume tests pass.
- [ ] Prompt-injection boundaries and no-content diagnostic tests pass.
- [ ] Existing specs 22, 23, 24, and 24.1 acceptance tests remain green, including existing console, template, rules/state, and Places behavior where present in the current implementation.
- [ ] `mvn -f engine/pom.xml clean verify` passes under the repository's supported Java version; record the exact command and runtime version.
- [ ] Core dependency-boundary tests remain green; no framework, provider SDK, or network dependency enters `engine/core`.
- [ ] Every mismatch found between this document and the actual current `main` source has been listed in the implementation PR or commit notes rather than silently ignored.

## 17. Decisions / assumptions to revisit only if current source or intent contradicts them

| Question | Default in this spec | Why |
|---|---|---|
| Is free text enabled by default? | No; require `freeTextAllowed: true` and a text-capable input mode. | Prevent accidental exposure for older packs. |
| What is the hard text limit? | 500 code points; default 300; pack may only lower it. | Bounded cost and limited personal context. |
| What if moderation/interpreter is missing? | Fail closed for text; choices remain available. | A missing adapter must not become an allow-all path. |
| Can a short reply change state? | No; it is transient and state-neutral. | Only authored enabled options execute game logic. |
| Can a free-text value be saved in `RunSnapshot.startInputs`? | No; omit that key's value and permit resume without it. | Snapshot fields otherwise create accidental long-term retention. |
| Can the pack weaken PII/moderation/retention rules? | No. | Privacy guarantees are global constraints. |
| Is PII scrubbing complete? | No; regex detection is defense in depth and moderation can still block uncertain text. | Avoid a false safety guarantee. |
| Does this spec implement provider-specific moderation? | No; it defines the port and fail-closed behavior. Provider integration belongs to spec 26/host wiring. | Keep the core provider-agnostic and console-testable. |

## 18. Rollout checklist

1. Re-open the current `main` implementations of `Command.SubmitText`, `InputSpec`, scene parsing/validation, `RunRequest`, `RunSnapshot`, `SnapshotCodec`, and `EngineBuilder`; reconcile signatures with this contract.
2. Read intent note 01 and specs 22–24.1 before changing code. Treat this document as additive unless an explicit conflict is documented.
3. Implement closed input-mode validation, normalization/PII scrubber, input guard/interpreter ports, and fail-closed runtime path.
4. Add privacy-safe issue handling and ensure exceptions, diagnostic fields and `toString()` methods do not print text.
5. Prevent free-text start inputs from entering snapshots and add legacy-snapshot privacy tests.
6. Add fake-based tests for all outcomes, no-content logging assertions and choice-path regression tests.
7. Run the full engine verification command and inspect the repository diff for accidental fixture, `app/server`, or schema changes.
8. In spec 26, wire structured interpretation to the generation pipeline without bypassing any guard, scrubber, output validation, privacy, or closed-world constraints in this spec.
