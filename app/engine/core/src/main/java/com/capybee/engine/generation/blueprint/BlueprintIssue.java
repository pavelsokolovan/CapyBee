package com.capybee.engine.generation.blueprint;

/** A content-safe diagnostic produced while reading an untrusted blueprint. */
public record BlueprintIssue(String code, String resource, String pointer, String message) {}
