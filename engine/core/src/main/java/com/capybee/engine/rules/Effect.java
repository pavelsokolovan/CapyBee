package com.capybee.engine.rules;

import java.util.Map;

public sealed interface Effect permits Effect.CounterAdd, Effect.CounterSet, Effect.FlagSet,
        Effect.SetAdd, Effect.SetRemove, Effect.EmitEvent, Effect.EndRun {
    record CounterAdd(String counter, int delta) implements Effect {}
    record CounterSet(String counter, int value) implements Effect {}
    record FlagSet(String flag, boolean value) implements Effect {}
    record SetAdd(String set, String value) implements Effect {}
    record SetRemove(String set, String value) implements Effect {}
    record EmitEvent(String event, Map<String, PayloadValue> payload) implements Effect {
        public EmitEvent { payload = Map.copyOf(payload); }
    }
    record EndRun(String outcome) implements Effect {}

    sealed interface PayloadValue permits PayloadValue.StringLiteral, PayloadValue.IntegerLiteral,
            PayloadValue.BooleanLiteral, PayloadValue.CounterRef {
        record StringLiteral(String value) implements PayloadValue {}
        record IntegerLiteral(int value) implements PayloadValue {}
        record BooleanLiteral(boolean value) implements PayloadValue {}
        record CounterRef(String counter) implements PayloadValue {}
    }
}