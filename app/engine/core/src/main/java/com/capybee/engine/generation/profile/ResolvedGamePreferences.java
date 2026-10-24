package com.capybee.engine.generation.profile;

import com.capybee.engine.pack.LocaleTag;
import java.util.List;
import java.util.Optional;

public record ResolvedGamePreferences(ProfileIdentity profileIdentity, LocaleTag gameLocale,
        String lengthPresetId, String challengePresetId, Optional<String> genre,
        Optional<String> setting, Optional<String> tone, List<String> interests) {
    public ResolvedGamePreferences {
        genre = genre == null ? Optional.empty() : genre; setting = setting == null ? Optional.empty() : setting;
        tone = tone == null ? Optional.empty() : tone; interests = List.copyOf(interests);
    }
}
