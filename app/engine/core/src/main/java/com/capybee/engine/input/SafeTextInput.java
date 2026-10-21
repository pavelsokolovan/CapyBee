package com.capybee.engine.input;

import java.util.Objects;

public final class SafeTextInput {
    private final String text;

    SafeTextInput(String text) {
        this.text = Objects.requireNonNull(text, "text");
    }

    public String text() {
        return text;
    }

    @Override
    public String toString() {
        return "SafeTextInput[redacted]";
    }
}