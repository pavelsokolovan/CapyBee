package com.capybee.engine.pack;

public record LocaleTag(String value) {
    public static boolean isValid(String text) {
        return text != null && text.matches("[a-z]{2,3}(-[A-Z]{2})?");
    }

    public LocaleTag {
        if (!isValid(value)) throw new IllegalArgumentException("Invalid locale tag: " + value);
    }
}