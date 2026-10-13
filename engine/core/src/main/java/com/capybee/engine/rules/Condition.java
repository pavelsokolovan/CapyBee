package com.capybee.engine.rules;

import java.util.List;

public sealed interface Condition permits Condition.CounterAtLeast, Condition.CounterAtMost,
        Condition.CounterEquals, Condition.FlagIs, Condition.SetContains, Condition.SetSizeAtLeast,
        Condition.VisitedScene, Condition.StepAtLeast, Condition.AtPlace, Condition.VisitedPlace,
        Condition.All, Condition.Any, Condition.Not {
    record CounterAtLeast(String counter, int value) implements Condition {}
    record CounterAtMost(String counter, int value) implements Condition {}
    record CounterEquals(String counter, int value) implements Condition {}
    record FlagIs(String flag, boolean value) implements Condition {}
    record SetContains(String set, String value) implements Condition {}
    record SetSizeAtLeast(String set, int size) implements Condition {}
    record VisitedScene(String scene) implements Condition {}
    record StepAtLeast(long step) implements Condition {}
    record AtPlace(String place) implements Condition {}
    record VisitedPlace(String place) implements Condition {}
    record All(List<Condition> conditions) implements Condition {
        public All { conditions = List.copyOf(conditions); }
    }
    record Any(List<Condition> conditions) implements Condition {
        public Any { conditions = List.copyOf(conditions); }
    }
    record Not(Condition condition) implements Condition {}
}