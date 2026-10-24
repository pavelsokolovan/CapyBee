package com.capybee.engine.generation.profile;

/** Narrow bridge used only by the strict loader to create the validated boundary. */
public final class ProfileValidationFactory {
    private ProfileValidationFactory() {}
    public static ValidatedGeneratorProfile validated(GeneratorProfile profile, ProfileIdentity identity) {
        return new ValidatedGeneratorProfile(profile, identity);
    }
}
