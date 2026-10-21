package com.capybee.engine.session.wire;

import com.capybee.engine.api.EndReason;
import com.capybee.engine.api.View;

import java.time.Instant;

public record RunSummaryDto(String runId, String status, long revision, String packId, String packVersion,
                            String locale, EndReason endReason, String failureCode, int viewSchema,
                            View view, Instant updatedAt) {}
