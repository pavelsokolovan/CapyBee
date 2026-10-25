package com.capybee.engine.rules;

import java.util.Objects;

/** Pure evaluator shared by authored runtime rules and validated generated rules. */
public final class ConditionEvaluator {
    private ConditionEvaluator() {}

    public static boolean evaluate(Condition condition, RuleStateView state) {
        Objects.requireNonNull(condition, "condition");
        Objects.requireNonNull(state, "state");
        if (condition instanceof Condition.CounterAtLeast item) return state.counter(item.counter()) >= item.value();
        if (condition instanceof Condition.CounterAtMost item) return state.counter(item.counter()) <= item.value();
        if (condition instanceof Condition.CounterEquals item) return state.counter(item.counter()) == item.value();
        if (condition instanceof Condition.FlagIs item) return state.flag(item.flag()) == item.value();
        if (condition instanceof Condition.SetContains item) return state.set(item.set()).contains(item.value());
        if (condition instanceof Condition.SetSizeAtLeast item) return state.set(item.set()).size() >= item.size();
        if (condition instanceof Condition.VisitedScene item) return state.visitedScene(item.scene());
        if (condition instanceof Condition.AtPlace item) return state.atPlace(item.place());
        if (condition instanceof Condition.VisitedPlace item) return state.visitedPlace(item.place());
        if (condition instanceof Condition.StepAtLeast item) return state.step() >= item.step();
        if (condition instanceof Condition.All item) return item.conditions().stream().allMatch(value -> evaluate(value, state));
        if (condition instanceof Condition.Any item) return item.conditions().stream().anyMatch(value -> evaluate(value, state));
        if (condition instanceof Condition.Not item) return !evaluate(item.condition(), state);
        throw new IllegalStateException("Unsupported condition variant");
    }
}
