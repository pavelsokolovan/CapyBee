package com.capybee.engine.pack;

public record PackId(String value) {
    public static boolean isValid(String text) {
        return text != null && text.length() <= 48 && text.matches("[a-z][a-z0-9]*(-[a-z0-9]+)*");
    }

    public PackId {
        if (!isValid(value)) throw new IllegalArgumentException("Invalid pack id: " + value);
    }
}