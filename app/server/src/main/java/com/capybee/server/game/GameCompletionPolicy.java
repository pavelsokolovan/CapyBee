package com.capybee.server.game;

import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Optional;

/** Host-reviewed bindings; generated prose and resultClass are never identifiers. */
@Component
public class GameCompletionPolicy {
    public static final String RESULT_KEY = "capybee_rpg";
    public static final String REST = "rest";
    public record Suggestion(String key, int version) {}
    public Optional<Suggestion> suggest(String packId, String version, String outcome, String scene) {
        if (!"lanterns-of-willow-marsh".equals(packId) || !"1.0.0".equals(version)) return Optional.empty();
        String key = outcome == null ? null : Map.of(
                "radiant-festival", "say_thank_you_helper",
                "gentle-lighting", "notice_small_win_today",
                "festival-postponed", REST).get(outcome);
        return key == null ? Optional.empty() : Optional.of(new Suggestion(key, 1));
    }
}
