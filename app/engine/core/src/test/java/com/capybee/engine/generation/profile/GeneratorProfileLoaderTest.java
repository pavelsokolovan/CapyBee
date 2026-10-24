package com.capybee.engine.generation.profile;

import static org.assertj.core.api.Assertions.assertThat;

import com.capybee.engine.generation.profile.json.GeneratorProfileLoader;
import com.capybee.engine.pack.LocaleTag;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GeneratorProfileLoaderTest {
    private final GeneratorProfileLoader loader = new GeneratorProfileLoader();

    @Test void loadsNeutralProfileAndResolvesDefaultsWithoutProvider() {
        ProfileLoadResult loaded = loader.load(bundle(profileJson()));
        assertThat(loaded.issues()).isEmpty();
        assertThat(loaded.profile()).isPresent();
        var profile = loaded.profile().orElseThrow();
        assertThat(profile.profile().supportedLocales()).extracting(LocaleTag::value).containsExactly("en", "pl");

        var result = new PreparationPresetResolver().resolve(profile, Map.of(), null, HostGenerationCeilings.none());
        assertThat(result.issues()).isEmpty();
        assertThat(result.preferences().orElseThrow().gameLocale().value()).isEqualTo("en");
        assertThat(result.preferences().orElseThrow().lengthPresetId()).isEqualTo("short");
        assertThat(result.constraints().orElseThrow().maxScenes()).isEqualTo(12);
    }

    @Test void fingerprintIgnoresJsonWhitespaceButChangesWithPrompt() {
        var original = loader.load(bundle(profileJson())).profile().orElseThrow();
        String first = original.identity().fingerprint();
        Map<String, byte[]> spaced = bundle("\n  " + profileJson() + "\n");
        String second = loader.load(spaced).profile().orElseThrow().identity().fingerprint();
        assertThat(second).isEqualTo(first);
        Map<String, byte[]> changed = bundle(profileJson());
        changed.put("prompts/en/blueprint.md", bytes("Different neutral instructions."));
        var modified = loader.load(changed).profile().orElseThrow();
        assertThat(modified.identity().fingerprint()).isNotEqualTo(first);
        var registry = new GeneratorProfileRegistry();
        assertThat(registry.register(original)).isEmpty();
        assertThat(registry.register(modified).orElseThrow().code()).isEqualTo("PROFILE_IDENTITY_CONFLICT");
    }

    @Test void rejectsUnknownFieldsTraversalAndUnsupportedMechanics() {
        Map<String, byte[]> unknown = bundle(profileJson().replace("\"schemaVersion\":1", "\"schemaVersion\":1,\"surprise\":true"));
        assertThat(loader.load(unknown).issues()).extracting(ProfileIssue::code).contains("PROFILE_UNKNOWN_FIELD");

        Map<String, byte[]> traversal = bundle(profileJson());
        traversal.put("../secret.json", bytes("{}"));
        assertThat(loader.load(traversal).issues()).extracting(ProfileIssue::code).contains("PROFILE_RESOURCE_INVALID");

        Map<String, byte[]> mechanic = bundle(profileJson().replace(
                "\"allowedEffects\":[\"FLAG_SET\",\"COUNTER_ADD\"]}",
                "\"allowedEffects\":[\"FLAG_SET\",\"COUNTER_ADD\",\"END_RUN\"]}"));
        assertThat(loader.load(mechanic).issues()).extracting(ProfileIssue::code).contains("PROFILE_CAPABILITY_UNSUPPORTED");
    }

    @Test void resolverDistinguishesClearOrdersSetsAndNeverEchoesTransientText() {
        var profile = loader.load(bundle(profileJson())).profile().orElseThrow();
        Map<String, PreparationAnswer> selected = Map.of(
                "tone", new PreparationAnswer.Cleared(),
                "interests", new PreparationAnswer.Selected(List.of("nature", "animals")));
        var result = new PreparationPresetResolver().resolve(profile, selected, new LocaleTag("en"), null);
        assertThat(result.preferences().orElseThrow().tone()).isEmpty();
        assertThat(result.preferences().orElseThrow().interests()).containsExactly("animals", "nature");

        String privateText = "Private Person at 123 Secret Street";
        var rejected = new PreparationPresetResolver().resolve(profile,
                Map.of("story-wish", PreparationAnswer.Selected.one(privateText)), null, null);
        assertThat(rejected.issues()).extracting(ProfileIssue::code).contains("PREPARATION_TRANSIENT_INPUT_REQUIRED");
        assertThat(rejected.issues().toString()).doesNotContain(privateText);
    }

    @Test void localeConflictAndImpossibleHostCeilingFailClosed() {
        var profile = loader.load(bundle(profileJson())).profile().orElseThrow();
        var conflict = new PreparationPresetResolver().resolve(profile,
                Map.of("game-locale", PreparationAnswer.Selected.one("pl")), new LocaleTag("en"), null);
        assertThat(conflict.issues()).extracting(ProfileIssue::code).contains("PREPARATION_LOCALE_CONFLICT");
        var impossible = new PreparationPresetResolver().resolve(profile, Map.of(), null,
                new HostGenerationCeilings(5, null, null, null, null, null, null, null, null, null));
        assertThat(impossible.issues()).extracting(ProfileIssue::code).contains("PREPARATION_CONSTRAINTS_UNSATISFIABLE");
    }

    private static Map<String, byte[]> bundle(String profile) {
        Map<String, byte[]> files = new LinkedHashMap<>();
        files.put("profile.json", bytes(profile));
        files.put("setup.json", bytes(setupJson()));
        files.put("strings/en.json", bytes(stringsJson("English")));
        files.put("strings/pl.json", bytes(stringsJson("Polski")));
        files.put("prompts/en/blueprint.md", bytes("Create a bounded neutral adventure blueprint."));
        files.put("prompts/pl/blueprint.md", bytes("Utwórz neutralny plan przygody."));
        files.put("quality/en.json", bytes("{}"));
        files.put("quality/pl.json", bytes("{}"));
        return files;
    }
    private static byte[] bytes(String value) { return value.getBytes(StandardCharsets.UTF_8); }

    private static String profileJson() { return """
        {"schemaVersion":1,"id":"generic-adventure","version":"1.0.0","titleKey":"profile.title","descriptionKey":"profile.description",
         "supportedLocales":["en","pl"],"defaultLocale":"en","setupResource":"setup.json",
         "stringResources":{"en":"strings/en.json","pl":"strings/pl.json"},
         "blueprintPromptResources":{"en":"prompts/en/blueprint.md","pl":"prompts/pl/blueprint.md"},
         "qualityResources":{"en":"quality/en.json","pl":"quality/pl.json"},
         "policy":{"audience":{"minAge":10,"maxAge":14},"freeTextAllowed":true,"maxSuggestionCodePoints":300,"forbiddenTags":["personal-data"]},
         "capabilities":{"phaseKinds":["OPENING","DEVELOPMENT","CHALLENGE","RESOLUTION","ENDING"],"graphKind":"FORWARD_DAG","placesEnabled":false,
           "minScenes":6,"maxScenes":18,"maxChoicesPerScene":3,"minActors":1,"maxActors":6,"minObjectives":1,"maxObjectives":4,"minEndings":2,"maxEndings":3,
           "maxCounters":2,"maxFlags":6,"maxSets":1,"maxSetValues":4,"counterMin":0,"counterMax":6,"maxCounterDelta":1,"maxConditionDepth":3,
           "maxConditionNodes":12,"maxEffectsPerChoice":3,"maxExploredStates":20000,
           "allowedConditions":["FLAG_IS","COUNTER_AT_LEAST","VISITED_SCENE","ALL","ANY"],"allowedEffects":["FLAG_SET","COUNTER_ADD"]},
         "lengthPresets":[{"id":"short","labelKey":"length.short","minStoryTransitions":5,"maxStoryTransitions":7,"maxScenes":12},{"id":"medium","labelKey":"length.medium","minStoryTransitions":8,"maxStoryTransitions":10,"maxScenes":18}],
         "challengePresets":[{"id":"gentle","labelKey":"challenge.gentle","maxCounters":0,"maxFlags":4,"maxSets":0,"maxConditionDepth":2,"maxEffectsPerChoice":2,"allowedConditions":["FLAG_IS","VISITED_SCENE","ALL","ANY"],"allowedEffects":["FLAG_SET"]},{"id":"standard","labelKey":"challenge.standard","maxCounters":2,"maxFlags":6,"maxSets":0,"maxConditionDepth":3,"maxEffectsPerChoice":3,"allowedConditions":["FLAG_IS","COUNTER_AT_LEAST","VISITED_SCENE","ALL","ANY"],"allowedEffects":["FLAG_SET","COUNTER_ADD"]}],
         "generationBudget":{"totalCallsPerRun":32,"blueprintMaxAttempts":2,"blueprintMaxOutputTokens":6000,"sceneMaxAttempts":2,"sceneMaxOutputTokens":1200,"maxInputCharacters":24000},
         "fallbacks":[],"files":["setup.json","strings/en.json","strings/pl.json","prompts/en/blueprint.md","prompts/pl/blueprint.md","quality/en.json","quality/pl.json"]}
        """; }

    private static String setupJson() { return """
        {"schemaVersion":1,"steps":[
          {"id":"feel","titleKey":"step.feel","inputIds":["tone","interests"]},
          {"id":"scope","titleKey":"step.scope","inputIds":["length","challenge"]},
          {"id":"language","titleKey":"step.language","inputIds":["game-locale"]},
          {"id":"idea","titleKey":"step.idea","inputIds":["story-wish"]}],
         "inputs":[
          {"id":"tone","kind":"ENUM","required":false,"labelKey":"setup.tone","values":["cozy","curious"]},
          {"id":"interests","kind":"ENUM_SET","required":false,"labelKey":"setup.interests","values":["animals","nature"],"maxItems":2},
          {"id":"length","kind":"ENUM","required":true,"labelKey":"setup.length","values":["short","medium"]},
          {"id":"challenge","kind":"ENUM","required":true,"labelKey":"setup.challenge","values":["gentle","standard"]},
          {"id":"game-locale","kind":"ENUM","required":true,"labelKey":"setup.locale","values":["en","pl"]},
          {"id":"story-wish","kind":"FREE_TEXT","required":false,"labelKey":"setup.wish","maxChars":300}],
         "questions":[
          {"inputId":"tone","helpKey":"help.tone","defaultValues":["curious"],"options":[{"value":"cozy","labelKey":"tone.cozy"},{"value":"curious","labelKey":"tone.curious"}]},
          {"inputId":"interests","helpKey":"help.interests","defaultValues":[],"options":[{"value":"animals","labelKey":"interest.animals"},{"value":"nature","labelKey":"interest.nature"}]},
          {"inputId":"length","helpKey":"help.length","defaultValues":["short"],"options":[{"value":"short","labelKey":"length.short"},{"value":"medium","labelKey":"length.medium"}]},
          {"inputId":"challenge","helpKey":"help.challenge","defaultValues":["gentle"],"options":[{"value":"gentle","labelKey":"challenge.gentle"},{"value":"standard","labelKey":"challenge.standard"}]},
          {"inputId":"game-locale","helpKey":"help.locale","defaultValues":["en"],"options":[{"value":"en","labelKey":"locale.en"},{"value":"pl","labelKey":"locale.pl"}]},
          {"inputId":"story-wish","helpKey":"help.wish","defaultValues":[],"options":[],"freeTextNoticeKey":"notice.wish"}]}
        """; }

    private static String stringsJson(String language) { return """
        {"profile.title":"%s profile","profile.description":"Neutral adventures",
         "step.feel":"Feel","step.scope":"Scope","step.language":"Language","step.idea":"Idea",
         "setup.tone":"Tone","setup.interests":"Interests","setup.length":"Length","setup.challenge":"Challenge","setup.locale":"Language","setup.wish":"Idea",
         "help.tone":"Choose a tone","help.interests":"Choose interests","help.length":"Choose length","help.challenge":"Mechanical complexity","help.locale":"Choose game language",
         "help.wish":"Optional fictional idea. Do not enter real names, contacts, exact locations, schools or secrets. Saved story content may reflect the idea.",
         "notice.wish":"This optional idea may be sent to an external service after confirmation.",
         "tone.cozy":"Cozy","tone.curious":"Curious","interest.animals":"Animals","interest.nature":"Nature",
         "length.short":"Short","length.medium":"Medium","challenge.gentle":"Gentle","challenge.standard":"Standard","locale.en":"English","locale.pl":"Polish"}
        """.formatted(language); }
}
