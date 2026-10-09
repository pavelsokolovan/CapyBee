package com.capybee.engine.pack;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocaleTagTest {
    @Test void acceptsLocaleTags() {
        for (String value : new String[]{"pl", "en", "eng", "pt-BR"}) {
            assertTrue(LocaleTag.isValid(value));
            assertEquals(value, new LocaleTag(value).value());
        }
    }
    @Test void rejectsInvalidTags() {
        for (String value : new String[]{"", "p", "engl", "EN", "pt-br", "en_US", "en-USA"}) {
            assertFalse(LocaleTag.isValid(value));
            assertThrows(IllegalArgumentException.class, () -> new LocaleTag(value));
        }
        assertFalse(LocaleTag.isValid(null));
    }
}