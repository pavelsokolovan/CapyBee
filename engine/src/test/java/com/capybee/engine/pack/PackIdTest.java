package com.capybee.engine.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackIdTest {
    @Test void acceptsValidIdsAndLengthBoundary() {
        assertEquals("a", new PackId("a").value());
        assertTrue(PackId.isValid("fixture-1"));
        assertTrue(PackId.isValid("a".repeat(48)));
    }
    @Test void rejectsInvalidIds() {
        for (String value : new String[]{"", "A", "1a", "a--b", "a-", "a_b", "a.b", "a".repeat(49)}) {
            assertFalse(PackId.isValid(value));
            assertThrows(IllegalArgumentException.class, () -> new PackId(value));
        }
        assertFalse(PackId.isValid(null));
        assertThrows(IllegalArgumentException.class, () -> new PackId(null));
    }
}