package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.ProfileIdentity;
import com.capybee.engine.pack.PackVersion;

final class DetectiveCaseContract {
    static final String CLUE_PREFIX = "clue-";
    static final String SUSPECT_PREFIX = "suspect-";
    static final String ACCUSE_PREFIX = "accuse-";
    static final String COMPANION_ID = "capybee";
    static final String DETECTIVE_ID = "detective";
    static final String CLIENT_ID = "client";
    private static final PackVersion CLUE_VERSION = new PackVersion(1, 2, 0);
    private static final PackVersion CAST_VERSION = new PackVersion(1, 3, 0);

    private DetectiveCaseContract() {}

    static boolean applies(ProfileIdentity profile) {
        return isDetectiveProfile(profile) && profile.version().compareTo(CLUE_VERSION) >= 0;
    }

    static boolean requiresRecurringCast(ProfileIdentity profile) {
        return isDetectiveProfile(profile) && profile.version().compareTo(CAST_VERSION) >= 0;
    }

    static boolean isDetectiveProfile(ProfileIdentity profile) {
        return profile.id().value().equals("classic-detective");
    }
}
