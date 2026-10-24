package com.capybee.engine.generation.profile;

import java.util.Objects;

public final class ValidatedGeneratorProfile {
    private final GeneratorProfile profile;
    private final ProfileIdentity identity;

    ValidatedGeneratorProfile(GeneratorProfile profile, ProfileIdentity identity) {
        this.profile = Objects.requireNonNull(profile); this.identity = Objects.requireNonNull(identity);
    }
    public GeneratorProfile profile() { return profile; }
    public ProfileIdentity identity() { return identity; }
}
