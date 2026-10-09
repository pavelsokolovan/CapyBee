package com.capybee.engine.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PackVersionTest {
    @Test void parsesAndOrdersNumericVersions() {
        var version = PackVersion.tryParse("1.2.3").orElseThrow();
        assertEquals(new PackVersion(1, 2, 3), version);
        assertEquals("1.2.3", version.toString());
        assertTrue(version.compareTo(new PackVersion(2, 0, 0)) < 0);
        assertTrue(version.compareTo(new PackVersion(1, 3, 0)) < 0);
        assertTrue(version.compareTo(new PackVersion(1, 2, 2)) > 0);
        assertEquals(0, version.compareTo(version));
        assertTrue(PackVersion.tryParse("0.0.0").isPresent());
    }
    @Test void rejectsInvalidAndOverflowingVersions() {
        for (String value : new String[]{"1.2", "01.2.3", "1.2.3-rc1", "-1.2.3", "1.2.3 ", "999999999999.2.3"}) {
            assertTrue(PackVersion.tryParse(value).isEmpty());
        }
        assertTrue(PackVersion.tryParse(null).isEmpty());
        assertThrows(IllegalArgumentException.class, () -> new PackVersion(-1, 0, 0));
        assertThrows(IllegalArgumentException.class, () -> new PackVersion(0, -1, 0));
        assertThrows(IllegalArgumentException.class, () -> new PackVersion(0, 0, -1));
    }
}