package com.capybee.engine.pack;

import java.util.List;

public record ContentPolicy(boolean freeTextAllowed, Audience audience, List<String> forbiddenTags) {
    public ContentPolicy { forbiddenTags = List.copyOf(forbiddenTags); }
    public record Audience(int minAge, int maxAge) {}
}