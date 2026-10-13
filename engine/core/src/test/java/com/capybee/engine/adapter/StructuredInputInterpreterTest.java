package com.capybee.engine.adapter;

import com.capybee.engine.api.InputMode;
import com.capybee.engine.input.InputContext;
import com.capybee.engine.input.InputResolution;
import com.capybee.engine.input.PiiScrubber;
import com.capybee.engine.input.SafeTextInput;
import com.capybee.engine.port.StructuredGenerationRequest;
import com.capybee.engine.port.TextGenerationException;
import com.capybee.engine.port.TextGenerationResult;
import com.capybee.engine.port.TextGenerator;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StructuredInputInterpreterTest {
    private SafeTextInput safeText(String text) {
        return new PiiScrubber().process(text).input();
    }

    private InputContext context() {
        return new InputContext("en", InputMode.CHOICE_OR_TEXT, 120,
                List.of(new InputContext.InputOption("continue", "Continue", true),
                        new InputContext.InputOption("locked", "Locked", false)),
                "general", Set.of("personal-data"), "policy-v1");
    }

    private TextGenerationResult result(String json) {
        return new TextGenerationResult(json, null, "test-model", TextGenerationResult.FinishReason.COMPLETE);
    }

    @Test
    void sendsOnlySafeInputAndEnabledOptionsInASeparateUntrustedJsonField() {
        AtomicReference<StructuredGenerationRequest> captured = new AtomicReference<>();
        TextGenerator generator = request -> {
            captured.set(request);
            return result("{\"resolution\":\"SELECT_OPTION\",\"optionId\":\"continue\"}");
        };
        var interpreter = new StructuredInputInterpreter(generator);

        InputResolution resolution = interpreter.interpret(safeText("scrubbed [redacted:email]"), context());

        assertThat(resolution).isEqualTo(new InputResolution.SelectOption("continue"));
        assertThat(captured.get().taskId()).isEqualTo("INPUT_RESOLUTION");
        assertThat(captured.get().maxOutputTokens()).isEqualTo(400);
        assertThat(captured.get().untrustedDataJson()).contains("scrubbed [redacted:email]", "locked", "enabled");
        assertThat(captured.get().taskInstructions()).doesNotContain("scrubbed");
        assertThat(captured.get().toString()).doesNotContain("redacted:email");
    }

    @Test
    void rejectsUnknownOrDisabledOptionIdsAndExtraProperties() {
        for (String output : List.of(
                "{\"resolution\":\"SELECT_OPTION\",\"optionId\":\"missing\"}",
                "{\"resolution\":\"SELECT_OPTION\",\"optionId\":\"locked\"}",
                "{\"resolution\":\"SELECT_OPTION\",\"optionId\":\"continue\",\"route\":\"invented\"}")) {
            var interpreter = new StructuredInputInterpreter(request -> result(output));
            assertThat(interpreter.interpret(safeText("hello"), context()))
                    .isEqualTo(new InputResolution.NoMatch("GENERATION_OUTPUT_INVALID"));
        }
    }

    @Test
    void rejectsMalformedOutputAndMapsProviderFailureToSafeNoMatch() {
        var malformed = new StructuredInputInterpreter(request -> result("```json\n{}\n```"));

        assertThat(malformed.interpret(safeText("hello"), context()))
                .isEqualTo(new InputResolution.NoMatch("GENERATION_OUTPUT_INVALID"));
    }

    @Test
    void providerFailurePropagatesOnlyItsClosedCodeForRuntimeSafeMapping() {
        var unavailable = new StructuredInputInterpreter(request -> {
            throw new IllegalStateException("private-provider-detail");
        });

        assertThatThrownBy(() -> unavailable.interpret(safeText("hello"), context()))
                .isInstanceOf(TextGenerationException.class)
                .hasMessage("UNAVAILABLE")
                .hasNoCause()
                .hasToString("TextGenerationException[UNAVAILABLE]");
    }

    @Test
    void providerRefusalReturnsBlockedWithoutExposingItsText() {
        var refusal = new StructuredInputInterpreter(request -> new TextGenerationResult(
                "private-provider-detail", null, "test-model", TextGenerationResult.FinishReason.REFUSED));

        assertThat(refusal.interpret(safeText("hello"), context()))
                .isEqualTo(new InputResolution.Blocked("GENERATION_PROVIDER_REFUSED"));
    }

    @Test
    void acceptsBoundedShortReplyForTheRunToSafetyCheck() {
        var interpreter = new StructuredInputInterpreter(request -> result(
                "{\"resolution\":\"SHORT_REPLY\",\"text\":\"A short, kind reply.\"}"));

        assertThat(interpreter.interpret(safeText("hello"), context()))
                .isEqualTo(new InputResolution.ShortReply("A short, kind reply."));
    }
}