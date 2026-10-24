package com.capybee.engine.generation.blueprint;

@FunctionalInterface
public interface BlueprintCancellation {
    BlueprintCancellation NEVER = () -> false;
    boolean cancelled();
}
