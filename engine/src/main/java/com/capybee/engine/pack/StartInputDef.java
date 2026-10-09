package com.capybee.engine.pack;

import java.util.List;

public sealed interface StartInputDef {
    String id();
    boolean required();
    String labelKey();

    record EnumInput(String id, boolean required, String labelKey, List<String> values) implements StartInputDef {
        public EnumInput { values = List.copyOf(values); }
    }

    record EnumSetInput(String id, boolean required, String labelKey, List<String> values, int maxItems)
            implements StartInputDef {
        public EnumSetInput { values = List.copyOf(values); }
    }

    record FreeTextInput(String id, boolean required, String labelKey, int maxChars) implements StartInputDef {}
}