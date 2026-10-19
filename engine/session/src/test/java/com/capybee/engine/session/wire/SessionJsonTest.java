package com.capybee.engine.session.wire;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionJsonTest {
    @Test
    void goldenRunSummaryRoundTrips() throws IOException {
        String golden;
        try (var input = getClass().getResourceAsStream("/wire/run-summary.json")) {
            golden = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        RunSummaryDto summary = SessionJson.decode(golden, RunSummaryDto.class);

        assertThat(summary.runId()).isEqualTo("5f2fc4c8-bfc4-4b2f-8fd6-04fd187d687e");
        assertThat(summary.status()).isEqualTo("ACTIVE");
        assertThat(summary.revision()).isEqualTo(4);
        assertThat(summary.packId()).isEqualTo("fixture-minimal");
        assertThat(summary.packVersion()).isEqualTo("1.0.0");
        assertThat(summary.locale()).isEqualTo("en");
        assertThat(summary.endReason()).isNull();
        assertThat(summary.failureCode()).isNull();
        assertThat(summary.viewSchema()).isEqualTo(1);
        assertThat(summary.view().blocks()).isEmpty();
        assertThat(summary.updatedAt()).isEqualTo(Instant.parse("2026-10-16T20:11:03Z"));
        assertThat(SessionJson.decode(SessionJson.encode(summary), RunSummaryDto.class)).isEqualTo(summary);
    }

    @Test
    void wireRecordsKeepTheirContractFieldsAndDefensiveCopies() {
        UUID commandId = UUID.randomUUID();
        var command = new CommandRequestDto(commandId, 3L,
                new CommandRequestDto.CommandDto("CHOOSE", "explore", null, null));
        var outcome = new CommandOutcomeDto(commandId, "APPLIED", true, 4, 4,
                "ACTIVE", null, 1, List.of(), List.of());
        var pack = new PackInfoDto("fixture-minimal", "1.0.0", List.of("en"), "en", List.of(), false);
        var error = new ErrorDto(new ErrorDto.Error("RUN_NOT_FOUND", "Run not found", Map.of()));

        assertThat(command.id()).isEqualTo(commandId);
        assertThat(command.expectedRevision()).isEqualTo(3);
        assertThat(command.command().type()).isEqualTo("CHOOSE");
        assertThat(command.command().optionId()).isEqualTo("explore");
        assertThat(command.command().text()).isNull();
        assertThat(command.command().placeId()).isNull();
        assertThat(outcome.commandId()).isEqualTo(commandId);
        assertThat(outcome.outcome()).isEqualTo("APPLIED");
        assertThat(outcome.accepted()).isTrue();
        assertThat(outcome.appliedRevision()).isEqualTo(4);
        assertThat(outcome.revision()).isEqualTo(4);
        assertThat(outcome.status()).isEqualTo("ACTIVE");
        assertThat(outcome.viewSchema()).isEqualTo(1);
        assertThat(outcome.events()).isEmpty();
        assertThat(outcome.issues()).isEmpty();
        assertThat(pack.id()).isEqualTo("fixture-minimal");
        assertThat(pack.version()).isEqualTo("1.0.0");
        assertThat(pack.locales()).containsExactly("en");
        assertThat(pack.defaultLocale()).isEqualTo("en");
        assertThat(pack.startInputs()).isEmpty();
        assertThat(pack.generated()).isFalse();
        assertThat(error.error().code()).isEqualTo("RUN_NOT_FOUND");
        assertThat(error.error().message()).isEqualTo("Run not found");
        assertThat(error.error().details()).isEmpty();
        assertThatThrownBy(() -> SessionJson.decode("{", RunSummaryDto.class))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("SESSION_JSON_DECODE_FAILED");
    }
}
