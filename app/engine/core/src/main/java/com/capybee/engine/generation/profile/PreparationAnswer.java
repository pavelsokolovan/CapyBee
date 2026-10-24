package com.capybee.engine.generation.profile;

import java.util.List;

/** Typed draft state; absence, explicit clear and selection are deliberately distinct. */
public sealed interface PreparationAnswer permits PreparationAnswer.Cleared, PreparationAnswer.Selected {
    record Cleared() implements PreparationAnswer {}
    record Selected(List<String> values) implements PreparationAnswer {
        public Selected { values = List.copyOf(values); }
        public static Selected one(String value) { return new Selected(List.of(value)); }
    }
}
