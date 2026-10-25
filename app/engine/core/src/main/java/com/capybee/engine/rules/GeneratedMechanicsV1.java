package com.capybee.engine.rules;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** The closed, engine-owned generated-rules-v1 vocabulary. */
public final class GeneratedMechanicsV1 {
    public static final String CONTRACT_VERSION = "generated-rules-v1";

    public enum Category { CONDITION, EFFECT }
    public enum StateKind { NONE, COUNTER, FLAG, SET, SET_MEMBER, SCENE }
    public enum ChildShape { NONE, MANY, ONE }

    public enum Operator {
        COUNTER_AT_LEAST(Category.CONDITION, StateKind.COUNTER, ChildShape.NONE),
        COUNTER_AT_MOST(Category.CONDITION, StateKind.COUNTER, ChildShape.NONE),
        COUNTER_EQUALS(Category.CONDITION, StateKind.COUNTER, ChildShape.NONE),
        FLAG_IS(Category.CONDITION, StateKind.FLAG, ChildShape.NONE),
        SET_CONTAINS(Category.CONDITION, StateKind.SET_MEMBER, ChildShape.NONE),
        SET_SIZE_AT_LEAST(Category.CONDITION, StateKind.SET, ChildShape.NONE),
        VISITED_SCENE(Category.CONDITION, StateKind.SCENE, ChildShape.NONE),
        ALL(Category.CONDITION, StateKind.NONE, ChildShape.MANY),
        ANY(Category.CONDITION, StateKind.NONE, ChildShape.MANY),
        NOT(Category.CONDITION, StateKind.NONE, ChildShape.ONE),
        COUNTER_ADD(Category.EFFECT, StateKind.COUNTER, ChildShape.NONE),
        COUNTER_SET(Category.EFFECT, StateKind.COUNTER, ChildShape.NONE),
        FLAG_SET(Category.EFFECT, StateKind.FLAG, ChildShape.NONE),
        SET_ADD(Category.EFFECT, StateKind.SET_MEMBER, ChildShape.NONE),
        SET_REMOVE(Category.EFFECT, StateKind.SET_MEMBER, ChildShape.NONE);

        private final Category category;
        private final StateKind stateKind;
        private final ChildShape childShape;

        Operator(Category category, StateKind stateKind, ChildShape childShape) {
            this.category = category;
            this.stateKind = stateKind;
            this.childShape = childShape;
        }

        public Category category() { return category; }
        public StateKind stateKind() { return stateKind; }
        public ChildShape childShape() { return childShape; }
    }

    private static final Set<String> CONDITIONS = names(Category.CONDITION);
    private static final Set<String> EFFECTS = names(Category.EFFECT);

    private GeneratedMechanicsV1() {}

    public static Set<String> conditionNames() { return CONDITIONS; }
    public static Set<String> effectNames() { return EFFECTS; }

    public static Operator operator(String name) {
        if (name == null) return null;
        try { return Operator.valueOf(name); }
        catch (IllegalArgumentException ex) { return null; }
    }

    public static String nameOf(Condition value) {
        if (value instanceof Condition.CounterAtLeast) return Operator.COUNTER_AT_LEAST.name();
        if (value instanceof Condition.CounterAtMost) return Operator.COUNTER_AT_MOST.name();
        if (value instanceof Condition.CounterEquals) return Operator.COUNTER_EQUALS.name();
        if (value instanceof Condition.FlagIs) return Operator.FLAG_IS.name();
        if (value instanceof Condition.SetContains) return Operator.SET_CONTAINS.name();
        if (value instanceof Condition.SetSizeAtLeast) return Operator.SET_SIZE_AT_LEAST.name();
        if (value instanceof Condition.VisitedScene) return Operator.VISITED_SCENE.name();
        if (value instanceof Condition.All) return Operator.ALL.name();
        if (value instanceof Condition.Any) return Operator.ANY.name();
        if (value instanceof Condition.Not) return Operator.NOT.name();
        return null;
    }

    public static String nameOf(Effect value) {
        if (value instanceof Effect.CounterAdd) return Operator.COUNTER_ADD.name();
        if (value instanceof Effect.CounterSet) return Operator.COUNTER_SET.name();
        if (value instanceof Effect.FlagSet) return Operator.FLAG_SET.name();
        if (value instanceof Effect.SetAdd) return Operator.SET_ADD.name();
        if (value instanceof Effect.SetRemove) return Operator.SET_REMOVE.name();
        return null;
    }

    private static Set<String> names(Category category) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        Arrays.stream(Operator.values()).filter(value -> value.category == category)
                .map(Enum::name).forEach(result::add);
        return Collections.unmodifiableSet(result);
    }
}
