package com.capybee.engine.session;

import com.capybee.engine.generation.profile.EffectiveGenerationConstraints;
import com.capybee.engine.generation.profile.ResolvedGamePreferences;
import com.capybee.engine.generation.profile.ValidatedGeneratorProfile;
import java.util.Objects;

/** Ephemeral, deliberately non-record preparation capability. */
public final class ConfirmedDynamicPreparation {
    private final ValidatedGeneratorProfile profile;
    private final ResolvedGamePreferences preferences;
    private final EffectiveGenerationConstraints constraints;
    private final long revision;
    private final String suggestion;
    public ConfirmedDynamicPreparation(ValidatedGeneratorProfile profile, ResolvedGamePreferences preferences,
            EffectiveGenerationConstraints constraints, long revision, String suggestion) {
        this.profile=Objects.requireNonNull(profile);this.preferences=Objects.requireNonNull(preferences);
        this.constraints=Objects.requireNonNull(constraints);if(revision<1)throw new IllegalArgumentException("revision");
        this.revision=revision;this.suggestion=suggestion==null||suggestion.isBlank()?null:suggestion;
    }
    public ValidatedGeneratorProfile profile(){return profile;}
    public ResolvedGamePreferences preferences(){return preferences;}
    public EffectiveGenerationConstraints constraints(){return constraints;}
    public long revision(){return revision;}
    String suggestion(){return suggestion;}
    String signature(){return profile.identity()+"|"+preferences+"|"+constraints+"|"+revision+"|"+(suggestion!=null);}
    @Override public String toString(){return "ConfirmedDynamicPreparation[profile="+profile.identity()+", revision="+revision+", suggestion=redacted]";}
}
