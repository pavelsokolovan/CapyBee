package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.ProfileIdentity;
import com.capybee.engine.pack.PackVersion;

final class DetectiveCaseContract {
    static final String CLUE_PREFIX = "clue-";
    static final String SUSPECT_PREFIX = "suspect-";
    static final String ACCUSE_PREFIX = "accuse-";
    private static final PackVersion FIRST_VERSION = new PackVersion(1, 2, 0);

    private DetectiveCaseContract() {}

    static boolean applies(ProfileIdentity profile) {
        return profile.id().value().equals("classic-detective")
                && profile.version().compareTo(FIRST_VERSION) >= 0;
    }
}
