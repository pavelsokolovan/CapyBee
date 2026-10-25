package com.capybee.engine.generation.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.blueprint.ParsedGameBlueprint.*;
import com.capybee.engine.generation.profile.*;
import com.capybee.engine.pack.*;
import com.capybee.engine.rules.ResultClass;
import java.util.*;
import org.junit.jupiter.api.Test;

class DefaultBlueprintSemanticValidatorTest {
    private final DefaultBlueprintSemanticValidator validator = new DefaultBlueprintSemanticValidator();

    @Test void provesCompleteBlueprintAndProducesStableRedactedEvidence() {
        Context context = context(100);
        var first = validate(proposal(), context); var second = validate(proposal(), context);
        assertThat(first.status()).isEqualTo(BlueprintSemanticValidator.Status.PASS);
        assertThat(first.issues()).isEqualTo(second.issues());
        assertThat(first.validatedBlueprint().evidence()).isEqualTo(second.validatedBlueprint().evidence());
        var accepted = first.validatedBlueprint();
        assertThat(accepted.blueprint()).isEqualTo(proposal());
        assertThat(accepted.evidence().sceneCount()).isEqualTo(8);
        assertThat(accepted.evidence().edgeCount()).isEqualTo(9);
        assertThat(accepted.evidence().minimumTransitions()).isEqualTo(5);
        assertThat(accepted.evidence().maximumTransitions()).isEqualTo(5);
        assertThat(accepted.evidence().witnessedEndingIds()).containsExactly("success", "partial");
        assertThat(accepted.evidence().witnessedObjectiveIds()).containsExactly("finish");
        assertThat(accepted.evidence().semanticContractVersion()).isEqualTo("generated-rules-v1");
        assertThat(accepted.mechanicsPlan().contractVersion()).isEqualTo("generated-rules-v1");
        assertThat(accepted.mechanicsPlan().objectiveConditions()).containsExactly(new com.capybee.engine.rules.Condition.FlagIs("ready", true));
        assertThat(accepted.mechanicsPlan().toString()).contains("redacted").doesNotContain("ready");
        assertThat(accepted.toString()).doesNotContain("Signal Path");
        assertThat(accepted.evidence().toString()).contains("witnesses=redacted").doesNotContain("finish");
        org.junit.jupiter.api.Assertions.assertThrows(UnsupportedOperationException.class,
                () -> accepted.evidence().witnessedEndingIds().add("private"));
    }

    @Test void rejectsUnknownReferencesAndNeverLeaksSymbols() {
        Context context = context(100); ParsedGameBlueprint source = proposal();
        DecisionScene opening = (DecisionScene) source.scenes().getFirst();
        Choice broken = new Choice("private-choice", "private prose", "private-scene", null, List.of());
        ParsedGameBlueprint changed = withScene(source, 0, new DecisionScene(opening.id(), opening.phase(), opening.title(),
                opening.intent(), opening.actorIds(), opening.objectiveIds(), List.of(broken)));
        var result = validate(changed, context);
        assertThat(result.status()).isEqualTo(BlueprintSemanticValidator.Status.REJECT);
        assertThat(result.issues()).extracting(BlueprintIssue::code).contains("BLUEPRINT_SCENE_UNKNOWN");
        assertThat(result.toString()).doesNotContain("private-choice").doesNotContain("private-scene").doesNotContain("private prose");
    }

    @Test void rejectsCyclesSkippedPhasesAndUnreachableScenesStatically() {
        Context context = context(100); ParsedGameBlueprint source = proposal();
        DecisionScene devA = (DecisionScene) source.scenes().get(1);
        ParsedGameBlueprint cycle = withScene(source, 1, decision(devA.id(), PhaseKind.DEVELOPMENT,
                List.of(choice("back", "start", null, List.of()))));
        assertThat(validate(cycle, context).issues()).extracting(BlueprintIssue::code)
                .contains("BLUEPRINT_ROUTE_BACKWARDS", "BLUEPRINT_ROUTE_CYCLIC");

        DecisionScene opening = (DecisionScene) source.scenes().getFirst();
        ParsedGameBlueprint skip = withScene(source, 0, decision(opening.id(), PhaseKind.OPENING,
                List.of(choice("skip", "challenge", null, List.of()))));
        assertThat(validate(skip, context).issues()).extracting(BlueprintIssue::code)
                .contains("BLUEPRINT_ROUTE_SKIPS_PHASE");
    }

