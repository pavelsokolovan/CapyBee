package com.capybee.server.game;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GeneratorProfileCatalogTest {
    private static final Path PROFILES = Path.of("..", "engine", "generator-profiles");

    @Test void loadsOnlyValidatedAllowlistedProfiles() {
        var catalog = new GeneratorProfileCatalog(true, PROFILES, List.of("generic-adventure"));
        assertThat(catalog.enabled()).isTrue();
        assertThat(catalog.all()).hasSize(1);
        assertThat(catalog.find("generic-adventure").orElseThrow().identity().fingerprint()).isNotBlank();
        assertThat(catalog.find("not-allowlisted")).isEmpty();
    }

    @Test void disabledCatalogNeedsNoProviderFiles() {
        var catalog = new GeneratorProfileCatalog(false, null, List.of());
        assertThat(catalog.enabled()).isFalse();
        assertThat(catalog.all()).isEmpty();
    }

    @Test void enabledCatalogFailsClosedForMissingProfile() {
        assertThatThrownBy(() -> new GeneratorProfileCatalog(true, PROFILES, List.of("missing-profile")))
                .isInstanceOf(IllegalStateException.class).hasMessage("DYNAMIC_PROFILE_INVALID");
    }
}
