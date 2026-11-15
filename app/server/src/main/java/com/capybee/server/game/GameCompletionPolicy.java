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
        if ("capybee-new-hive".equals(packId)) {
            if (!java.util.Set.of("1.0.0", "1.1.0", "1.2.0").contains(version == null ? "" : version)) return Optional.empty();
            String key = switch (outcome == null ? "" : outcome) {
                case "new-path" -> "ending-new".equals(scene) ? "say_thank_you_helper" : null;
                case "two-lights" -> "ending-two".equals(scene) ? "listen_song_reminds_home" : null;
                case "rest-here" -> "ending-rest".equals(scene) ? REST : null;
                default -> null;
            };
            return key == null ? Optional.empty() : Optional.of(new Suggestion(key, 1));
        }
        if (!"lanterns-of-willow-marsh".equals(packId) || !"1.0.0".equals(version)) return Optional.empty();
        String key = outcome == null ? null : Map.of(
                "radiant-festival", "say_thank_you_helper",
                "gentle-lighting", "notice_small_win_today",
                "festival-postponed", REST).get(outcome);
        return key == null ? Optional.empty() : Optional.of(new Suggestion(key, 1));
    }
}
