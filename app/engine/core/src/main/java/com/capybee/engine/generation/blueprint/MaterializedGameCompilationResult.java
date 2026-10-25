package com.capybee.engine.generation.blueprint;

import java.util.Objects;

public sealed interface MaterializedGameCompilationResult permits MaterializedGameCompilationResult.Compiled,
        MaterializedGameCompilationResult.InvalidAcceptedInput, MaterializedGameCompilationResult.CompilerFailure {
    record Compiled(MaterializedGameDefinition definition) implements MaterializedGameCompilationResult {
        public Compiled { Objects.requireNonNull(definition); }
        @Override public String toString() { return "Compiled[definition=redacted]"; }
    }
    record InvalidAcceptedInput(String code) implements MaterializedGameCompilationResult {
        public InvalidAcceptedInput { Objects.requireNonNull(code); }
    }
    record CompilerFailure(String code) implements MaterializedGameCompilationResult {
        public CompilerFailure { Objects.requireNonNull(code); }
    }
}
