package com.capybee.engine.pack;

import com.capybee.engine.pack.json.PackLoader;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class PackRegistryTest {
    private Pack pack() {
        return new PackLoader(List.of()).load(new PackId("fixture-minimal"),
                new ClasspathPackSource(getClass().getClassLoader(), "game-packs/fixture-minimal"));
    }

    @Test void findsRequiresAndListsImmutablePacks() {
        Pack pack = pack();
        var registry = new PackRegistry(List.of(pack));
        assertEquals(pack, registry.find(pack.manifest().id()).orElseThrow());
        assertEquals(pack, registry.require(pack.manifest().id()));
        assertEquals(List.of(pack), List.copyOf(registry.all()));
        assertTrue(registry.find(new PackId("absent")).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> registry.require(new PackId("absent")));
        assertThrows(UnsupportedOperationException.class, () -> registry.all().clear());
        assertTrue(new PackRegistry(List.of()).all().isEmpty());
    }

    @Test void rejectsDuplicateIds() {
        Pack pack = pack();
        assertThrows(IllegalArgumentException.class, () -> new PackRegistry(List.of(pack, pack)));
    }

    @Test void modelCollectionsCannotBeMutated() {
        Pack pack = pack();
        assertThrows(UnsupportedOperationException.class, () -> pack.files().clear());
        assertTrue(pack.file("absent.json").isEmpty());
        assertThrows(IllegalStateException.class, () -> pack.requireFile("absent.json"));
        assertThrows(UnsupportedOperationException.class, () -> pack.manifest().locales().clear());
        assertThrows(UnsupportedOperationException.class, () -> pack.manifest().files().clear());
        assertThrows(UnsupportedOperationException.class, () -> pack.manifest().startInputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> pack.manifest().features().clear());
        assertThrows(UnsupportedOperationException.class, () -> pack.manifest().events().clear());
        assertThrows(UnsupportedOperationException.class, () -> pack.manifest().contentPolicy().forbiddenTags().clear());
        assertNull(pack.manifest().contentPolicy().audience());
        assertNull(pack.manifest().description());
    }
}