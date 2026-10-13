package com.capybee.engine.input;

import java.util.Set;

public record ScrubResult(String safeText, boolean changed, Set<PiiKind> detectedKinds) {
    public ScrubResult {
        detectedKinds = Set.copyOf(detectedKinds);
    }

    @Override
    public String toString() {
        return "ScrubResult[changed=" + changed + ", detectedKinds=" + detectedKinds + "]";
    }
}