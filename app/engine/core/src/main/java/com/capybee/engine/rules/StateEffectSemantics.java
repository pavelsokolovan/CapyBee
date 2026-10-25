package com.capybee.engine.rules;

import java.util.Objects;

/** Deterministic semantics for the state-only effect subset. */
public final class StateEffectSemantics {
    public enum CounterPolicy { REJECT_OUT_OF_RANGE, CLAMP_TO_DECLARATION }
    public enum Failure { NONE, UNSUPPORTED_EFFECT, UNKNOWN_TARGET, UNKNOWN_SET_MEMBER, COUNTER_OUT_OF_RANGE }

    public interface StateMutation {
        Integer counterValue(String id);
        Integer counterMin(String id);
        Integer counterMax(String id);
        Boolean flagValue(String id);
        boolean hasSet(String id);
        boolean isSetMember(String id, String value);
        void setCounter(String id, int value);
        void setFlag(String id, boolean value);
        void addSetValue(String id, String value);
        void removeSetValue(String id, String value);
    }

    private StateEffectSemantics() {}

    public static Failure apply(Effect effect, StateMutation state, CounterPolicy policy) {
        Objects.requireNonNull(effect, "effect");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(policy, "policy");
        if (effect instanceof Effect.CounterAdd item) {
            Integer current = state.counterValue(item.counter());
            return current == null ? Failure.UNKNOWN_TARGET
                    : writeCounter(item.counter(), (long) current + item.delta(), state, policy);
        }
        if (effect instanceof Effect.CounterSet item) {
            return state.counterValue(item.counter()) == null ? Failure.UNKNOWN_TARGET
                    : writeCounter(item.counter(), item.value(), state, policy);
        }
        if (effect instanceof Effect.FlagSet item) {
            if (state.flagValue(item.flag()) == null) return Failure.UNKNOWN_TARGET;
            state.setFlag(item.flag(), item.value());
            return Failure.NONE;
        }
        if (effect instanceof Effect.SetAdd item) {
            if (!state.hasSet(item.set())) return Failure.UNKNOWN_TARGET;
            if (!state.isSetMember(item.set(), item.value())) return Failure.UNKNOWN_SET_MEMBER;
            state.addSetValue(item.set(), item.value());
            return Failure.NONE;
        }
        if (effect instanceof Effect.SetRemove item) {
            if (!state.hasSet(item.set())) return Failure.UNKNOWN_TARGET;
            if (!state.isSetMember(item.set(), item.value())) return Failure.UNKNOWN_SET_MEMBER;
            state.removeSetValue(item.set(), item.value());
            return Failure.NONE;
        }
        return Failure.UNSUPPORTED_EFFECT;
    }

    private static Failure writeCounter(String id, long value, StateMutation state, CounterPolicy policy) {
        Integer minimum = state.counterMin(id), maximum = state.counterMax(id);
        if (minimum == null || maximum == null) return Failure.UNKNOWN_TARGET;
        if (value < minimum || value > maximum) {
            if (policy == CounterPolicy.REJECT_OUT_OF_RANGE) return Failure.COUNTER_OUT_OF_RANGE;
            value = Math.max(minimum, Math.min((long) maximum, value));
        }
        state.setCounter(id, (int) value);
        return Failure.NONE;
    }
}
