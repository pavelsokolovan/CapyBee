# Spec 41 — CapyBee generated-text limits

**Status** — Implemented. See [acceptance](../game-generated-text-limits-acceptance.md).

**Baseline** — `df095ac` on `main`, after spec 39.

**Intent** — [Illustrated Honeycomb RPG](intent/03-illustrated-honeycomb-rpg.md)

**Depends on** — Specs 27.11 and 27.25 for generation. Coordinate the Hive fallback copy and Pack release with spec 40. Specs 37–39 provide the browser baseline.

Keep Polish generated scenes quick to read on a phone. Give the model the
same word limits that the engine enforces, and use complete, playable fallback
copy when an attempt fails the check.

## Baseline

- The Hive Pack's `generation/quality/pl.json` limits sentence and paragraph
  length in code points. Its lantern fallback still has a narration string
  longer than 20 words. `GenerationPipeline` already checks generated scenes,
  gives eligible failures one repair attempt, and validates authored fallbacks.
- Dynamic runs already have a separate policy resource in
  `app/engine/generator-profiles/<profile>/quality/pl.json`.
  `GeneratorProfileLoader` validates that resource, and
  `DefaultBlueprintOutputReviewer` reviews the proposal. Later,
  `DynamicPresentationPipeline` builds each scene's fallback from the accepted
  scene intent and choice intents. A limit applied only to the scene provider
  would leave that fallback unchecked.
- The current Hive release is **1.4.0**. Specs 30.3 and 30.6 already use 1.3.0
  and 1.4.0. Publish the short-copy assets and generation policy together in
  the next unused Pack version, **1.5.0** on this baseline.

## Word limits

Apply these rules to new Polish runs that opt in through their trusted locale
resource. Enable them for the new `capybee-new-hive` Pack and for the Polish
resources in the maintained `generic-adventure` and `classic-detective` profiles.

| Player-facing field | Requirement |
| --- | --- |
| Scene narration | At most 20 words in the entire `narration` string |
| Speech | At most 20 words in each `dialogue[].text` |
| Scene choice label | 2–5 words, inclusive, for every `choices[].label` |
| Dynamic scene and choice intents | Apply the narration and choice limits respectively because the fallback displays these strings |

Use the existing nonblank and code-point rules in addition to these limits.
Apply the narration rule to opening, ordinary and ending scenes. Story and
scene titles, speaker names, setup copy, separate result copy, continuity facts,
and host controls such as **Odpocznij** keep their existing validation. Keep the
current limits for English and any resource without this opt-in.

Count words identically in blueprint review, scene evaluation and fallback
verification. Normalize a copy to Unicode NFC for counting. A word is a run of
Unicode letters, combining marks and decimal digits containing at least one
letter or decimal digit. Join runs across an internal straight or curly
apostrophe, or a hyphen (ASCII hyphen, U+2010 or U+2011). Other punctuation,
whitespace and symbols separate runs. Standalone punctuation and icons count
zero. A line break does not reset a field's count.

| Polish sample | Count |
| --- | --- |
| `W Latarni Wspomnień pali się ciepłe światło.` | 7 |
| `Nie musisz nikomu opowiadać. Możesz tu po prostu postać.` | 9 |
| `🏮 Zabieram światło` | 2 |
| `Idę,patrzę` | 2 |
| `polsko‑angielski` | 1 |
| `🏮 Latarnia` | 1 |

Generate one or two short sentences where natural. Keep labels distinct and
specific enough to tell the child what each action does. Preserve the existing
choice order, IDs and meanings. Use the current payload shape and render the
accepted text exactly as parsed.

## Configuration and prompts

1. Add one optional strict `sceneWordLimits` object to the Pack quality
   resource and to the dynamic locale policy. Use the same three integer
   fields in both resources.

   ```json
   "sceneWordLimits": {
     "maxBlockWords": 20,
     "minChoiceWords": 2,
     "maxChoiceWords": 5
   }
   ```

   Use `GenerationQualityProfileParser` and `GeneratorProfileLoader` to
   reject malformed objects at load time, including unknown or missing keys,
   non-integers, values outside 1–100, and an inverted choice range. Absence
   preserves existing behavior. Include the configured values in existing
   policy identity and fingerprint calculations. Preserve historical Pack
   quality fingerprints for resources without the object.

