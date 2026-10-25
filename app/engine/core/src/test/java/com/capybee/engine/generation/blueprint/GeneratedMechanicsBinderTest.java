package com.capybee.engine.generation.blueprint;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.capybee.engine.generation.PhaseKind;
import com.capybee.engine.generation.blueprint.ParsedGameBlueprint.*;
import java.util.List;
import org.junit.jupiter.api.Test;

class GeneratedMechanicsBinderTest {
    @Test void bindsEveryOperatorToExistingDslAndPreservesOrder() {
        Condition condition = new CompositeCondition("ALL", List.of(
                new CounterCondition("COUNTER_AT_LEAST", "counter", 1),
                new CounterCondition("COUNTER_AT_MOST", "counter", 2),
                new CounterCondition("COUNTER_EQUALS", "counter", 2),
                new FlagCondition("flag", true), new SetContainsCondition("set", "member"),
                new SetSizeCondition("set", 1), new VisitedSceneCondition("scene"),
                new CompositeCondition("ANY", List.of(new FlagCondition("flag", false))),
                new NotCondition(new FlagCondition("flag", false))));
        List<Effect> effects = List.of(new CounterEffect("COUNTER_ADD", "counter", 1),
                new CounterEffect("COUNTER_SET", "counter", 2), new FlagEffect("flag", true),
                new SetEffect("SET_ADD", "set", "member"), new SetEffect("SET_REMOVE", "set", "member"));
        ValidatedMechanicsPlan plan = new GeneratedMechanicsBinder().bind(blueprint(condition, effects));

        assertThat(plan.objectiveConditions()).singleElement().isInstanceOf(com.capybee.engine.rules.Condition.All.class);
        var children = ((com.capybee.engine.rules.Condition.All) plan.objectiveConditions().getFirst()).conditions();
        assertThat(children).extracting(Object::getClass).containsExactly(
                com.capybee.engine.rules.Condition.CounterAtLeast.class,
                com.capybee.engine.rules.Condition.CounterAtMost.class,
                com.capybee.engine.rules.Condition.CounterEquals.class,
                com.capybee.engine.rules.Condition.FlagIs.class,
                com.capybee.engine.rules.Condition.SetContains.class,
                com.capybee.engine.rules.Condition.SetSizeAtLeast.class,
                com.capybee.engine.rules.Condition.VisitedScene.class,
                com.capybee.engine.rules.Condition.Any.class,
                com.capybee.engine.rules.Condition.Not.class);
        assertThat(plan.sceneChoices().getFirst().getFirst().effects()).extracting(Object::getClass).containsExactly(
                com.capybee.engine.rules.Effect.CounterAdd.class,
                com.capybee.engine.rules.Effect.CounterSet.class,
                com.capybee.engine.rules.Effect.FlagSet.class,
                com.capybee.engine.rules.Effect.SetAdd.class,
                com.capybee.engine.rules.Effect.SetRemove.class);
        assertThat(plan.sceneChoices().getFirst().getFirst().toString()).isEqualTo("ChoiceMechanics[redacted]");
    }

    @Test void unknownDiscriminatorFailsAsContentFreeContractMismatch() {
        ParsedGameBlueprint value = blueprint(new CounterCondition("private-script", "private-id", 1), List.of());
        assertThatThrownBy(() -> new GeneratedMechanicsBinder().bind(value))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Generated mechanics contract mismatch")
                .hasMessageNotContaining("private");
    }

    private static ParsedGameBlueprint blueprint(Condition condition, List<Effect> effects) {
        var objective = new Objective("objective", "title", "description", condition);
        var choice = new Choice("choice", "intent", "scene", null, effects);
        var scene = new DecisionScene("scene", PhaseKind.OPENING, "title", "intent", List.of(), List.of(), List.of(choice));
        return new ParsedGameBlueprint(1, "en", "title", "premise", "setting", List.of(), List.of(), List.of(),
                new State(List.of(), List.of(), List.of()), List.of(objective), List.of(), "scene", List.of(scene), List.of());
    }
}
