package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.blueprint.ParsedGameBlueprint.*;
import com.capybee.engine.rules.GeneratedMechanicsV1;
import java.util.ArrayList;
import java.util.List;

/** Package-private binder; callers must establish declarations and references first. */
final class GeneratedMechanicsBinder {
    ValidatedMechanicsPlan bind(ParsedGameBlueprint blueprint) {
        List<com.capybee.engine.rules.Condition> objectives = blueprint.objectives().stream()
                .map(Objective::completeWhen).map(this::condition).toList();
        List<List<ValidatedMechanicsPlan.ChoiceMechanics>> scenes = new ArrayList<>();
        for (Scene scene : blueprint.scenes()) {
            if (scene instanceof DecisionScene decision) {
                scenes.add(decision.choices().stream().map(choice -> new ValidatedMechanicsPlan.ChoiceMechanics(
                        choice.requires() == null ? null : condition(choice.requires()),
                        choice.effects().stream().map(this::effect).toList())).toList());
            } else scenes.add(List.of());
        }
        return new ValidatedMechanicsPlan(objectives, scenes);
    }

    private com.capybee.engine.rules.Condition condition(Condition value) {
        GeneratedMechanicsV1.Operator operator = conditionOperator(value);
        if (operator == null) throw mismatch();
        return switch (operator) {
            case COUNTER_AT_LEAST -> { CounterCondition v = (CounterCondition) value; yield new com.capybee.engine.rules.Condition.CounterAtLeast(v.counter(), v.value()); }
            case COUNTER_AT_MOST -> { CounterCondition v = (CounterCondition) value; yield new com.capybee.engine.rules.Condition.CounterAtMost(v.counter(), v.value()); }
            case COUNTER_EQUALS -> { CounterCondition v = (CounterCondition) value; yield new com.capybee.engine.rules.Condition.CounterEquals(v.counter(), v.value()); }
            case FLAG_IS -> { FlagCondition v = (FlagCondition) value; yield new com.capybee.engine.rules.Condition.FlagIs(v.flag(), v.value()); }
            case SET_CONTAINS -> { SetContainsCondition v = (SetContainsCondition) value; yield new com.capybee.engine.rules.Condition.SetContains(v.set(), v.value()); }
            case SET_SIZE_AT_LEAST -> { SetSizeCondition v = (SetSizeCondition) value; yield new com.capybee.engine.rules.Condition.SetSizeAtLeast(v.set(), v.size()); }
            case VISITED_SCENE -> { VisitedSceneCondition v = (VisitedSceneCondition) value; yield new com.capybee.engine.rules.Condition.VisitedScene(v.scene()); }
            case ALL -> { CompositeCondition v = (CompositeCondition) value; yield new com.capybee.engine.rules.Condition.All(v.conditions().stream().map(this::condition).toList()); }
            case ANY -> { CompositeCondition v = (CompositeCondition) value; yield new com.capybee.engine.rules.Condition.Any(v.conditions().stream().map(this::condition).toList()); }
            case NOT -> { NotCondition v = (NotCondition) value; yield new com.capybee.engine.rules.Condition.Not(condition(v.condition())); }
            case COUNTER_ADD, COUNTER_SET, FLAG_SET, SET_ADD, SET_REMOVE -> throw mismatch();
        };
    }

    private com.capybee.engine.rules.Effect effect(Effect value) {
        GeneratedMechanicsV1.Operator operator = effectOperator(value);
        if (operator == null) throw mismatch();
        return switch (operator) {
            case COUNTER_ADD -> { CounterEffect v = (CounterEffect) value; yield new com.capybee.engine.rules.Effect.CounterAdd(v.counter(), v.value()); }
            case COUNTER_SET -> { CounterEffect v = (CounterEffect) value; yield new com.capybee.engine.rules.Effect.CounterSet(v.counter(), v.value()); }
            case FLAG_SET -> { FlagEffect v = (FlagEffect) value; yield new com.capybee.engine.rules.Effect.FlagSet(v.flag(), v.value()); }
            case SET_ADD -> { SetEffect v = (SetEffect) value; yield new com.capybee.engine.rules.Effect.SetAdd(v.set(), v.value()); }
            case SET_REMOVE -> { SetEffect v = (SetEffect) value; yield new com.capybee.engine.rules.Effect.SetRemove(v.set(), v.value()); }
            case COUNTER_AT_LEAST, COUNTER_AT_MOST, COUNTER_EQUALS, FLAG_IS, SET_CONTAINS,
                    SET_SIZE_AT_LEAST, VISITED_SCENE, ALL, ANY, NOT -> throw mismatch();
        };
    }

    private static GeneratedMechanicsV1.Operator conditionOperator(Condition value) {
        if (value instanceof CounterCondition v) return GeneratedMechanicsV1.operator(v.type());
        if (value instanceof FlagCondition) return GeneratedMechanicsV1.Operator.FLAG_IS;
        if (value instanceof SetContainsCondition) return GeneratedMechanicsV1.Operator.SET_CONTAINS;
        if (value instanceof SetSizeCondition) return GeneratedMechanicsV1.Operator.SET_SIZE_AT_LEAST;
        if (value instanceof VisitedSceneCondition) return GeneratedMechanicsV1.Operator.VISITED_SCENE;
        if (value instanceof CompositeCondition v) return GeneratedMechanicsV1.operator(v.type());
        if (value instanceof NotCondition) return GeneratedMechanicsV1.Operator.NOT;
        return null;
    }

    private static GeneratedMechanicsV1.Operator effectOperator(Effect value) {
        if (value instanceof CounterEffect v) return GeneratedMechanicsV1.operator(v.type());
        if (value instanceof FlagEffect) return GeneratedMechanicsV1.Operator.FLAG_SET;
        if (value instanceof SetEffect v) return GeneratedMechanicsV1.operator(v.type());
        return null;
    }

    private static IllegalStateException mismatch() {
        return new IllegalStateException("Generated mechanics contract mismatch");
    }
}
