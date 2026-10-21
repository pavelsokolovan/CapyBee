package com.capybee.engine.generation;

import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackSource;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FullGeneratedGameContractTest {
    private static final Path FULL = Path.of("..", "fixtures", "game-packs", "fixture-generated-full-minimal")
            .toAbsolutePath().normalize();
    private static final Path CONTENT = Path.of("..", "fixtures", "game-packs", "fixture-generated")
            .toAbsolutePath().normalize();

    @Test
    void distinguishesContentOnlyAndValidatedFullGameAndReportsDerivedFacts() {
        PackLoader loader = new PackLoader(Engine.sectionValidators());
        var content = loader.load(new PackId("fixture-generated"), new DirectoryPackSource(CONTENT));
        var full = loader.load(new PackId("fixture-generated-full-minimal"), new DirectoryPackSource(FULL));

        assertThat(content.generatedGameProfile()).hasValueSatisfying(profile ->
                assertThat(profile.kind()).isEqualTo(GeneratedGameProfile.Kind.CONTENT_ONLY));
        assertThat(full.generatedGameProfile()).hasValueSatisfying(profile -> {
            assertThat(profile.kind()).isEqualTo(GeneratedGameProfile.Kind.FULL_GAME);
            assertThat(profile.contractVersion()).isEqualTo(1);
            assertThat(profile.phases()).hasSize(5);
            assertThat(profile.reachableStoryScenes()).isEqualTo(7);
            assertThat(profile.endings()).isEqualTo(2);
            assertThat(profile.minimumStoryTransitions()).isEqualTo(4);
            assertThat(profile.maximumStoryTransitions()).isEqualTo(4);
            assertThat(profile.minimumRequiredProviderCalls()).isEqualTo(8);
        });
    }

    @Test
    void rejectsInsufficientBudgetWithStableCode() {
        DirectoryPackSource source = new DirectoryPackSource(FULL);
        PackSource invalid = override(source, "generation/catalog.json",
                source.read("generation/catalog.json").orElseThrow().replace("\"totalCallsPerRun\":8", "\"totalCallsPerRun\":7"));
        var result = new PackLoader(Engine.sectionValidators())
                .tryLoad(new PackId("fixture-generated-full-minimal"), invalid);
        assertThat(result.issues()).extracting(issue -> issue.code()).contains("PACK_RUN_BUDGET_INSUFFICIENT");
    }

    @Test
    void rejectsBackwardsRouteAndMissingSlotWithStableCodes() {
        DirectoryPackSource source = new DirectoryPackSource(FULL);
        String scenes = source.read("scenes/en/story.json").orElseThrow()
                .replace("\"next\":\"resolution\"", "\"next\":\"development-a\"");
        var backwards = new PackLoader(Engine.sectionValidators()).tryLoad(
                new PackId("fixture-generated-full-minimal"), override(source, "scenes/en/story.json", scenes));
        assertThat(backwards.issues()).extracting(issue -> issue.code()).contains("PACK_RUN_ROUTE_BACKWARDS");

        String catalog = source.read("generation/catalog.json").orElseThrow()
                .replaceAll("(?s),?\\s*\\{\"slotId\":\"challenge-slot\".*?\"toneTags\":\\[\"hopeful\"\\]}", "");
        var missing = new PackLoader(Engine.sectionValidators()).tryLoad(
                new PackId("fixture-generated-full-minimal"), override(source, "generation/catalog.json", catalog));
        assertThat(missing.issues()).extracting(issue -> issue.code()).contains("PACK_RUN_SCENE_SLOT_MISSING");
    }

    private static PackSource override(PackSource delegate, String path, String value) {
        return new PackSource() {
            @Override public Optional<String> read(String requested) {
                return requested.equals(path) ? Optional.of(value) : delegate.read(requested);
            }
            @Override public String describe() { return "full-game-overridden"; }
        };
    }
}
