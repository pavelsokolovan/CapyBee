package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.blueprint.ParsedGameBlueprint.*;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.pack.*;
import com.capybee.engine.rules.Condition;
import com.capybee.engine.rules.Effect;
import com.capybee.engine.rules.ResultClass;
import org.junit.jupiter.api.Test;

import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MaterializedGameCompilerTest {
    @Test void compilesReviewedCandidateDeterministicallyWithTrustedIdsAndRuntimeShapes() {
        Fixture fixture = fixture();
        BlueprintGenerationResult.CandidateReviewed candidate = reviewed(fixture);
        MaterializedGameCompiler compiler = new MaterializedGameCompiler();

        var first = (MaterializedGameCompilationResult.Compiled) compiler.compile(candidate);
        var second = (MaterializedGameCompilationResult.Compiled) compiler.compile(candidate);
        MaterializedGameDefinition definition = first.definition();

        assertThat(second.definition()).isEqualTo(definition);
        assertThat(definition.idBindings().actors().values()).containsExactly("actor-001");
        assertThat(definition.idBindings().scenes().values()).containsExactly(
                "scene-001", "scene-002", "scene-003", "scene-004", "scene-005", "scene-006", "scene-007", "scene-008");
        assertThat(definition.idBindings().choices().get("start").values()).containsExactly("choice-001-001", "choice-001-002");
        assertThat(definition.runtimeSceneSet().startId()).isEqualTo("scene-001");
        assertThat(definition.runtimeSceneSet().scenes().get("scene-001").options())
                .extracting(option -> option.next()).containsExactly("scene-002", "scene-003");
        assertThat(definition.runtimeRules().flags()).extracting(value -> value.id()).containsExactly("flag-001");
        assertThat(definition.runtimeRules().objectives().getFirst().completeWhen())
                .isEqualTo(new Condition.FlagIs("flag-001", true));
        assertThat(definition.runtimeRules().objectives().getFirst().onComplete()).isEmpty();
        assertThat(definition.runtimeRules().resultFacts()).isEmpty();
        assertThat(definition.localizedContent()).containsEntry("story.title", "Signal Path")
                .containsEntry("scene.001.choice.001.intent", "Continue with the plan.");
        assertThat(definition.canonicalScenes().getFirst().options().getFirst().label())
                .isEqualTo(MaterializedGameCompiler.UNRESOLVED_LABEL);
        assertThat(definition.toString()).contains("redacted").doesNotContain("Signal Path");
        assertThat(definition.idBindings().toString()).isEqualTo("IdBindings[redacted]");
        assertThatThrownBy(() -> definition.localizedContent().put("x", "y"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test void rejectsMismatchedTrustStampWithoutReturningPartialDefinition() {
        Fixture fixture = fixture();
        BlueprintGenerationResult.CandidateReviewed candidate = reviewed(fixture);
        var forged = new BlueprintGenerationResult.CandidateReviewed(candidate.blueprint(),
                new BlueprintGenerationResult.Evidence(new ProfileIdentity(new PackId("other"), new PackVersion(1, 0, 0), "x"),
                        "prep", 1, "review-v1", DefaultBlueprintSemanticValidator.VALIDATOR_VERSION));
        assertThat(new MaterializedGameCompiler().compile(forged))
                .isEqualTo(new MaterializedGameCompilationResult.InvalidAcceptedInput("MATERIALIZED_ACCEPTED_INPUT_PROFILE_MISMATCH"));
    }

    @Test void verifierRejectsMutatedEvidenceWithContentFreeCode() {
        MaterializedGameDefinition original = ((MaterializedGameCompilationResult.Compiled)
                new MaterializedGameCompiler().compile(reviewed(fixture()))).definition();
        MaterializedGameDefinition changed = new MaterializedGameDefinition(original.definitionFormatVersion(), original.compilerVersion(),
                original.origin(), original.profileIdentity(), original.locale(), original.lengthPresetId(), original.challengePresetId(),
                original.blueprintSchemaVersion(), original.generatedRulesContractVersion(), original.semanticValidatorVersion(),
                original.outputReviewerVersion(), original.storyPlan(), original.actors(), original.phases(), original.scenePlans(),
                original.canonicalScenes(), original.runtimeSceneSet(), original.runtimeRules(), original.localizedContent(), original.idBindings(),
                new MaterializedGameDefinition.CompilationEvidence(999, original.compilationEvidence().objectiveCount(),
                        original.compilationEvidence().sceneCount(), original.compilationEvidence().choiceCount(),
                        original.compilationEvidence().outcomeCount(), original.compilationEvidence().counterCount(),
                        original.compilationEvidence().flagCount(), original.compilationEvidence().setCount(),
                        original.compilationEvidence().edgeCount(), original.compilationEvidence().exploredConfigurationCount(),
                        original.compilationEvidence().minimumTransitions(), original.compilationEvidence().maximumTransitions()));
        assertThat(new MaterializedGameDefinitionVerifier().verify(changed))
                .isEqualTo(new MaterializedGameDefinitionVerifier.Verification(false, "MATERIALIZED_DEFINITION_EVIDENCE_MISMATCH"));
    }

    private static BlueprintGenerationResult.CandidateReviewed reviewed(Fixture fixture) {
        var validation = new DefaultBlueprintSemanticValidator().validate(fixture.blueprint, fixture.profile,
                fixture.preferences, fixture.constraints);
        assertThat(validation.status()).isEqualTo(BlueprintSemanticValidator.Status.PASS);
        return new BlueprintGenerationResult.CandidateReviewed(validation.validatedBlueprint(),
                new BlueprintGenerationResult.Evidence(fixture.profile.identity(), "prep", 1, "review-v1", validation.validatorVersion()));
    }

    private static Fixture fixture() {
        ProfileIdentity identity = new ProfileIdentity(new PackId("test-profile"), new PackVersion(1, 0, 0), "fingerprint");
        Set<String> conditions = Set.of("COUNTER_AT_LEAST", "COUNTER_AT_MOST", "COUNTER_EQUALS", "FLAG_IS", "SET_CONTAINS",
                "SET_SIZE_AT_LEAST", "VISITED_SCENE", "ALL", "ANY", "NOT");
        Set<String> effects = Set.of("COUNTER_ADD", "COUNTER_SET", "FLAG_SET", "SET_ADD", "SET_REMOVE");
        ProfileCapabilities caps = new ProfileCapabilities(List.of(PhaseKind.values()), "FORWARD_DAG", false, 6, 12, 3,
                1, 4, 1, 4, 2, 3, 4, 12, 2, 8, 0, 10, 3, 4, 32, 6, 100, conditions, effects);
        LengthPreset length = new LengthPreset("short", "length.short", 5, 7, 12);
        ChallengePreset challenge = new ChallengePreset("gentle", "challenge.gentle", 4, 12, 2, 4, 6, conditions, effects);
        LocaleTag locale = new LocaleTag("en");
        GeneratorProfile raw = new GeneratorProfile(1, identity.id(), identity.version(), "title", "description", List.of(locale),
                locale, new GeneratorProfile.ProfilePolicy(10, 14, true, 300, Set.of()), caps, List.of(length), List.of(challenge),
                new ProfileGenerationBudget(20, 2, 2000, 2, 1000, 1200), null, Map.of(), Map.of(), Map.of(), List.of(), List.of());
        ValidatedGeneratorProfile profile = ProfileValidationFactory.validated(raw, identity);
        ResolvedGamePreferences preferences = new ResolvedGamePreferences(identity, locale, "short", "gentle",
                Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        EffectiveGenerationConstraints constraints = new EffectiveGenerationConstraints(5, 7, 6, 12, 4, 12, 2, 4, 6,
                conditions, effects, 20, 2000, 1000, 1200);

        var actors = List.of(new Actor("guide", "Mika", "Guide", "A careful guide."));
        var state = new State(List.of(), List.of(new Flag("ready", false)), List.of());
        var objectives = List.of(new Objective("finish", "Finish", "Prepare the signal.", new FlagCondition("ready", true)));
        var phases = Arrays.stream(PhaseKind.values()).map(kind -> new Phase(kind, "Follow the goal.", "Develop the plan.",
                "Move onward.", List.of("guide"), List.of("finish"))).toList();
        List<Scene> scenes = List.of(
                decision("start", PhaseKind.OPENING, List.of(choice("same", "dev-a", null, List.of()), choice("route-b", "dev-b", null, List.of()))),
                decision("dev-a", PhaseKind.DEVELOPMENT, List.of(choice("same", "challenge", null, List.of()))),
                decision("dev-b", PhaseKind.DEVELOPMENT, List.of(choice("merge-b", "challenge", null, List.of()))),
                decision("challenge", PhaseKind.CHALLENGE, List.of(choice("prepare", "resolve", null, List.of(new FlagEffect("ready", true))),
                        choice("defer", "resolve", null, List.of()))),
                decision("resolve", PhaseKind.RESOLUTION, List.of(choice("check", "check", null, List.of()))),
                decision("check", PhaseKind.RESOLUTION, List.of(choice("win", "success", new FlagCondition("ready", true), List.of()),
                        choice("pause", "partial", new FlagCondition("ready", false), List.of()))),
                new EndingScene("success", PhaseKind.ENDING, "Success", "Recognize success.", List.of("guide"), List.of("finish"), "won"),
                new EndingScene("partial", PhaseKind.ENDING, "Progress", "Recognize progress.", List.of("guide"), List.of("finish"), "helped"));
        ParsedGameBlueprint blueprint = new ParsedGameBlueprint(1, "en", "Signal Path", "A team prepares a signal.", "A quiet workshop.",
                List.of("gentle"), List.of("Make a thoughtful choice"), actors, state, objectives, phases, "start", scenes,
                List.of(new Outcome("won", ResultClass.SUCCESS, "Signal ready", "The signal is ready."),
                        new Outcome("helped", ResultClass.PARTIAL_SUCCESS, "Progress", "The team made progress.")));
        return new Fixture(blueprint, profile, preferences, constraints);
    }

    private static DecisionScene decision(String id, PhaseKind phase, List<Choice> choices) {
        return new DecisionScene(id, phase, "A careful step", "Plan the next action.", List.of("guide"), List.of("finish"), choices);
    }
    private static Choice choice(String id, String destination, ParsedGameBlueprint.Condition requires, List<ParsedGameBlueprint.Effect> effects) {
        return new Choice(id, "Continue with the plan.", destination, requires, effects);
    }
    private record Fixture(ParsedGameBlueprint blueprint, ValidatedGeneratorProfile profile,
                           ResolvedGamePreferences preferences, EffectiveGenerationConstraints constraints) {}
}
