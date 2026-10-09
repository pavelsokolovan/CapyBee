package com.capybee.engine.pack;

import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PackFixturesLoadTest {
    @ParameterizedTest
    @CsvSource({"fixture-minimal,pl,0,1", "fixture-text-dungeon,en,1,0", "fixture-space-courier,en,2,3"})
    void unrelatedPackShapesLoadThroughSameApi(String id, String locale, int features, int inputs) {
        var loader = new PackLoader(List.of());
        var result = loader.tryLoad(new PackId(id), new ClasspathPackSource(getClass().getClassLoader(), "game-packs/" + id));
        assertTrue(result.ok(), result.issues().toString());
        var manifest = result.pack().orElseThrow().manifest();
        assertEquals(locale, manifest.defaultLocale().value());
        assertEquals(features, manifest.features().size());
        assertEquals(inputs, manifest.startInputs().size());
        if (inputs == 0) assertEquals("{}", result.pack().orElseThrow().requireFile("strings/en.json").strip());
    }
}