2. Send the active numerical limits as trusted, scene-specific instructions
   on the initial request and on repair. Update the Hive Polish scene guidance
   in `prompts/pl/system.md` and use the validated policy in
   `DynamicPresentationPipeline`. Update the Polish blueprint prompts and
   blueprint repair guidance for the two opted-in profiles. Say explicitly
   that scene and choice intents must already read like final player copy
   because the fallback can display them verbatim.

3. Give repair the ordinary trusted IDs and context, the violated field paths,
   and the applicable bounds through the existing feedback contracts. Keep
   feedback and diagnostics limited to stable rule IDs, trusted field paths and
   numeric measures. Use blocking readability findings such as
   `Q_READABILITY_SCENE_BLOCK_WORD_LIMIT` and
   `Q_READABILITY_CHOICE_WORD_RANGE` for scene attempts and equivalent
   path-specific findings in blueprint review.

## Validation and fallback

1. Check parsed scene text before composition, caching, prefetch storage or
   persistence. Run the same check for entry and prefetch. Use the existing
   repair allowance when available. If the first attempt cannot be repaired,
   or the repair also fails, take the existing whole-scene fallback route.
   Existing budgets also cover provider-disabled and exhausted-budget paths.
   Keep the current structural, audience, safety, tone and repetition checks.
2. For the Hive Pack, check every Polish scene fallback, including endings
   and directly entered or prefetched destinations, during existing Pack
   validation. Coordinate with spec 40 so the new release's authored scenes
   and scene fallback files ship together. An invalid fallback fails Pack
   validation with its resource and field location.
3. For dynamic runs, check scene intents and choice intents during
   `DefaultBlueprintOutputReviewer` review, before accepting the definition.
   Use the existing single blueprint repair allowance and existing
   reviewed-fallback offer or start-failure behavior if the proposal still
   fails. Validate any offered reviewed blueprint against the active policy.
   Then validate all materialized scene fallbacks during bootstrap, before
   publishing the run. Scene generation failure uses that prevalidated,
   definition-specific fallback with the original choice IDs. A malformed
   policy or invalid fallback blocks publication through the existing
   configuration or fallback error path.
4. Reuse the persisted dynamic continuation policy on restore and future lazy
   generation in that run. Preserve previously accepted scene artifacts,
   Pack identities and copy across resume. Keep the resource bytes for Hive
   1.0.0–1.4.0 and preserve loading of historical dynamic snapshots. Use the
   existing immutable version and packaging workflow when shipping the new
   resources, including updated dynamic profile versions and policy identifiers.
   Commands, mechanics, persisted schemas and public payload shapes retain
   their current contracts.

## Verification

1. **Counting and config.** Cover 20 versus 21 words, choice labels with
   1, 2, 5 and 6 words, Polish diacritics in composed and decomposed form,
   apostrophes, hyphens, digits, icons, punctuation and line breaks. Verify
   sentence and paragraph code-point checks still apply. Test invalid policy
   objects in both loaders and unchanged behavior for older and English
   resources.
2. **Pack and dynamic generation.** Use a deterministic provider to verify
   first-pass acceptance, rejection then successful repair, rejection then
   whole-scene fallback, and fallback with no provider call when generation is
   disabled or the budget is exhausted. Exercise entry and prefetch plus at
   least one ending. Assert that rejected copy never reaches a public view,
   the cache, persisted artifacts, repair input or diagnostics. Assert that
   ordering, IDs and accepted command effects match the fallback route.
3. **Fallback and resume.** Exercise all new Hive scene fallbacks, invalid
   Pack fallback load, dynamic blueprint rejection and repair, and a dynamic
   fallback bootstrap failure. Prove a retained Hive save and a pre-spec
   dynamic save resume with their original text and no provider call.
   Verify an opted-in dynamic save can resume and enforce its persisted
   policy on the next unresolved scene.
4. **360 × 640 browser check.** Use the Spring-served expedition browser
   fixture with a deterministic provider. Verify a Polish scene with three
   actions and copy at the limits, then the same scene reached through
   fallback. At default text size, the art, full bubble and three action
   targets should be visible together without scrolling the beat. Preserve
   spec 37's scrolling behavior for multi-block scenes, the larger hub and
   enlarged text. Check Text mode for full text and equivalent actions.
   Record the fixture, viewport and results with the implementation's
   acceptance evidence.
5. **Gates.** Run `mvn -f app/engine/pom.xml clean verify` and
   `mvn -B -f app/pom.xml -pl server -am verify`. Run the integrated browser
   check and verify that release packaging includes the current and retained
   Pack versions plus the updated profile resources. Use local fixtures for
   generation throughout.
