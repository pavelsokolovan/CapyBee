package com.capybee.engine.generation.profile;

import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackVersion;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** In-memory identity guard: one id/version can name only one normalized profile. */
public final class GeneratorProfileRegistry {
    private final Map<Key, ValidatedGeneratorProfile> profiles = new LinkedHashMap<>();

    public Optional<ProfileIssue> register(ValidatedGeneratorProfile profile) {
        Key key = new Key(profile.identity().id(), profile.identity().version());
        ValidatedGeneratorProfile existing = profiles.get(key);
        if (existing != null && !existing.identity().fingerprint().equals(profile.identity().fingerprint())) {
            return Optional.of(new ProfileIssue("PROFILE_IDENTITY_CONFLICT", "profile.json", "",
                    "Profile identity is already registered with different content"));
        }
        profiles.putIfAbsent(key, profile);
        return Optional.empty();
    }
    public Optional<ValidatedGeneratorProfile> find(PackId id, PackVersion version) {
        return Optional.ofNullable(profiles.get(new Key(id, version)));
    }
    private record Key(PackId id, PackVersion version) {}
}
