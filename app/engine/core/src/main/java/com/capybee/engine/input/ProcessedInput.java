package com.capybee.engine.input;

import java.util.Set;

public record ProcessedInput(SafeTextInput input, boolean changed, Set<PiiKind> detectedKinds) {
    public ProcessedInput {
        detectedKinds = Set.copyOf(detectedKinds);
    }

    @Override
    public String toString() {
        return "ProcessedInput[changed=" + changed + ", detectedKinds=" + detectedKinds + "]";
    }
}