    @Test void distinguishesStructuralReachabilityFromExecutableLivenessAndEndingWitnesses() {
        Context context = context(100); ParsedGameBlueprint source = proposal();
        DecisionScene challenge = (DecisionScene) source.scenes().get(3);
        List<Choice> choices = challenge.choices().stream().map(choice -> new Choice(choice.id(), choice.intent(),
                choice.destinationSceneId(), null, List.of(new FlagEffect("ready", true)))).toList();
        List<Scene> scenes = replace(source.scenes(), 3, new DecisionScene(challenge.id(), challenge.phase(), challenge.title(),
                challenge.intent(), challenge.actorIds(), challenge.objectiveIds(), choices));
        scenes = replace(scenes, 2, decision("dev-b", PhaseKind.DEVELOPMENT,
                List.of(choice("blocked", "challenge", new FlagCondition("ready", true), List.of()))));
        ParsedGameBlueprint gated = copy(source, scenes);
        var result = validate(gated, context);
        assertThat(result.issues()).extracting(BlueprintIssue::code).contains("BLUEPRINT_ROUTE_DEAD_END", "BLUEPRINT_ENDING_UNREACHABLE");
    }

    @Test void rejectsReachableIntermediateCounterOverflowButNotUnusedBoundedState() {
        Context context = context(100); ParsedGameBlueprint source = proposal();
        State state = new State(List.of(new Counter("steps", 0, 1, 0)), source.state().flags(), List.of());
        DecisionScene challenge = (DecisionScene) source.scenes().get(3);
        Choice original = challenge.choices().getFirst();
        Choice overflow = new Choice(original.id(), original.intent(), original.destinationSceneId(), null,
                List.of(new CounterEffect("COUNTER_ADD", "steps", 1), new CounterEffect("COUNTER_ADD", "steps", 1),
                        new FlagEffect("ready", true)));
        List<Choice> choices = new ArrayList<>(challenge.choices()); choices.set(0, overflow);
        ParsedGameBlueprint changed = new ParsedGameBlueprint(source.schemaVersion(), source.locale(), source.title(), source.premise(),
                source.setting(), source.toneTags(), source.storyPromises(), source.actors(), state, source.objectives(), source.phases(),
                source.startSceneId(), replace(source.scenes(), 3, new DecisionScene(challenge.id(), challenge.phase(), challenge.title(),
                challenge.intent(), challenge.actorIds(), challenge.objectiveIds(), choices)), source.outcomes());
        assertThat(validate(changed, context).issues()).extracting(BlueprintIssue::code).contains("BLUEPRINT_STATE_BOUNDS_VIOLATED");

        ParsedGameBlueprint unused = new ParsedGameBlueprint(source.schemaVersion(), source.locale(), source.title(), source.premise(),
                source.setting(), source.toneTags(), source.storyPromises(), source.actors(), state, source.objectives(), source.phases(),
                source.startSceneId(), source.scenes(), source.outcomes());
        assertThat(validate(unused, context).status()).isEqualTo(BlueprintSemanticValidator.Status.PASS);
    }

    @Test void requiresSimultaneouslyEnabledSemanticallyDifferentPreResolutionChoices() {
        Context context = context(100); ParsedGameBlueprint source = proposal();
        DecisionScene opening = (DecisionScene) source.scenes().getFirst();
        Choice first = new Choice("first", "different label", "dev-a", new FlagCondition("ready", false), List.of());
        Choice second = new Choice("second", "different label", "dev-b", new FlagCondition("ready", true), List.of());
        DecisionScene equivalent = new DecisionScene(opening.id(), opening.phase(), opening.title(), opening.intent(), opening.actorIds(),
                opening.objectiveIds(), List.of(first, second));
        List<Scene> scenes = replace(source.scenes(), 0, equivalent);
        scenes = replace(scenes, 3, decision("challenge", PhaseKind.CHALLENGE,
                List.of(choice("same-a", "resolve", null, List.of()), choice("same-b", "resolve", null, List.of()))));
        ParsedGameBlueprint changed = copy(source, scenes);
        assertThat(validate(changed, context).issues()).extracting(BlueprintIssue::code).contains("BLUEPRINT_CHOICE_REQUIRED");
    }

