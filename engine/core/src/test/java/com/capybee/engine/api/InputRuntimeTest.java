package com.capybee.engine.api;

import com.capybee.engine.adapter.Randoms;
import com.capybee.engine.adapter.SequentialIdSource;
import com.capybee.engine.input.InputResolution;
import com.capybee.engine.input.InputSafetyAction;
import com.capybee.engine.input.InputSafetyDecision;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackRegistry;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.port.InputGuard;
import com.capybee.engine.port.InputInterpreter;
import com.capybee.engine.api.json.SnapshotCodec;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InputRuntimeTest {
    private static final PackId PACK_ID = new PackId("fixture-text-input");

    private Engine engine(InputGuard guard, InputInterpreter interpreter) {
        var source = new DirectoryPackSource(Path.of("..", "fixtures", "game-packs", "fixture-text-input"));
        var pack = new PackLoader(Engine.sectionValidators()).load(PACK_ID, source);
        var builder = Engine.builder(new PackRegistry(List.of(pack)))
                .clock(Clock.fixed(Instant.parse("2026-10-13T10:00:00Z"), ZoneOffset.UTC))
                .random(Randoms.seeded(42L)).ids(new SequentialIdSource("input"));
        if (guard != null) builder.inputGuard(guard);
        if (interpreter != null) builder.inputInterpreter(interpreter);
        return builder.build();
    }

    private InputSafetyDecision allow() {
        return new InputSafetyDecision(InputSafetyAction.ALLOW, null, "OK");
    }

    @Test
    void scrubsBeforeGuardAndInterpreterAndNeverSnapshotsFreeTextStartInput() {
        AtomicReference<String> guarded = new AtomicReference<>();
        AtomicReference<String> interpreted = new AtomicReference<>();
        InputGuard guard = (input, policy) -> {
            guarded.set(input.text());
            return allow();
        };
        InputInterpreter interpreter = (input, context) -> {
            interpreted.set(input.text());
            assertThat(context.options()).extracting(option -> option.id()).containsExactly("continue");
            return new InputResolution.SelectOption("continue");
        };

        var started = engine(guard, interpreter).start(new RunRequest(PACK_ID, null,
                Map.of("note", "Contact alice@example.com"), 7L));

        assertThat(started.ok()).isTrue();
        assertThat(guarded.get()).isEqualTo("Contact [redacted:email]");
        assertThat(started.run().snapshot().startInputs()).doesNotContainKey("note");
        assertThat(SnapshotCodec.toJson(started.run().snapshot())).doesNotContain("alice@example.com");

        var result = started.run().apply(new Command.SubmitText("Please email bob@example.com"));

        assertThat(result.accepted()).isTrue();
        assertThat(guarded.get()).isEqualTo("Please email [redacted:email]");
        assertThat(interpreted.get()).isEqualTo("Please email [redacted:email]");
        assertThat(result.view().input().mode()).isEqualTo(InputMode.TEXT);
        assertThat(result.view().input().maxChars()).isEqualTo(120);
    }

    @Test
    void missingGuardFailsClosedButAuthoredChoiceRemainsUsable() {
        var run = engine(null, (input, context) -> new InputResolution.SelectOption("continue"))
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        RunSnapshot before = run.snapshot();

        var rejected = run.apply(new Command.SubmitText("hello"));

        assertThat(rejected.accepted()).isFalse();
        assertThat(rejected.issues()).extracting(Issue::code).containsExactly("INPUT_GUARD_UNAVAILABLE");
        assertThat(run.snapshot()).isEqualTo(before);
        assertThat(run.apply(new Command.Choose("continue")).accepted()).isTrue();
    }

    @Test
    void missingInterpreterAndInventedOptionAreRejectedWithoutStateChanges() {
        var withoutInterpreter = engine((input, policy) -> allow(), null)
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        RunSnapshot beforeUnavailable = withoutInterpreter.snapshot();

        var unavailable = withoutInterpreter.apply(new Command.SubmitText("hello"));

        assertThat(unavailable.issues()).extracting(Issue::code).containsExactly("INPUT_INTERPRETER_UNAVAILABLE");
        assertThat(withoutInterpreter.snapshot()).isEqualTo(beforeUnavailable);

        var withInventedOption = engine((input, policy) -> allow(),
                (input, context) -> new InputResolution.SelectOption("hidden-option"))
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        RunSnapshot beforeInvalid = withInventedOption.snapshot();

        var invalid = withInventedOption.apply(new Command.SubmitText("hello"));

        assertThat(invalid.issues()).extracting(Issue::code).containsExactly("INPUT_RESOLUTION_INVALID");
        assertThat(withInventedOption.snapshot()).isEqualTo(beforeInvalid);
        assertThat(invalid.events()).isEmpty();
    }

    @Test
    void blockedAndFailingGuardsRejectWithoutLeakingInputOrChangingState() {
        var blockedRun = engine((input, policy) -> new InputSafetyDecision(InputSafetyAction.BLOCK,
                com.capybee.engine.input.SafetyCategory.PERSONAL_DATA, "BLOCKED"),
                (input, context) -> new InputResolution.SelectOption("continue"))
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        RunSnapshot beforeBlocked = blockedRun.snapshot();

        var blocked = blockedRun.apply(new Command.SubmitText("private-sentinel"));

        assertThat(blocked.issues()).extracting(Issue::code).containsExactly("INPUT_BLOCKED");
        assertThat(blocked.issues().getFirst().message()).doesNotContain("private-sentinel");
        assertThat(blockedRun.snapshot()).isEqualTo(beforeBlocked);

        var failingRun = engine((input, policy) -> {
            throw new IllegalStateException("private-sentinel");
        }, (input, context) -> new InputResolution.SelectOption("continue"))
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        RunSnapshot beforeFailure = failingRun.snapshot();

        var failed = failingRun.apply(new Command.SubmitText("private-sentinel"));

        assertThat(failed.issues()).extracting(Issue::code).containsExactly("INPUT_GUARD_UNAVAILABLE");
        assertThat(failed.issues().getFirst().message()).doesNotContain("private-sentinel");
        assertThat(failingRun.snapshot()).isEqualTo(beforeFailure);
    }

    @Test
    void shortReplyIsTransientAndDoesNotAdvanceTheRun() {
        var run = engine((input, policy) -> allow(),
                (input, context) -> new InputResolution.ShortReply("A brief reply"))
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        RunSnapshot before = run.snapshot();

        var result = run.apply(new Command.SubmitText("hello"));

        assertThat(result.accepted()).isTrue();
        assertThat(result.view().blocks()).extracting(block -> block.text()).contains("A brief reply");
        assertThat(run.view().blocks()).extracting(block -> block.text()).doesNotContain("A brief reply");
        assertThat(run.snapshot()).isEqualTo(before);
        assertThat(result.events()).isEmpty();
    }

    @Test
    void unsafeGeneratedReplyIsNotRenderedAndItsContentIsNotReturnedInIssues() {
        var run = engine((input, policy) -> input.text().contains("[redacted:email]")
                        ? new InputSafetyDecision(InputSafetyAction.BLOCK,
                        com.capybee.engine.input.SafetyCategory.PERSONAL_DATA, "BLOCKED") : allow(),
                (input, context) -> new InputResolution.ShortReply("Contact private-sentinel@example.com"))
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        run.apply(new Command.Choose("continue"));
        RunSnapshot before = run.snapshot();

        var result = run.apply(new Command.SubmitText("hello"));

        assertThat(result.accepted()).isFalse();
        assertThat(result.issues()).extracting(Issue::code).containsExactly("INPUT_OUTPUT_UNSAFE");
        assertThat(result.issues().getFirst().message()).doesNotContain("private-sentinel");
        assertThat(result.view().blocks()).extracting(block -> block.text()).doesNotContain("private-sentinel@example.com");
        assertThat(run.snapshot()).isEqualTo(before);
    }

    @Test
    void legacySnapshotFreeTextIsRemovedWhenResumedAndSavedAgain() {
        var run = engine((input, policy) -> allow(), null)
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        RunSnapshot current = run.snapshot();
        RunSnapshot legacy = new RunSnapshot(current.snapshotVersion(), current.runId(), current.pack(), current.locale(),
                current.seed(), Map.of("note", "legacy-private-marker"), current.createdAt(), current.updatedAt(),
                current.step(), current.state());

        var resumed = engine((input, policy) -> allow(), null).resume(legacy);

        assertThat(resumed.ok()).isTrue();
        assertThat(resumed.run().snapshot().startInputs()).doesNotContainKey("note");
        assertThat(SnapshotCodec.toJson(resumed.run().snapshot())).doesNotContain("legacy-private-marker");
    }

    @Test
    void perSceneLimitUsesCodePointsAndRejectedTextLeavesRunUnchanged() {
        var run = engine((input, policy) -> allow(), (input, context) -> new InputResolution.NoMatch("NONE"))
                .start(new RunRequest(PACK_ID, null, Map.of(), 7L)).run();
        run.apply(new Command.Choose("continue"));
        RunSnapshot before = run.snapshot();

        var tooLong = run.apply(new Command.SubmitText("😀".repeat(121)));

        assertThat(tooLong.issues()).extracting(Issue::code).containsExactly("INPUT_TOO_LONG");
        assertThat(run.snapshot()).isEqualTo(before);
    }

    @Test
    void freeTextStartInputOverTheGlobalLimitUsesContentFreeIssueCode() {
        var result = engine((input, policy) -> allow(), null).start(new RunRequest(PACK_ID, null,
                Map.of("note", "private-sentinel" + "x".repeat(500)), 7L));

        assertThat(result.ok()).isFalse();
        assertThat(result.issues()).extracting(Issue::code).contains("INPUT_TOO_LONG");
        assertThat(result.issues().stream().map(Issue::message).toList()).doesNotContain("private-sentinel");
    }
}