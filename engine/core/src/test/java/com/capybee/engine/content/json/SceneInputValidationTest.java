package com.capybee.engine.content.json;

import com.capybee.engine.api.Engine;
import com.capybee.engine.pack.DirectoryPackSource;
import com.capybee.engine.pack.PackId;
import com.capybee.engine.pack.PackSource;
import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SceneInputValidationTest {
    private static final PackId PACK_ID = new PackId("fixture-text-input");
    private static final Path PACK_PATH = Path.of("..", "fixtures", "game-packs", "fixture-text-input");
    private static final List<String> FILES = List.of("pack.json", "strings/en.json", "scenes/en/story.json");

    @Test
    void rejectsExplicitZeroTextLimit() {
        String scenes = packFiles().get("scenes/en/story.json").replace(
                "\"mode\": \"CHOICE_OR_TEXT\"", "\"mode\": \"CHOICE_OR_TEXT\", \"maxChars\": 0");

        assertThat(load(scenes).issues()).anyMatch(issue -> issue.code().equals("INPUT_LIMIT_INVALID"));
    }

    @Test
    void rejectsTextModeWithoutAChoiceSetWhenModeRequiresChoices() {
        String scenes = packFiles().get("scenes/en/story.json").replace(
                "\"options\": [{ \"id\": \"continue\", \"label\": \"Continue\", \"next\": \"text-scene\" }]",
                "\"options\": []");

        assertThat(load(scenes).issues()).anyMatch(issue -> issue.code().equals("INPUT_MODE_INVALID"));
    }

    @Test
    void rejectsFreeTextWhenPackPolicyDisablesIt() {
        Map<String, String> files = packFiles();
        files.put("pack.json", files.get("pack.json").replace("\"freeTextAllowed\": true", "\"freeTextAllowed\": false"));
        files.put("pack.json", files.get("pack.json").replaceAll(
            "(?s)\"startInputs\"\\s*:\\s*\\[.*?\\]", "\"startInputs\": []"));

        assertThat(load(files).issues()).anyMatch(issue -> issue.code().equals("FREE_TEXT_DISABLED"));
    }

    private com.capybee.engine.pack.LoadResult load(String scenes) {
        Map<String, String> files = packFiles();
        files.put("scenes/en/story.json", scenes);
        return load(files);
    }

    private com.capybee.engine.pack.LoadResult load(Map<String, String> files) {
        PackSource source = new PackSource() {
            @Override
            public Optional<String> read(String relativePath) {
                return Optional.ofNullable(files.get(relativePath));
            }

            @Override
            public String describe() {
                return "input-validation-test";
            }
        };
        return new PackLoader(Engine.sectionValidators()).tryLoad(PACK_ID, source);
    }

    private Map<String, String> packFiles() {
        DirectoryPackSource directory = new DirectoryPackSource(PACK_PATH);
        Map<String, String> files = new LinkedHashMap<>();
        for (String path : FILES) files.put(path, directory.read(path).orElseThrow());
        return files;
    }
}