package com.capybee.engine.places.json;

import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.LoadResult;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackSource;
import com.capybee.engine.pack.json.PackLoader;
import com.capybee.engine.places.PlaceLayoutKind;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PlacesParserTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final PackId PACK_ID = new PackId("fixture-text-dungeon");
    private final DirectoryPackSource directory = new DirectoryPackSource(
            Path.of("..", "fixtures", "game-packs", "fixture-text-dungeon"));

    @Test
    void parsesValidatedMapInDefinitionOrderAndPassesThroughLayoutMetadata() {
        LoadResult loaded = load(sourceWith(Map.of()));

        assertThat(loaded.ok()).isTrue();
        var map = PlacesParser.parse(loaded.pack().orElseThrow());
        assertThat(map.layoutKind()).isEqualTo(PlaceLayoutKind.GRAPH);
        assertThat(map.startPlace()).isEqualTo("cell");
        assertThat(map.places()).extracting(place -> place.id())
                .containsExactly("cell", "corridor", "armory", "stairs", "exit");
        assertThat(map.links()).hasSize(4);
        assertThat(map.places().getFirst().position().x()).isZero();
        assertThatThrownBy(() -> map.places().clear()).isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void requiresTheFeatureAndIndexedFileOnlyWhenPlacesAreEnabled() {
        ObjectNode manifest = object(directory.read("pack.json").orElseThrow());
        ArrayNode files = (ArrayNode) manifest.get("files");
        for (int index = files.size() - 1; index >= 0; index--) {
            if (files.get(index).asString().equals("places.json")) files.remove(index);
        }
        LoadResult missing = load(sourceWith(Map.of("pack.json", manifest.toString())));
        assertThat(missing.issues()).extracting(issue -> issue.code()).contains("PACK_REQUIRED_FILE_MISSING");

        manifest = object(directory.read("pack.json").orElseThrow());
        manifest.set("features", MAPPER.createArrayNode());
        LoadResult disabled = load(sourceWith(Map.of("pack.json", manifest.toString())));
        assertThat(disabled.issues()).extracting(issue -> issue.code()).contains("PLACES_FEATURE_REQUIRED");

    }

    @Test
    void reportsDuplicateIdsLinksInvalidCoordinatesAndDanglingLocaleReferences() {
        ObjectNode places = object(directory.read("places.json").orElseThrow());
        ArrayNode placeEntries = (ArrayNode) places.get("places");
        placeEntries.add(placeEntries.get(0).deepCopy());
        ObjectNode firstPosition = (ObjectNode) placeEntries.get(1).get("position");
        firstPosition.put("x", 1001);
        ArrayNode links = (ArrayNode) places.get("links");
        links.add(links.get(0).deepCopy());
        ObjectNode strings = object(directory.read("strings/en.json").orElseThrow());
        strings.remove("place.armory");

        LoadResult result = load(sourceWith(Map.of(
                "places.json", places.toString(),
                "strings/en.json", strings.toString())));

        assertThat(result.ok()).isFalse();
        assertThat(result.issues()).extracting(issue -> issue.code())
                .contains("PACK_DUPLICATE_ID", "PLACES_FIELD_INVALID", "PACK_DANGLING_REF");
    }

    @Test
    void validatesLayoutKindsAndWarnsForDirectionallyUnreachablePlaces() {
        for (PlaceLayoutKind kind : PlaceLayoutKind.values()) {
            ObjectNode places = object(directory.read("places.json").orElseThrow());
            ((ObjectNode) places.get("layout")).put("kind", kind.name());
            assertThat(load(sourceWith(Map.of("places.json", places.toString()))).ok()).isTrue();
        }

        ObjectNode places = object(directory.read("places.json").orElseThrow());
        places.put("startPlace", "corridor");
        ((ObjectNode) ((ArrayNode) places.get("links")).get(0)).put("bidirectional", false);
        LoadResult result = load(sourceWith(Map.of("places.json", places.toString())));

        assertThat(result.ok()).isTrue();
        assertThat(result.issues()).anyMatch(issue -> issue.code().equals("PLACE_UNREACHABLE"));

        ((ObjectNode) places.get("layout")).put("kind", "UNKNOWN");
        LoadResult invalidLayout = load(sourceWith(Map.of("places.json", places.toString())));
        assertThat(invalidLayout.issues()).extracting(issue -> issue.code()).contains("PLACES_FIELD_INVALID");
    }

    private LoadResult load(PackSource source) {
        return new PackLoader(Engine.sectionValidators()).tryLoad(PACK_ID, source);
    }

    private PackSource sourceWith(Map<String, String> replacements) {
        return new PackSource() {
            @Override
            public Optional<String> read(String relativePath) {
                return replacements.containsKey(relativePath)
                        ? Optional.of(replacements.get(relativePath)) : directory.read(relativePath);
            }

            @Override
            public String describe() {
                return directory.describe();
            }
        };
    }

    private static ObjectNode object(String json) {
        return (ObjectNode) MAPPER.readTree(json);
    }
}
