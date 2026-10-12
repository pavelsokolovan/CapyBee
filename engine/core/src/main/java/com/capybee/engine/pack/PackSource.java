package com.capybee.engine.pack;

import java.util.Optional;

public interface PackSource {
    Optional<String> read(String relativePath);
    String describe();
}