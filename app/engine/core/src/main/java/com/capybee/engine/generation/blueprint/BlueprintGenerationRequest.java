package com.capybee.engine.generation.blueprint;

import com.capybee.engine.generation.profile.*;
import com.capybee.engine.port.InputGuard;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/** In-memory start-operation input. The optional suggestion is deliberately redacted from toString. */
public final class BlueprintGenerationRequest {
    private final String preparationId;
    private final long confirmedRevision;
    private final long currentRevision;
    private final ValidatedGeneratorProfile profile;
    private final ResolvedGamePreferences preferences;
    private final EffectiveGenerationConstraints constraints;
    private final String suggestion;
    private final InputGuard inputGuard;
    private final BlueprintCancellation cancellation;
    private final Instant deadline;

    public BlueprintGenerationRequest(String preparationId, long confirmedRevision, long currentRevision,
            ValidatedGeneratorProfile profile, ResolvedGamePreferences preferences,
            EffectiveGenerationConstraints constraints, String suggestion, InputGuard inputGuard,
            BlueprintCancellation cancellation, Instant deadline) {
        this.preparationId = Objects.requireNonNull(preparationId); this.confirmedRevision = confirmedRevision;
        this.currentRevision = currentRevision; this.profile = Objects.requireNonNull(profile);
        this.preferences = Objects.requireNonNull(preferences); this.constraints = Objects.requireNonNull(constraints);
        this.suggestion = suggestion; this.inputGuard = inputGuard;
        this.cancellation = cancellation == null ? BlueprintCancellation.NEVER : cancellation; this.deadline = deadline;
        if (preparationId.isBlank() || preparationId.length() > 128 || confirmedRevision < 0 || currentRevision < 0) {
            throw new IllegalArgumentException("Invalid blueprint preparation identity");
        }
    }
    public String preparationId(){return preparationId;} public long confirmedRevision(){return confirmedRevision;}
    public long currentRevision(){return currentRevision;} public ValidatedGeneratorProfile profile(){return profile;}
    public ResolvedGamePreferences preferences(){return preferences;} public EffectiveGenerationConstraints constraints(){return constraints;}
    public Optional<String> suggestion(){return Optional.ofNullable(suggestion);} public InputGuard inputGuard(){return inputGuard;}
    public BlueprintCancellation cancellation(){return cancellation;} public Instant deadline(){return deadline;}
    @Override public String toString(){return "BlueprintGenerationRequest[preparationId="+preparationId+", suggestion=redacted]";}
}
