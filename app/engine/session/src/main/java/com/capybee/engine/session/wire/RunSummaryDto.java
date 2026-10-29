package com.capybee.engine.session.wire;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.View;

import java.time.Instant;

public record RunSummaryDto(String runId, String status, long revision, SourceDto source,
                            String packId, String packVersion,
                            String locale, EndReason endReason, String failureCode, int viewSchema,
                            View view, Instant updatedAt) {
    public RunSummaryDto(String runId, String status, long revision, String packId, String packVersion,
                         String locale, EndReason endReason, String failureCode, int viewSchema,
                         View view, Instant updatedAt) {
        this(runId, status, revision,
                new SourceDto("PACK", packId, packVersion, null, null, null), packId, packVersion,
                locale, endReason, failureCode, viewSchema, view, updatedAt);
    }
    public record SourceDto(String kind, String packId, String packVersion,
                            String profileId, String profileVersion, String gameFingerprint) {}
}
