package com.capybee.engine.pack;

import java.util.List;

public record EventDef(String id, List<PayloadField> payload) {
    public EventDef { payload = List.copyOf(payload); }
    public record PayloadField(String name, PayloadType type) {}
    public enum PayloadType { STRING, INT, BOOL, REF_ID }
}