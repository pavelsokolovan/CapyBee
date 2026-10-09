package com.capybee.engine.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ReferenceIndexTest {
    @Test void referencesCanPrecedeDefinitionsAndKindsAreIndependent() {
        var index = new ReferenceIndex();
        index.reference("one", "id", "a.json", "/ref");
        index.define("one", "id", "b.json", "/id");
        index.define("two", "id", "c.json", "/id");
        assertTrue(index.check().isEmpty());
        assertTrue(index.check().isEmpty());
    }
    @Test void reportsAllDuplicatesAndDanglingReferencesWithLocations() {
        var index = new ReferenceIndex();
        index.define("one", "id", "a.json", "/first");
        index.define("one", "id", "b.json", "/second");
        index.reference("two", "id", "c.json", "/ref");
        var issues = index.check();
        assertEquals(2, issues.size());
        assertEquals("PACK_DUPLICATE_ID", issues.getFirst().code());
        assertEquals("b.json", issues.getFirst().file());
        assertEquals("/second", issues.getFirst().pointer());
        assertEquals("PACK_DANGLING_REF", issues.get(1).code());
        assertEquals("two:id", issues.get(1).message());
        assertThrows(UnsupportedOperationException.class, () -> issues.clear());
    }
}