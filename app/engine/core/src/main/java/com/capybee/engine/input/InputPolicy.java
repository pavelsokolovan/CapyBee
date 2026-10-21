package com.capybee.engine.input;

import java.util.Set;

public record InputPolicy(boolean freeTextAllowed, int maxInputCodePoints, int maxReplyCodePoints,
                          int maxReplyLines, String audience, Set<String> forbiddenTags,
                          String policyVersion) {
    public InputPolicy {
        forbiddenTags = Set.copyOf(forbiddenTags);
    }
}