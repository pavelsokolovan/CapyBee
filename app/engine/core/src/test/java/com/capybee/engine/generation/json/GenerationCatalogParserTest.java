package com.capybee.engine.generation.json;

import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackSource;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class GenerationCatalogParserTest {
    private static final Path PACK = Path.of("..", "fixtures", "game-packs", "fixture-generated")
            .toAbsolutePath().normalize();

    @Test
    void loadsCatalogAndTrustedSceneSlots() {
        var result = new PackLoader(Engine.sectionValidators())
                .tryLoad(new PackId("fixture-generated"), new DirectoryPackSource(PACK));

        assertThat(result.ok()).isTrue();
        assertThat(GenerationCatalogParser.parse(result.pack().orElseThrow()).sceneSlots())
                .extracting(slot -> slot.sceneId())
                .containsExactly("opening", "meeting");
    }

    @Test
    void rejectsCatalogFallbackPathOutsideGenerationFallbackDirectory() {
        DirectoryPackSource source = new DirectoryPackSource(PACK);
        PackSource invalid = new OverriddenSource(source, "generation/catalog.json",
                source.read("generation/catalog.json").orElseThrow()
                        .replace("generation/fallbacks/scenario.json", "../scenario.json"));

        var result = new PackLoader(Engine.sectionValidators())
                .tryLoad(new PackId("fixture-generated"), invalid);

        assertThat(result.ok()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("GENERATION_CATALOG_INVALID");
    }

    @Test
    void rejectsMissingCatalogBeforePackCanBeUsed() {
        DirectoryPackSource source = new DirectoryPackSource(PACK);
        PackSource missing = new PackSource() {
            @Override public Optional<String> read(String relativePath) {
                return relativePath.equals("generation/catalog.json") ? Optional.empty() : source.read(relativePath);
            }
            @Override public String describe() { return "catalog-missing"; }
        };

        var result = new PackLoader(Engine.sectionValidators())
                .tryLoad(new PackId("fixture-generated"), missing);

        assertThat(result.ok()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("PACK_FILE_MISSING");
    }

            @Test
            void rejectsInvalidFallbackDuringPackLoading() {
            DirectoryPackSource source = new DirectoryPackSource(PACK);
            PackSource invalidFallback = new OverriddenSource(source, "generation/fallbacks/cast.json", "{\"unknown\":true}");

            var result = new PackLoader(Engine.sectionValidators())
                .tryLoad(new PackId("fixture-generated"), invalidFallback);

            assertThat(result.ok()).isFalse();
            assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("GENERATION_FALLBACK_INVALID");
            }

            @Test
            void rejectsTemplateObjectiveReferencesThatAreNotDeclaredByThePack() {
            DirectoryPackSource source = new DirectoryPackSource(PACK);
            PackSource invalidTemplate = new OverriddenSource(source, "generation/templates.json",
                source.read("generation/templates.json").orElseThrow()
                    .replace("\"objectiveIds\":[]", "\"objectiveIds\":[\"missing-objective\"]"));

            var result = new PackLoader(Engine.sectionValidators())
                .tryLoad(new PackId("fixture-generated"), invalidTemplate);

            assertThat(result.ok()).isFalse();
            assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("GENERATION_REFERENCE_INVALID");
            }

    private record OverriddenSource(PackSource delegate, String path, String value) implements PackSource {
        @Override public Optional<String> read(String requestedPath) {
            return requestedPath.equals(path) ? Optional.of(value) : delegate.read(requestedPath);
        }
        @Override public String describe() { return "catalog-overridden"; }
    }
}