    @Test void returnsInconclusiveExactlyWhenAnotherDistinctConfigurationWouldExceedCeiling() {
        var pass = validate(proposal(), context(17));
        var limited = validate(proposal(), context(16));
        assertThat(pass.status()).isEqualTo(BlueprintSemanticValidator.Status.PASS);
        assertThat(pass.validatedBlueprint().evidence().exploredConfigurationCount()).isEqualTo(17);
        assertThat(limited.status()).isEqualTo(BlueprintSemanticValidator.Status.INCONCLUSIVE);
        assertThat(limited.issues()).extracting(BlueprintIssue::code).containsExactly("BLUEPRINT_STATE_LIMIT_EXCEEDED");
    }

    @Test void invalidTrustedContextIsDistinctAndExaminedBeforeBlueprintContent() {
        Context context = context(100);
        var wrong = new ResolvedGamePreferences(new ProfileIdentity(new PackId("other"), new PackVersion(1,0,0), "private"),
                new LocaleTag("en"), "short", "gentle", Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        var result = validator.validate(null, context.profile, wrong, context.constraints);
        assertThat(result.status()).isEqualTo(BlueprintSemanticValidator.Status.INVALID_CONTEXT);
        assertThat(result.issues()).extracting(BlueprintIssue::code).containsExactly("BLUEPRINT_VALIDATION_CONTEXT_INVALID");
    }

    @Test void provesIndependentPolishCounterAndSetTopology() {
        Context context = context(100, "pl");
        var result = validate(polishProposal(), context);
        assertThat(result.status()).isEqualTo(BlueprintSemanticValidator.Status.PASS);
        assertThat(result.validatedBlueprint().locale()).isEqualTo(new LocaleTag("pl"));
        assertThat(result.validatedBlueprint().evidence().sceneCount()).isEqualTo(11);
        assertThat(result.validatedBlueprint().evidence().witnessedEndingIds()).containsExactly("sukces", "czesciowy");
    }

    @Test void visitedSceneObjectiveCanCompleteAtItsEnding() {
        Context context = context(100); ParsedGameBlueprint source = proposal();
        Objective visited = new Objective("finish", "Finish", "Reach the result.", new VisitedSceneCondition("success"));
        ParsedGameBlueprint changed = new ParsedGameBlueprint(source.schemaVersion(), source.locale(), source.title(), source.premise(),
                source.setting(), source.toneTags(), source.storyPromises(), source.actors(), source.state(), List.of(visited), source.phases(),
                source.startSceneId(), source.scenes(), source.outcomes());
        assertThat(validate(changed, context).status()).isEqualTo(BlueprintSemanticValidator.Status.PASS);
    }

    private BlueprintSemanticValidator.Validation validate(ParsedGameBlueprint value, Context context) {
        return validator.validate(value, context.profile, context.preferences, context.constraints);
    }

    private static ParsedGameBlueprint proposal() {
        var actors = List.of(new Actor("guide", "Mika", "Guide", "A careful guide."));
        var state = new State(List.of(), List.of(new Flag("ready", false)), List.of());
        var objectives = List.of(new Objective("finish", "Finish", "Prepare the signal.", new FlagCondition("ready", true)));
        var phases = Arrays.stream(PhaseKind.values()).map(kind -> new Phase(kind, "Follow the goal.", "Develop the plan.",
                "Move onward.", List.of("guide"), List.of("finish"))).toList();
        List<Scene> scenes = List.of(
                decision("start", PhaseKind.OPENING, List.of(choice("route-a", "dev-a", null, List.of()), choice("route-b", "dev-b", null, List.of()))),
                decision("dev-a", PhaseKind.DEVELOPMENT, List.of(choice("merge-a", "challenge", null, List.of()))),
                decision("dev-b", PhaseKind.DEVELOPMENT, List.of(choice("merge-b", "challenge", null, List.of()))),
                decision("challenge", PhaseKind.CHALLENGE, List.of(choice("prepare", "resolve", null, List.of(new FlagEffect("ready", true))),
                        choice("defer", "resolve", null, List.of()))),
                decision("resolve", PhaseKind.RESOLUTION, List.of(choice("check", "check", null, List.of()))),
                decision("check", PhaseKind.RESOLUTION, List.of(choice("win", "success", new FlagCondition("ready", true), List.of()),
                        choice("pause", "partial", new FlagCondition("ready", false), List.of()))),
                new EndingScene("success", PhaseKind.ENDING, "Success", "Recognize success.", List.of("guide"), List.of("finish"), "won"),
                new EndingScene("partial", PhaseKind.ENDING, "Progress", "Recognize progress.", List.of("guide"), List.of("finish"), "helped"));
        return new ParsedGameBlueprint(1, "en", "Signal Path", "A team prepares a signal.", "A quiet workshop.",
                List.of("gentle"), List.of("Make a thoughtful choice"), actors, state, objectives, phases, "start", scenes,
                List.of(new Outcome("won", ResultClass.SUCCESS, "Signal ready", "The signal is ready."),
                        new Outcome("helped", ResultClass.PARTIAL_SUCCESS, "Progress", "The team made progress.")));
    }

    private static ParsedGameBlueprint polishProposal() {
        var actor = List.of(new Actor("opiekun", "Olek", "Opiekun", "Pomaga zespołowi."));
        var state = new State(List.of(new Counter("postep", 0, 1, 0)), List.of(),
                List.of(new FiniteSet("slady", List.of("klucz", "kurz"), List.of())));
        Condition counterReady = new CompositeCondition("ALL", List.of(
                new CounterCondition("COUNTER_AT_LEAST", "postep", 1),
                new CounterCondition("COUNTER_AT_MOST", "postep", 1),
                new CounterCondition("COUNTER_EQUALS", "postep", 1)));
        Condition setReady = new CompositeCondition("ALL", List.of(new SetContainsCondition("slady", "klucz"),
                new SetSizeCondition("slady", 1)));
        Condition ready = new CompositeCondition("ANY", List.of(counterReady, setReady));
        var objectives = List.of(new Objective("cel", "Znajdź drogę", "Zdobądź potrzebny ślad.", ready));
        var phases = Arrays.stream(PhaseKind.values()).map(kind -> new Phase(kind, "Wykonaj krok.", "Plan się rozwija.",
                "Przejdź dalej.", List.of("opiekun"), List.of("cel"))).toList();
        List<Scene> scenes = List.of(
                polishDecision("poczatek", PhaseKind.OPENING, List.of(polishChoice("dalej", "wybor", null, List.of()))),
                polishDecision("wybor", PhaseKind.DEVELOPMENT, List.of(polishChoice("licznik", "proba-licznik", null, List.of()),
                        polishChoice("zestaw", "proba-zestaw", null, List.of()), polishChoice("odloz", "proba-pusta", null, List.of()))),
                polishDecision("proba-licznik", PhaseKind.CHALLENGE, List.of(polishChoice("ustaw", "wynik-licznik", null,
                        List.of(new CounterEffect("COUNTER_SET", "postep", 0), new CounterEffect("COUNTER_ADD", "postep", 1))))),
                polishDecision("proba-zestaw", PhaseKind.CHALLENGE, List.of(polishChoice("dodaj", "wynik-zestaw", null,
                        List.of(new SetEffect("SET_REMOVE", "slady", "kurz"), new SetEffect("SET_ADD", "slady", "klucz"))))),
                polishDecision("proba-pusta", PhaseKind.CHALLENGE, List.of(polishChoice("pomin", "wynik-pusty", null, List.of()))),
                polishDecision("wynik-licznik", PhaseKind.RESOLUTION, List.of(polishChoice("sprawdz-a", "sprawdzenie", null, List.of()))),
                polishDecision("wynik-zestaw", PhaseKind.RESOLUTION, List.of(polishChoice("sprawdz-b", "sprawdzenie", null, List.of()))),
                polishDecision("wynik-pusty", PhaseKind.RESOLUTION, List.of(polishChoice("sprawdz-c", "sprawdzenie", null, List.of()))),
                polishDecision("sprawdzenie", PhaseKind.RESOLUTION, List.of(polishChoice("wygrana", "sukces", ready, List.of()),
                        polishChoice("jeszcze-nie", "czesciowy", new NotCondition(ready), List.of()))),
                new EndingScene("sukces", PhaseKind.ENDING, "Sukces", "Cel został osiągnięty.", List.of("opiekun"), List.of("cel"), "wygrana"),
                new EndingScene("czesciowy", PhaseKind.ENDING, "Przerwa", "Zespół spróbuje ponownie.", List.of("opiekun"), List.of("cel"), "pomoc"));
        return new ParsedGameBlueprint(1, "pl", "Ścieżka", "Zespół szuka bezpiecznej drogi.", "Spokojne miejsce.",
                List.of("łagodny"), List.of("Dokonaj wyboru"), actor, state, objectives, phases, "poczatek", scenes,
                List.of(new Outcome("wygrana", ResultClass.SUCCESS, "Gotowe", "Cel osiągnięty."),
                        new Outcome("pomoc", ResultClass.PARTIAL_SUCCESS, "Postęp", "To dobry początek.")));
    }

    private static DecisionScene polishDecision(String id, PhaseKind phase, List<Choice> choices) {
        return new DecisionScene(id, phase, "Kolejny krok", "Wybierz działanie.", List.of("opiekun"), List.of("cel"), choices);
    }
    private static Choice polishChoice(String id, String destination, Condition requires, List<Effect> effects) {
        return new Choice(id, "Kontynuuj plan.", destination, requires, effects);
    }

    private static DecisionScene decision(String id, PhaseKind phase, List<Choice> choices) {
        return new DecisionScene(id, phase, "A careful step", "Plan the next action.", List.of("guide"), List.of("finish"), choices);
    }
    private static Choice choice(String id, String destination, Condition requires, List<Effect> effects) {
        return new Choice(id, "Continue with the plan.", destination, requires, effects);
    }
    private static ParsedGameBlueprint withScene(ParsedGameBlueprint source, int index, Scene scene) { return copy(source, replace(source.scenes(), index, scene)); }
    private static ParsedGameBlueprint copy(ParsedGameBlueprint source, List<Scene> scenes) {
        return new ParsedGameBlueprint(source.schemaVersion(), source.locale(), source.title(), source.premise(), source.setting(), source.toneTags(),
                source.storyPromises(), source.actors(), source.state(), source.objectives(), source.phases(), source.startSceneId(), scenes, source.outcomes());
    }
    private static <T> List<T> replace(List<T> source, int index, T value) { List<T> result = new ArrayList<>(source); result.set(index, value); return List.copyOf(result); }

    private static Context context(int maxStates) { return context(maxStates, "en"); }
    private static Context context(int maxStates, String localeValue) {
        ProfileIdentity identity = new ProfileIdentity(new PackId("test-profile"), new PackVersion(1,0,0), "fingerprint");
        Set<String> conditions = Set.of("COUNTER_AT_LEAST", "COUNTER_AT_MOST", "COUNTER_EQUALS", "FLAG_IS", "SET_CONTAINS",
                "SET_SIZE_AT_LEAST", "VISITED_SCENE", "ALL", "ANY", "NOT");
        Set<String> effects = Set.of("COUNTER_ADD", "COUNTER_SET", "FLAG_SET", "SET_ADD", "SET_REMOVE");
        ProfileCapabilities caps = new ProfileCapabilities(List.of(PhaseKind.values()), "FORWARD_DAG", false, 6, 12, 3,
                1, 4, 1, 4, 2, 3, 4, 12, 2, 8, 0, 10, 3, 4, 32, 6, maxStates, conditions, effects);
        LengthPreset length = new LengthPreset("short", "length.short", 5, 7, 12);
        ChallengePreset challenge = new ChallengePreset("gentle", "challenge.gentle", 4, 12, 2, 4, 6, conditions, effects);
        LocaleTag locale = new LocaleTag(localeValue);
        GeneratorProfile raw = new GeneratorProfile(1, identity.id(), identity.version(), "title", "description", List.of(locale),
                locale, new GeneratorProfile.ProfilePolicy(10,14,true,300,Set.of()), caps, List.of(length), List.of(challenge),
                new ProfileGenerationBudget(20,2,2000,2,1000,1200), null, Map.of(), Map.of(), Map.of(), List.of(), List.of());
        ValidatedGeneratorProfile profile = ProfileValidationFactory.validated(raw, identity);
        ResolvedGamePreferences preferences = new ResolvedGamePreferences(identity, locale, "short", "gentle",
                Optional.empty(), Optional.empty(), Optional.empty(), List.of());
        EffectiveGenerationConstraints constraints = new EffectiveGenerationConstraints(5,7,6,12,4,12,2,4,6,conditions,effects,20,2000,1000,1200);
        return new Context(profile, preferences, constraints);
    }
    private record Context(ValidatedGeneratorProfile profile, ResolvedGamePreferences preferences,
                           EffectiveGenerationConstraints constraints) {}
}
